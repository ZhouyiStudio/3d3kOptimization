package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 刷怪笼优化器
 * - 限制刷怪笼每 tick 的生成数量
 * - 当刷怪笼周围实体过多时暂停生成
 * - 控制刷怪笼的激活范围
 * - 检测刷怪笼农场的异常生成
 */
public class SpawnerOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable spawnerTask;

    // 刷怪笼的最后生成时间（用于限制生成速率）
    private final Map<Location, Long> lastSpawnTime = new HashMap<>();
    // 刷怪笼的累计生成计数（用于速率控制）
    private final Map<Location, Integer> spawnCount = new HashMap<>();
    // 被暂停的刷怪笼
    private final Set<Location> suppressedSpawners = new HashSet<>();

    public SpawnerOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isSpawnerOptimizerEnabled()) return;

        spawnerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkSpawners();
            }
        };
        // 每 1 秒（20 tick）检查一次
        spawnerTask.runTaskTimer(plugin, 60L, 20L);
    }

    /**
     * 检查所有世界的刷怪笼
     */
    private void checkSpawners() {
        long now = System.currentTimeMillis();
        int maxSpawnRate = config.getSpawnerMaxSpawnRate();
        int maxNearbyEntities = config.getSpawnerMaxNearbyEntities();
        int checkRadius = config.getSpawnerCheckRadius();
        int delayMs = config.getSpawnerActivationDelay() * 50; // tick 转 ms

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                for (var state : chunk.getTileEntities()) {
                    if (!(state instanceof CreatureSpawner spawner)) continue;

                    Location loc = spawner.getLocation();
                    String spawnerType = spawner.getSpawnedType() != null ?
                            spawner.getSpawnedType().name() : "UNKNOWN";

                    // 检查是否有玩家在附近（刷怪笼激活范围）
                    boolean hasPlayerNearby = false;
                    for (Player player : world.getPlayers()) {
                        if (player.getLocation().distanceSquared(loc) <= checkRadius * checkRadius) {
                            hasPlayerNearby = true;
                            break;
                        }
                    }
                    if (!hasPlayerNearby) continue;

                    // 检查周围实体数
                    if (maxNearbyEntities > 0) {
                        int nearbyCount = countEntitiesAround(loc, 16);
                        if (nearbyCount >= maxNearbyEntities) {
                            // 周围实体过多，暂停刷怪笼
                            if (suppressedSpawners.add(loc)) {
                                spawner.setDelay(200); // 设置延迟，减少生成
                                if (config.isDebug()) {
                                    plugin.getLogger().fine("[Spawner] 暂停刷怪笼 @" +
                                            world.getName() + " " + loc.getBlockX() + "," +
                                            loc.getBlockY() + "," + loc.getBlockZ() +
                                            " (周围实体: " + nearbyCount + ")");
                                }
                            }
                            continue;
                        }
                    }

                    // 移除暂停状态（如果周围实体减少了）
                    suppressedSpawners.remove(loc);

                    // 限制生成速率
                    if (maxSpawnRate > 0) {
                        Long lastSpawn = lastSpawnTime.get(loc);
                        if (lastSpawn != null && (now - lastSpawn) < delayMs) {
                            // 距上次生成太近，延长生成延迟
                            int currentDelay = spawner.getDelay();
                            if (currentDelay < 40) {
                                spawner.setDelay(40);
                            }
                            continue;
                        }

                        // 跟踪单位时间内的生成数
                        int count = spawnCount.getOrDefault(loc, 0) + 1;
                        spawnCount.put(loc, count);
                        lastSpawnTime.put(loc, now);

                        // 如果在1秒内生成过多，延长延迟
                        if (count > maxSpawnRate) {
                            spawner.setDelay(Math.max(spawner.getDelay(), 60));
                            if (config.isDebug()) {
                                plugin.getLogger().fine("[Spawner] 限制刷怪笼速率 @" +
                                        world.getName() + " (已生成: " + count + "/秒)");
                            }
                            spawnCount.clear();
                        }
                    }
                }
            }
        }

        // 定期清理过期记录
        long expireTime = now - 5000;
        lastSpawnTime.entrySet().removeIf(e -> e.getValue() < expireTime);
        spawnCount.clear();
    }

    /**
     * 统计位置周围指定半径内的实体数量
     */
    private int countEntitiesAround(Location location, int radius) {
        int count = 0;
        World world = location.getWorld();
        if (world == null) return 0;

        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            if (!entity.isValid() || entity.isDead()) continue;
            if (entity.getLocation().distanceSquared(location) <= radius * radius) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取当前被暂停的刷怪笼数量
     */
    public int getSuppressedSpawnerCount() {
        return suppressedSpawners.size();
    }

    /**
     * 获取指定世界的刷怪笼统计
     */
    public SpawnerStats getSpawnerStats(World world) {
        int total = 0, active = 0, suppressed = 0;
        for (Chunk chunk : world.getLoadedChunks()) {
            for (var state : chunk.getTileEntities()) {
                if (state instanceof CreatureSpawner spawner) {
                    total++;
                    Location loc = spawner.getLocation();
                    if (suppressedSpawners.contains(loc)) {
                        suppressed++;
                    } else if (spawner.getDelay() < 100) {
                        active++;
                    }
                }
            }
        }
        return new SpawnerStats(total, active, suppressed);
    }

    public void shutdown() {
        lastSpawnTime.clear();
        spawnCount.clear();
        suppressedSpawners.clear();
        if (spawnerTask != null) {
            spawnerTask.cancel();
        }
    }

    /**
     * 刷怪笼统计
     */
    public record SpawnerStats(int total, int active, int suppressed) {
        @Override
        public String toString() {
            return "刷怪笼: " + total + " | 活跃: " + active +
                    " | 暂停: " + suppressed;
        }
    }
}
