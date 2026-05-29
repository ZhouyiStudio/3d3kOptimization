package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 区块优化器
 * - 优化区块加载/卸载
 * - 控制视距
 * - 定期清理无用区块
 */
public class ChunkOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable chunkTask;

    public ChunkOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        // 启动区块管理任务（每 5 秒）
        chunkTask = new BukkitRunnable() {
            @Override
            public void run() {
                optimizeChunks();
            }
        };
        chunkTask.runTaskTimer(plugin, 100L, 100L);
    }

    /**
     * 优化所有世界的区块
     */
    private void optimizeChunks() {
        for (World world : Bukkit.getWorlds()) {
            unloadUnusedChunks(world);
        }
    }

    /**
     * 卸载不再使用的区块
     */
    private void unloadUnusedChunks(World world) {
        Chunk[] loadedChunks = world.getLoadedChunks();
        if (loadedChunks.length == 0) return;

        // 获取所有玩家所在的区块坐标
        Set<String> playerChunks = new HashSet<>();
        for (Player player : world.getPlayers()) {
            int chunkX = player.getLocation().getBlockX() >> 4;
            int chunkZ = player.getLocation().getBlockZ() >> 4;
            int viewDist = config.getViewDistance();

            // 添加玩家周围视距内的所有区块
            for (int dx = -viewDist; dx <= viewDist; dx++) {
                for (int dz = -viewDist; dz <= viewDist; dz++) {
                    playerChunks.add((chunkX + dx) + "," + (chunkZ + dz));
                }
            }
        }

        // 如果没有玩家在线，跳过卸载
        if (playerChunks.isEmpty()) return;

        int unloaded = 0;
        int maxUnloads = config.getMaxUnloadsPerTick();

        for (Chunk chunk : loadedChunks) {
            if (unloaded >= maxUnloads) break;

            String key = chunk.getX() + "," + chunk.getZ();

            // 不卸载玩家附近的区块
            if (playerChunks.contains(key)) continue;

            // 不卸载有实体的区块（保留实体区块防止实体丢失）
            if (chunk.getEntities().length > 5) continue;

            // 卸载区块
            if (world.unloadChunk(chunk.getX(), chunk.getZ(), true)) {
                unloaded++;
                if (config.isDebug()) {
                    plugin.getLogger().fine("[Chunk] 卸载区块: " + world.getName() + " [" + key + "]");
                }
            }
        }

        if (unloaded > 0 && config.isDebug()) {
            plugin.getLogger().info("[Chunk] " + world.getName() + " 卸载了 " + unloaded + " 个区块");
        }
    }

    /**
     * 获取指定世界的区块统计信息
     */
    public ChunkStats getChunkStats(World world) {
        Chunk[] loaded = world.getLoadedChunks();
        int totalEntities = 0;
        for (Chunk chunk : loaded) {
            totalEntities += chunk.getEntities().length;
        }
        return new ChunkStats(loaded.length, totalEntities);
    }

    /**
     * 清理 - 插件卸载时调用
     */
    public void cleanup() {
        if (chunkTask != null) {
            chunkTask.cancel();
        }
    }

    /**
     * 区块统计信息
     */
    public record ChunkStats(int loadedChunks, int totalEntities) {
        @Override
        public String toString() {
            return "已加载区块: " + loadedChunks + " | 区块实体: " + totalEntities;
        }
    }
}
