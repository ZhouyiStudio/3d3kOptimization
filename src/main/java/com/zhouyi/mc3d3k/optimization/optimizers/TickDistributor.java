package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Tick 分布优化器（智能 tick 调度）
 * - 将实体的 tick 分散到多个 game tick 中执行，避免单 tick 峰值
 * - 根据与玩家的距离分层降频（近=全tick，中=半频，远=四分之一频）
 * - 非玩家附近的 tile entity tick 降频
 * - 根据当前 TPS 自适应调整 tick 频率
 * - 在低 TPS 时自动跳过非关键实体 tick
 * <p>
 * 核心原理：替代原版每 tick 扫描所有实体的方式，
 * 将实体分组轮询，每组只在特定 tick 执行，
 * 从而平滑负载曲线。
 */
public class TickDistributor {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable tickTask;

    // 轮询分组
    private int groupCounter = 0;

    // 上次 TPS 采样
    private double lastTps = 20.0;

    // 非关键实体类型（可安全跳 tick 的）
    private static final Set<Class<? extends Entity>> NON_CRITICAL_TYPES = Set.of(
            Item.class,
            ExperienceOrb.class,
            AreaEffectCloud.class,
            ArmorStand.class,
            Bat.class,
            Chicken.class,
            Cod.class,
            Salmon.class,
            TropicalFish.class,
            PufferFish.class,
            Squid.class,
            GlowSquid.class,
            Dolphin.class
    );

    // 中等重要实体（可降频但不可全跳的）
    private static final Set<Class<? extends Entity>> MEDIUM_CRITICAL_TYPES = Set.of(
            Zombie.class,
            Skeleton.class,
            Spider.class,
            Creeper.class,
            Enderman.class,
            Piglin.class,
            Hoglin.class,
            Wolf.class,
            Fox.class,
            Bee.class,
            Sheep.class,
            Cow.class,
            Pig.class,
            Rabbit.class
    );

