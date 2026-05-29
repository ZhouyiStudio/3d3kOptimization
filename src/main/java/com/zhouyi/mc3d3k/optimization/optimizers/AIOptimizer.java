package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * AI 优化器
 * - 远离玩家的怪物冻结 AI（不移动、不攻击）
 * - 激活范围之外的非敌对实体设置为不会移动
 * - 定期唤醒检查
 */
public class AIOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable aiTask;

    // 被冻结的实体 - 用于恢复时解除冻结
    private final Set<UUID> frozenEntities = new HashSet<>();

    public AIOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isAiOptimizerEnabled()) return;

        aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                freezeDistantEntities();
            }
        };
        // 每 1 秒检查一次
        aiTask.runTaskTimer(plugin, 60L, 20L);
    }

    /**
     * 冻结远离玩家的实体 AI
     */
    private void freezeDistantEntities() {
        int range = config.getAiFreezeRange();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            // 获取所有玩家位置（用于快速距离判断）
            List<Player> players = world.getPlayers();

            // 当前活跃的冻结 UUID 集合（本轮检查）
            Set<UUID> activeFreezes = new HashSet<>();

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player) continue;
                if (entity instanceof Item) continue;
                if (entity instanceof ExperienceOrb) continue;
                if (!entity.isValid() || entity.isDead()) continue;

                // 检查实体是否在任一玩家的激活范围内
                boolean nearPlayer = false;
                for (Player player : players) {
                    if (!player.isOnline() || player.isDead()) continue;
                    if (entity.getLocation().distanceSquared(player.getLocation())
                            <= (double) range * range) {
                        nearPlayer = true;
                        break;
                    }
                }

                UUID uuid = entity.getUniqueId();

                if (!nearPlayer) {
                    // 远离玩家 - 冻结 AI
                    if (frozenEntities.add(uuid)) {
                        // 仅在状态变化时操作，减少性能开销
                        entity.setAI(false);
                        // 标记静止
                        entity.setVelocity(entity.getVelocity().zero());
                    }
                    activeFreezes.add(uuid);
                } else {
                    // 在玩家附近 - 如果有 AI 被冻结，恢复
                    if (frozenEntities.contains(uuid)) {
                        entity.setAI(true);
                        frozenEntities.remove(uuid);
                    }
                }
            }

            // 清理已经不存在的实体 UUID
            frozenEntities.retainAll(activeFreezes);
        }
    }

    /**
     * 获取当前被冻结的实体数量
     */
    public int getFrozenEntityCount() {
        return frozenEntities.size();
    }

    public void shutdown() {
        // 恢复所有被冻结的实体
        for (UUID uuid : frozenEntities) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null && !entity.isDead()) {
                entity.setAI(true);
            }
        }
        frozenEntities.clear();

        if (aiTask != null) {
            aiTask.cancel();
        }
    }
}
