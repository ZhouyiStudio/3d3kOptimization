package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 生物限制器
 * - 控制每个区块的最大生物数
 * - 控制每种生物的每区块上限
 * - 全局实体上限控制
 * - 刷怪抑制
 */
public class MobLimiter implements Listener {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable cleanupTask;

    // 用于计数缓存的映射
    private final Map<String, Integer> typeCountCache = new HashMap<>();

    public MobLimiter(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        // 注册事件监听
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 定期清理任务 - 移除超过限制的生物
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                enforceLimits();
            }
        };
        cleanupTask.runTaskTimer(plugin, 200L, 200L);
    }

    /**
     * 生物生成事件 - 在生物生成时检查限制
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!config.isMobLimiterEnabled()) return;

        Entity entity = event.getEntity();
        Location location = event.getLocation();
        World world = location.getWorld();
        if (world == null) return;

        // 限制自然生成的生物，保留人造生成（如刷怪笼、命令）
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CHUNK_GEN) {

            // 检查全局上限
            if (isGlobalLimitReached(entity, world)) {
                event.setCancelled(true);
                return;
            }

            // 检查每区块上限
            Chunk chunk = location.getChunk();
            if (isChunkLimitReached(entity, chunk)) {
                event.setCancelled(true);
                return;
            }

            // 检查每种生物限制
            if (isPerTypeLimitReached(entity, chunk)) {
                event.setCancelled(true);
                return;
            }

            // 刷怪抑制
            if (isSpawnSuppressed(entity, location)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 检查是否达到全局上限
     */
    private boolean isGlobalLimitReached(Entity entity, World world) {
        String type = getEntityCategory(entity);
        int limit = switch (type) {
            case "MONSTER" -> config.getGlobalMonstersLimit();
            case "ANIMAL" -> config.getGlobalAnimalsLimit();
            case "WATER" -> config.getGlobalWaterLimit();
            case "FLYING" -> config.getGlobalFlyingLimit();
            default -> -1;
        };

        if (limit <= 0) return false;

        int current = countEntitiesInWorld(entity.getClass(), world);
        return current >= limit;
    }

    /**
     * 检查是否达到每区块上限
     */
    private boolean isChunkLimitReached(Entity entity, Chunk chunk) {
        int maxPerChunk = config.getMaxMobsPerChunk();
        if (maxPerChunk <= 0) return false;

        int current = chunk.getEntities().length;
        return current >= maxPerChunk;
    }

    /**
     * 检查每种生物是否达到限制
     */
    private boolean isPerTypeLimitReached(Entity entity, Chunk chunk) {
        String entityType = entity.getType().name();
        int limit = config.getPerTypeLimit(entityType);
        if (limit <= 0) return false;

        int current = 0;
        for (Entity e : chunk.getEntities()) {
            if (e.getType().name().equals(entityType) && !e.isDead()) {
                current++;
            }
        }
        return current >= limit;
    }

    /**
     * 刷怪抑制检查 - 玩家附近实体过多时停止生成
     */
    private boolean isSpawnSuppressed(Entity entity, Location location) {
        int threshold = config.getSpawnSuppressThreshold();
        if (threshold <= 0) return false;

        // 查找最近的玩家
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player player : location.getWorld().getPlayers()) {
            double dist = player.getLocation().distance(location);
            if (dist < minDist) {
                minDist = dist;
                nearest = player;
            }
        }

        if (nearest == null) return false;

        // 检查玩家附近实体数（半径 24 格）
        int nearbyCount = 0;
        for (Entity e : nearest.getNearbyEntities(24, 24, 24)) {
            if (isHostile(e) && !e.isDead()) {
                nearbyCount++;
            }
        }

        return nearbyCount >= threshold;
    }

    /**
     * 执行限制 - 定期清除超过限制的生物
     */
    private void enforceLimits() {
        if (!config.isMobLimiterEnabled()) return;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.isDead() || !entity.isValid()) continue;

                // 不移除玩家
                if (entity instanceof Player) continue;

                Chunk chunk = entity.getLocation().getChunk();

                // 检查全局上限
                if (isGlobalLimitReached(entity, world)) {
                    // 移除最远的实体
                    removeRandomEntity(entity, world);
                    continue;
                }

                // 检查每区块上限
                if (isChunkLimitReached(entity, chunk)) {
                    entity.remove();
                    if (config.isDebug()) {
                        plugin.getLogger().fine("[MobLimiter] 移除超出区块限制: " +
                                entity.getType().name() + " @" + chunk.getX() + "," + chunk.getZ());
                    }
                }
            }
        }
    }

    /**
     * 移除随机实体 - 当全局上限达到时，移除一个距离玩家最远的同类实体
     */
    private void removeRandomEntity(Entity entity, World world) {
        // 简单处理：移除该实体
        entity.remove();
        if (config.isDebug()) {
            plugin.getLogger().fine("[MobLimiter] 移除超出全局限制: " + entity.getType().name());
        }
    }

    /**
     * 计算世界中某种实体的数量
     */
    private int countEntitiesInWorld(Class<? extends Entity> entityClass, World world) {
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entityClass.isInstance(entity) && !entity.isDead()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取实体分类
     */
    private String getEntityCategory(Entity entity) {
        if (entity instanceof Monster || entity instanceof Raider) return "MONSTER";
        if (entity instanceof Animals || entity instanceof AbstractVillager) return "ANIMAL";
        if (entity instanceof WaterMob) return "WATER";
        if (entity instanceof Flying) return "FLYING";
        // 通过类型名称判断额外的飞行生物
        String name = entity.getType().name();
        if (name.equals("PHANTOM") || name.equals("BAT") || name.equals("BEE") || name.equals("PARROT")) {
            return "FLYING";
        }
        return "OTHER";
    }

    /**
     * 判断是否敌对生物
     */
    private boolean isHostile(Entity entity) {
        return entity instanceof Monster ||
                entity instanceof Raider ||
                entity instanceof Slime ||
                entity instanceof Phantom;
    }

    /**
     * 获取世界的生物统计信息
     */
    public MobStats getMobStats(World world) {
        int monsters = 0, animals = 0, water = 0, flying = 0, other = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            if (entity.isDead()) continue;
            switch (getEntityCategory(entity)) {
                case "MONSTER" -> monsters++;
                case "ANIMAL" -> animals++;
                case "WATER" -> water++;
                case "FLYING" -> flying++;
                default -> other++;
            }
        }
        return new MobStats(monsters, animals, water, flying, other);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }

    /**
     * 生物统计信息
     */
    public record MobStats(int monsters, int animals, int water, int flying, int other) {
        @Override
        public String toString() {
            return "怪物: " + monsters + " | 动物: " + animals +
                    " | 水生: " + water + " | 飞行: " + flying +
                    " | 其他: " + other + " | 总计: " + (monsters + animals + water + flying + other);
        }
    }
}
