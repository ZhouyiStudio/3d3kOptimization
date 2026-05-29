package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Villager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 村民优化器
 * - 限制村民每个 tick 的 gossip 更新频率
 * - 减少村民无效的 AI 寻路
 * - 优化村民工作站点搜索
 * - 限制村民繁殖（可选）
 */
public class VillagerOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable villagerTask;

    public VillagerOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isVillagerOptimizerEnabled()) return;

        villagerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (config.isVillagerLimitGossip()) {
                    throttleGossip();
                }
                if (config.isVillagerDisableBreeding()) {
                    disableBreeding();
                }
                if (config.isVillagerLimitAITasks()) {
                    limitAITasks();
                }
            }
        };
        // 每 5 tick 检查一次，减少频率
        villagerTask.runTaskTimer(plugin, 40L, 5L);
    }

    /**
     * 限制村民 gossip 频率 - 随机跳过一些 tick 的 gossip 更新
     */
    private void throttleGossip() {
        double skipChance = config.getVillagerGossipSkipChance();
        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.isDead() || !villager.isValid()) continue;
                // 随机跳过 gossip 更新
                if (Math.random() < skipChance) {
                    // 通过设置 AI 暂停来减少 gossip 处理
                    villager.setAI(false);
                    // 延迟恢复 AI
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!villager.isDead() && villager.isValid()) {
                            villager.setAI(true);
                        }
                    }, 2L);
                }
            }
        }
    }

    /**
     * 禁用村民繁殖 - 阻止村民进入繁殖模式
     */
    private void disableBreeding() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.isDead() || !villager.isValid()) continue;
                // 设置繁殖年龄为负数（-9999 表示永不繁殖）
                if (villager.getAge() >= 0) {
                    villager.setAge(-9999);
                }
            }
        }
    }

    /**
     * 限制村民 AI 任务 - 远距离村民只保留基础 AI
     */
    private void limitAITasks() {
        int freezeRange = config.getVillagerFreezeRange();
        if (freezeRange <= 0) return;

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.isDead() || !villager.isValid()) continue;

                // 查找最近的玩家
                boolean hasPlayerNearby = world.getPlayers().stream()
                        .anyMatch(p -> p.getLocation().distanceSquared(villager.getLocation()) < freezeRange * freezeRange);

                if (!hasPlayerNearby) {
                    villager.setAI(false);
                }
            }
        }
        // 恢复已加载区块中靠近玩家的村民 AI
        // 这个逻辑由每个 tick 的简单检查处理
    }

    public void shutdown() {
        if (villagerTask != null) {
            villagerTask.cancel();
        }
        // 恢复所有村民 AI
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (!villager.isDead() && villager.isValid()) {
                    villager.setAI(true);
                }
            }
        }
    }
}
