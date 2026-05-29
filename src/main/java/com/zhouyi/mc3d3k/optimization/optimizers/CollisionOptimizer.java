package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 碰撞优化器
 * - 减少大量实体间的碰撞检测
 * - 限制单个区块内的最大实体碰撞数
 * - 对低 TPS 时自动减少碰撞检测频率
 */
public class CollisionOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable collisionTask;
    private int tickCounter = 0;

    public CollisionOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isCollisionOptimizerEnabled()) return;

        collisionTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
                // 根据配置的间隔执行
                int interval = config.getCollisionCheckInterval();
                if (tickCounter % interval != 0) return;

                if (config.isCollisionLimitPerChunk()) {
                    limitChunkCollisions();
                }
            }
        };
        collisionTask.runTaskTimer(plugin, 60L, 1L);
    }

    /**
     * 限制每个区块碰撞实体数量
     * 对高密度区域分散实体位置，减少碰撞计算
     */
    private void limitChunkCollisions() {
        int maxPerChunk = config.getCollisionMaxEntitiesPerChunk();
        if (maxPerChunk <= 0) return;

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            // 使用区块坐标分组
            Map<String, List<Entity>> chunkMap = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player || entity.isDead()) continue;
                if (!(entity instanceof LivingEntity)) continue;
                if (!entity.isValid()) continue;

                String chunkKey = entity.getLocation().getChunk().getX() + "," + entity.getLocation().getChunk().getZ();
                chunkMap.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(entity);
            }

            // 对超过限制的区块，随机碰撞（推走）部分实体
            for (Map.Entry<String, List<Entity>> entry : chunkMap.entrySet()) {
                List<Entity> entities = entry.getValue();
                if (entities.size() <= maxPerChunk) continue;

                // 超过限制的实体数量
                int excess = entities.size() - maxPerChunk;
                // 随机打乱并选取超出的实体
                Collections.shuffle(entities);
                for (int i = 0; i < excess; i++) {
                    Entity e = entities.get(i);
                    // 设置碰撞豁免标记
                    e.setGravity(true);
                    // 小幅度随机位移，降低碰撞检测精度
                    if (e instanceof LivingEntity living) {
                        living.setCollidable(false);
                        // 1 tick 后恢复碰撞，分散碰撞检测
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (!living.isDead() && living.isValid()) {
                                living.setCollidable(true);
                            }
                        }, 3L);
                    }
                }
            }
        }
    }

    /**
     * 检测当前 TPS 并自动降低碰撞负载
     */
    public void checkPerformance() {
        if (!config.isCollisionAutoScale()) return;
        double tps = Bukkit.getTPS()[0];
        if (tps < config.getCollisionTpsThreshold()) {
            // TPS 低时，增大检查间隔，减少碰撞计算
            if (collisionTask != null) {
                collisionTask.cancel();
                collisionTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        tickCounter++;
                        int interval = config.getCollisionCheckInterval() * 3; // 间隔×3
                        if (tickCounter % interval != 0) return;
                        if (config.isCollisionLimitPerChunk()) {
                            limitChunkCollisions();
                        }
                    }
                };
                collisionTask.runTaskTimer(plugin, 60L, 1L);
            }
        }
    }

    public void shutdown() {
        if (collisionTask != null) {
            collisionTask.cancel();
        }
    }
}
