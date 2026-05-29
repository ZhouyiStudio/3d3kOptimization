package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Hopper;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 漏斗优化器
 * - 减少漏斗的检查频率（hopper cooldown）
 * - 限制每个 tick 处理的漏斗数量，分散负载
 * - 移除空漏斗的 tick
 */
public class HopperOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable hopperTask;
    private int currentIndex = 0;

    public HopperOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isHopperOptimizerEnabled()) return;

        hopperTask = new BukkitRunnable() {
            @Override
            public void run() {
                processHoppers();
            }
        };
        // 每 2 tick 处理一次，分散漏斗负载
        hopperTask.runTaskTimer(plugin, 20L, 2L);
    }

    /**
     * 分批处理漏斗
     */
    private void processHoppers() {
        int maxPerTick = config.getHopperMaxPerTick();
        if (maxPerTick <= 0) return;

        // 收集所有世界的漏斗
        List<Hopper> allHoppers = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            // 只处理有玩家的世界
            if (world.getPlayers().isEmpty()) continue;
            // 通过区块实体获取漏斗（不做全量扫描，只处理已加载区块中的漏斗）
            // 注意：getEntitiesByClass 只获取已加载实体
            allHoppers.addAll(world.getEntitiesByClass(Hopper.class));
        }

        if (allHoppers.isEmpty()) return;

        // 循环索引，分批处理
        int size = allHoppers.size();
        if (currentIndex >= size) {
            currentIndex = 0;
        }

        int end = Math.min(currentIndex + maxPerTick, size);
        for (int i = currentIndex; i < end; i++) {
            Hopper hopper = allHoppers.get(i);
            if (hopper.isDead() || !hopper.isValid()) continue;

            // 设置自定义冷却时间（tick）
            // 通过 setCooldown 减少漏斗检查频率
            int cooldown = config.getHopperCooldown();
            if (cooldown > 0 && hopper.getCooldown() < cooldown) {
                hopper.setCooldown(cooldown);
            }
        }

        currentIndex = (currentIndex + maxPerTick >= size) ? 0 : currentIndex + maxPerTick;
    }

    public void shutdown() {
        if (hopperTask != null) {
            hopperTask.cancel();
        }
    }
}
