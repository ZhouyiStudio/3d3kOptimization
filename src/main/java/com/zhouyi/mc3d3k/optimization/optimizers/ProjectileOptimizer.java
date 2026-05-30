package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 抛射物优化器
 * - 清理卡在方块中过久的箭矢、三叉戟、雪球等抛射物
 * - 限制每个世界的抛射物总数
 * - 清理掉落在虚空中的抛射物
 */
public class ProjectileOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable cleanupTask;

    // 需要清理的抛射物类型
    private static final Set<Class<? extends Projectile>> PROJECTILE_TYPES = Set.of(
            Arrow.class,
            SpectralArrow.class,
            Trident.class,
            Snowball.class,
            Egg.class,
            EnderPearl.class,
            ThrownExpBottle.class,
            LlamaSpit.class,
            ShulkerBullet.class,
            DragonFireball.class,
            WitherSkull.class,
            SmallFireball.class,
            LargeFireball.class,
            Fireball.class
    );

    // 记录每个抛射物的存活时长（tick）
    private final Map<UUID, Integer> projectileAge = new HashMap<>();

    public ProjectileOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isProjectileOptimizerEnabled()) return;

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                cleanProjectiles();
            }
        };
        // 每 4 秒（80 tick）执行一次清理
        cleanupTask.runTaskTimer(plugin, 100L, 80L);
    }

    /**
     * 清理过期的抛射物
     */
    private void cleanProjectiles() {
        int maxAge = config.getProjectileMaxAge() * 20; // 秒转 tick
        int maxPerWorld = config.getProjectileMaxPerWorld();
        int totalRemoved = 0;

        for (World world : Bukkit.getWorlds()) {
            List<Entity> projectiles = world.getEntities().stream()
                    .filter(e -> PROJECTILE_TYPES.stream().anyMatch(t -> t.isInstance(e)))
                    .toList();
            int count = projectiles.size();

            // 限制每世界抛射物总数
            if (maxPerWorld > 0 && count > maxPerWorld) {
                int toRemove = count - maxPerWorld;
                for (Entity proj : projectiles) {
                    if (toRemove <= 0) break;
                    if (!shouldPreserveProjectile(proj)) {
                        proj.remove();
                        toRemove--;
                        totalRemoved++;
                    }
                }
            }

            // 按存活时间清理
            for (Entity proj : projectiles) {
                if (!proj.isValid() || proj.isDead()) continue;
                if (shouldPreserveProjectile(proj)) continue;

                UUID uuid = proj.getUniqueId();
                int age = projectileAge.getOrDefault(uuid, 0) + 1;
                projectileAge.put(uuid, age);

                if (maxAge > 0 && age > maxAge) {
                    proj.remove();
                    projectileAge.remove(uuid);
                    totalRemoved++;
                    if (config.isDebug()) {
                        plugin.getLogger().fine("[Projectile] 移除过期抛射物: " +
                                proj.getType().name() + " @" + world.getName());
                    }
                    continue;
                }

                // 清理卡在方块中的未移动抛射物（长时间不移动的箭头等）
                if (proj instanceof Arrow arrow && arrow.isInBlock()) {
                    if (age > maxAge / 2) {
                        arrow.remove();
                        projectileAge.remove(uuid);
                        totalRemoved++;
                        if (config.isDebug()) {
                            plugin.getLogger().fine("[Projectile] 移除卡方块箭矢: @" + world.getName());
                        }
                    }
                }
            }
        }

        if (totalRemoved > 0 && config.isDebug()) {
            plugin.getLogger().info("[Projectile] 本次清理抛射物: " + totalRemoved + " 个");
        }
    }

    /**
     * 判断是否需要保留该抛射物（玩家射出的、仍在飞行中的）
     */
    private boolean shouldPreserveProjectile(Entity entity) {
        if (entity instanceof Projectile proj) {
            // 保留仍在飞行中的、有射击者的抛射物
            if (proj.getShooter() instanceof Player && !proj.isOnGround()) {
                // 仍在空中飞行的玩家抛射物保留
                return proj.getVelocity().lengthSquared() > 0.01;
            }
            // 三叉戟有拾取机制的特殊处理
            if (proj instanceof Trident trident) {
                return !trident.isInBlock() && trident.getPickupStatus() != null;
            }
        }
        return false;
    }

    /**
     * 获取世界中抛射物的统计数据
     */
    public ProjectileStats getProjectileStats(World world) {
        int arrows = 0, tridents = 0, otherProjectiles = 0;
        for (Entity e : world.getEntities()) {
            if (e instanceof Arrow && !(e instanceof SpectralArrow)) arrows++;
            else if (e instanceof Trident) tridents++;
            else if (PROJECTILE_TYPES.stream().anyMatch(t -> t.isInstance(e))) otherProjectiles++;
        }
        return new ProjectileStats(arrows, tridents, otherProjectiles);
    }

    public void shutdown() {
        projectileAge.clear();
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }

    /**
     * 抛射物统计信息
     */
    public record ProjectileStats(int arrows, int tridents, int other) {
        @Override
        public String toString() {
            return "箭矢: " + arrows + " | 三叉戟: " + tridents +
                    " | 其他: " + other + " | 总计: " + (arrows + tridents + other);
        }
    }
}