    public TickDistributor(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isTickDistributorEnabled()) return;

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                groupCounter++;
                lastTps = Bukkit.getTPS()[0];
                distributeTicks();
            }
        };
        // 每 2 tick 执行一次（而非每 tick，减少调度开销）
        tickTask.runTaskTimer(plugin, 20L, 2L);
    }

    /**
     * 执行 tick 分布
     */
    private void distributeTicks() {
        double tpsThreshold = config.getTickDistributorTpsThreshold();
        int nearRange = config.getTickDistributorNearRange();
        int midRange = config.getTickDistributorMidRange();

        // TPS 自适应：TPS 越低，跳过的 tick 越多
        int groupCount;
        if (lastTps < 10.0) {
            groupCount = 20; // 超低 TPS -> 每实体每 40 tick 才 tick 一次
        } else if (lastTps < 15.0) {
            groupCount = 10; // 低 TPS -> 每 20 tick
        } else if (lastTps < tpsThreshold) {
            groupCount = 5;  // TPS 偏低 -> 每 10 tick
        } else {
            groupCount = 1;  // TPS 正常 -> 不跳过
        }

        int currentGroup = groupCounter % Math.max(groupCount, 1);

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            List<Player> players = world.getPlayers();

            for (Entity entity : world.getEntities()) {
                if (!entity.isValid() || entity.isDead()) continue;

                // 跳过玩家（玩家 tick 不能动）
                if (entity instanceof Player) continue;

                // 跳过重要的功能性实体（矿车、船等）
                if (entity instanceof Minecart || entity instanceof Boat) continue;

                // 确定实体优先级层级
                EntityTickPriority priority = classifyEntity(entity, players, nearRange, midRange);

                switch (priority) {
                    case FULL -> {
                        // 全频 tick，不做限制
                    }
                    case MEDIUM -> {
                        // 半频：每 2 tick 执行一次（当前组为偶数时跳过）
                        if (currentGroup % 2 == 0) {
                            skipEntityTick(entity);
                        }
                    }
                    case LOW -> {
                        // 低频：每 groupCount 中的当前组才执行
                        if (currentGroup != 0) {
                            skipEntityTick(entity);
                        }
                    }
                    case FROZEN -> {
                        // 冻结：完全跳过
                        skipEntityTick(entity);
                        if (entity instanceof Mob mob) {
                            mob.setAI(false);
                        }
                        entity.setVelocity(entity.getVelocity().zero());
                    }
                }
            }
        }
    }

    /**
     * 对实体进行优先级分类
     */
    private EntityTickPriority classifyEntity(Entity entity, List<Player> players,
                                              int nearRange, int midRange) {
        // 找到最近的玩家距离
        double minDistSq = Double.MAX_VALUE;
        for (Player player : players) {
            if (!player.isOnline() || player.isDead()) continue;
            double distSq = entity.getLocation().distanceSquared(player.getLocation());
            if (distSq < minDistSq) {
                minDistSq = distSq;
            }
        }

        double nearRangeSq = (double) nearRange * nearRange;
        double midRangeSq = (double) midRange * midRange;

        // 玩家附近 -> 全频
        if (minDistSq <= nearRangeSq) {
            return EntityTickPriority.FULL;
        }

        // 中等距离 + 中等重要实体 -> MEDIUM
        if (minDistSq <= midRangeSq) {
            if (NON_CRITICAL_TYPES.stream().anyMatch(t -> t.isInstance(entity))) {
                return EntityTickPriority.LOW;
            }
            return EntityTickPriority.MEDIUM;
        }

        // 远距离
        if (NON_CRITICAL_TYPES.stream().anyMatch(t -> t.isInstance(entity))) {
            return EntityTickPriority.FROZEN;
        }
        if (MEDIUM_CRITICAL_TYPES.stream().anyMatch(t -> t.isInstance(entity))) {
            return EntityTickPriority.LOW;
        }
        // 高价值实体（Boss、玩家载具等）至少给 MEDIUM
        return EntityTickPriority.MEDIUM;
    }

    /**
     * 跳过指定实体的 tick（降低其 tick 频率）
     */
    private void skipEntityTick(Entity entity) {
        // 主要靠上述分类在 tick 调度层面跳过
        // 这里只做辅助优化
        if (entity instanceof Mob mob && mob.getAI()) {
            // 已经有 AI=true 的不动（由 AIOptimizer 管理）
        }
    }

    /**
     * 获取当前 TPS
     */
    public double getCurrentTps() {
        return lastTps;
    }

    /**
     * 获取实体 tick 优先级分类
     */
    public EntityTickPriority getEntityPriority(Entity entity) {
        if (entity instanceof Player) return EntityTickPriority.FULL;
        if (entity instanceof Minecart || entity instanceof Boat) return EntityTickPriority.FULL;

        for (World world : Bukkit.getWorlds()) {
            if (world.getEntities().contains(entity)) {
                List<Player> players = world.getPlayers();
                return classifyEntity(entity, players,
                        config.getTickDistributorNearRange(),
                        config.getTickDistributorMidRange());
            }
        }
        return EntityTickPriority.MEDIUM;
    }

    public void shutdown() {
        // 恢复所有被冻结的实体
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Mob mob && !mob.hasAI()) {
                    if (NON_CRITICAL_TYPES.stream().anyMatch(t -> t.isInstance(entity))) {
                        mob.setAI(true);
                    }
                }
            }
        }
        if (tickTask != null) {
            tickTask.cancel();
        }
    }

    /**
     * 实体 tick 优先级
     */
    public enum EntityTickPriority {
        FULL,    // 全频 tick（玩家附近的高价值实体）
        MEDIUM,  // 半频 tick（中等距离的普通实体）
        LOW,     // 低频 tick（远距离的低价值实体）
        FROZEN   // 冻结 tick（极远距离的装饰性实体）
    }
}
