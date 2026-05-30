package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 水生生物优化器
 * - 优化鱼、鱿鱼、海豚等低价值水生生物的 AI tick
 * - 远离玩家时冻结 AI（减少不必要的寻路和游泳动画）
 * - 限制每个区块的水生生物数量
 * - 清理卡在方块中的水生生物
 */
public class WaterMobOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable waterTask;

    // 当前被冻结的生物 UUID
    private final Set<UUID> frozenWaterMobs = new HashSet<>();

    // 需要优化的水生生物类型
    private static final Set<Class<? extends Entity>> WATER_MOB_TYPES = Set.of(
            Cod.class,
            Salmon.class,
            TropicalFish.class,
            PufferFish.class,
            Squid.class,
            GlowSquid.class,
            Dolphin.class,
            Turtle.class,
            Axolotl.class
    );

    public WaterMobOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isWaterMobOptimizerEnabled()) return;

        waterTask = new BukkitRunnable() {
            @Override
            public void run() {
                optimizeWaterMobs();
            }
        };
        // 每 2 秒（40 tick）执行一次
        waterTask.runTaskTimer(plugin, 60L, 40L);
    }

    /**
     * 执行水生生物优化
     */
    private void optimizeWaterMobs() {
        int freezeRange = config.getWaterMobFreezeRange();
        int maxPerChunk = config.getWaterMobMaxPerChunk();
        Set<UUID> activeFreezes = new HashSet<>();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            List<Player> players = world.getPlayers();

            for (Entity entity : world.getEntities()) {
                if (!isWaterMob(entity)) continue;
                if (!entity.isValid() || entity.isDead()) continue;

                UUID uuid = entity.getUniqueId();

                // 判断是否有玩家在附近
                boolean nearPlayer = false;
                if (freezeRange > 0) {
                    for (Player player : players) {
                        if (!player.isOnline() || player.isDead()) continue;
                        if (entity.getLocation().distanceSquared(player.getLocation())
                                <= (double) freezeRange * freezeRange) {
                            nearPlayer = true;
                            break;
                        }
                    }
                }

                // 冻结距离过远的水生生物 AI
                if (!nearPlayer && freezeRange > 0) {
                    if (frozenWaterMobs.add(uuid)) {
                        if (entity instanceof Mob mob) {
                            mob.setAI(false);
                        }
                        entity.setVelocity(entity.getVelocity().zero());
                    }
                    activeFreezes.add(uuid);
                } else {
                    // 玩家在附近，恢复 AI
                    if (frozenWaterMobs.contains(uuid)) {
                        if (entity instanceof Mob mob) {
                            mob.setAI(true);
                        }
                        frozenWaterMobs.remove(uuid);
                    }
                }
            }
        }

        // 清理无效的记录
        frozenWaterMobs.retainAll(activeFreezes);

        // 按区块限制数量
        if (maxPerChunk > 0) {
            for (World world : Bukkit.getWorlds()) {
                if (world.getPlayers().isEmpty()) continue;
                Map<String, List<Entity>> chunkMap = new HashMap<>();

                for (Entity entity : world.getEntities()) {
                    if (!isWaterMob(entity)) continue;
                    if (!entity.isValid() || entity.isDead()) continue;
                    String key = entity.getLocation().getChunk().getX() + "," +
                            entity.getLocation().getChunk().getZ();
                    chunkMap.computeIfAbsent(key, k -> new ArrayList<>()).add(entity);
                }

                for (List<Entity> mobs : chunkMap.values()) {
                    if (mobs.size() <= maxPerChunk) continue;
                    // 移除多余的水生生物（优先移除小鱼）
                    int toRemove = mobs.size() - maxPerChunk;
                    for (Entity mob : mobs) {
                        if (toRemove <= 0) break;
                        if (mob instanceof Fish) {
                            mob.remove();
                            toRemove--;
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断是否是需要优化的水生生物
     */
    private boolean isWaterMob(Entity entity) {
        return WATER_MOB_TYPES.stream().anyMatch(t -> t.isInstance(entity));
    }

    /**
     * 获取当前冻结的水生生物数量
     */
    public int getFrozenCount() {
        return frozenWaterMobs.size();
    }

    /**
     * 获取世界中水生生物的统计信息
     */
    public WaterMobStats getWaterMobStats(World world) {
        int fish = 0, squid = 0, dolphin = 0, other = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Fish) fish++;
            else if (entity instanceof Squid || entity instanceof GlowSquid) squid++;
            else if (entity instanceof Dolphin) dolphin++;
            else if (WATER_MOB_TYPES.stream().anyMatch(t -> t.isInstance(entity))) other++;
        }
        return new WaterMobStats(fish, squid, dolphin, other);
    }

    public void shutdown() {
        // 恢复所有冻结的生物
        for (UUID uuid : frozenWaterMobs) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof Mob mob && !entity.isDead()) {
                mob.setAI(true);
            }
        }
        frozenWaterMobs.clear();
        if (waterTask != null) {
            waterTask.cancel();
        }
    }

    /**
     * 水生生物统计
     */
    public record WaterMobStats(int fish, int squid, int dolphin, int other) {
        @Override
        public String toString() {
            return "鱼: " + fish + " | 鱿鱼: " + squid +
                    " | 海豚: " + dolphin + " | 其他: " + other +
                    " | 总计: " + (fish + squid + dolphin + other);
        }
    }
}
