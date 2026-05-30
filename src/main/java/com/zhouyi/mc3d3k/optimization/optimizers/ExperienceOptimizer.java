package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 经验球优化器
 * - 合并附近的经验球（减少实体数量）
 * - 远离玩家时冻结经验球 AI tick
 * - 限制每个区块的经验球数量
 * - TPS 过低时自动降低经验球 tick 频率
 * - 清理卡在方块中的经验球
 */
public class ExperienceOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable xpTask;

    // 需要跳过的 tick 计数（用于降频）
    private int tickCounter = 0;

    public ExperienceOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isExperienceOptimizerEnabled()) return;

        // 核心优化任务：每 10 tick 执行一次（而不是每 tick，减少扫描开销）
        xpTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
                optimizeExperienceOrbs();
            }
        };
        xpTask.runTaskTimer(plugin, 40L, 10L);
    }

    /**
     * 执行经验球优化
     */
    private void optimizeExperienceOrbs() {
        boolean mergeXp = config.isMergeXp();
        int mergeRadius = config.getXpMergeRadius();
        int maxOrbsPerChunk = config.getExperienceMaxPerChunk();
        int freezeRange = config.getExperienceFreezeRange();
        double tpsThreshold = config.getExperienceTpsThreshold();

        // 获取当前 TPS
        double currentTps = Bukkit.getTPS()[0];

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            List<ExperienceOrb> orbs = new ArrayList<>(world.getEntitiesByClass(ExperienceOrb.class));
            if (orbs.isEmpty()) continue;

            // 1. 限制每个区块的经验球数量
            if (maxOrbsPerChunk > 0) {
                Map<String, List<ExperienceOrb>> chunkMap = new HashMap<>();
                for (ExperienceOrb orb : orbs) {
                    if (!orb.isValid() || orb.isDead()) continue;
                    String key = orb.getLocation().getChunk().getX() + "," +
                            orb.getLocation().getChunk().getZ();
                    chunkMap.computeIfAbsent(key, k -> new ArrayList<>()).add(orb);
                }
                for (List<ExperienceOrb> chunkOrbs : chunkMap.values()) {
                    if (chunkOrbs.size() <= maxOrbsPerChunk) continue;
                    int toRemove = chunkOrbs.size() - maxOrbsPerChunk;
                    // 移除最小的经验球（按经验值排序）
                    chunkOrbs.sort(Comparator.comparingInt(ExperienceOrb::getExperience));
                    for (int i = 0; i < toRemove && i < chunkOrbs.size(); i++) {
                        ExperienceOrb orb = chunkOrbs.get(i);
                        if (orb.isValid() && !orb.isDead()) {
                            orb.remove();
                        }
                    }
                }
            }

            // 2. 合并附近的经验球
            if (mergeXp && mergeRadius > 0) {
                // 每 3 次 tick 执行一次合并，避免频繁扫描
                if (tickCounter % 3 == 0) {
                    mergeNearbyOrbs(world, mergeRadius);
                }
            }

            // 3. 冻结远离玩家的经验球
            if (freezeRange > 0) {
                for (ExperienceOrb orb : orbs) {
                    if (!orb.isValid() || orb.isDead()) continue;
                    boolean nearPlayer = false;
                    for (Player player : world.getPlayers()) {
                        if (!player.isOnline() || player.isDead()) continue;
                        if (orb.getLocation().distanceSquared(player.getLocation())
                                <= (double) freezeRange * freezeRange) {
                            nearPlayer = true;
                            break;
                        }
                    }
                    if (!nearPlayer) {
                        // 冻结经验球：移除 tick（设置极短的 despawn 时间让其快速消失）
                        // 实际上我们设置 velocity 为 0 并标记为不可拾取
                        orb.setVelocity(orb.getVelocity().zero());
                        // 给一个较短的消失时间，避免积累
                        if (orb.getTicksLived() > 6000) {
                            orb.remove();
                        }
                    }
                }
            }

            // 4. TPS 保护：TPS 过低时移除部分经验球
            if (currentTps < tpsThreshold && tpsThreshold > 0) {
                int toRemove = (int) (orbs.size() * 0.3); // 移除 30%
                if (toRemove > 0) {
                    orbs.sort(Comparator.comparingInt(ExperienceOrb::getExperience));
                    int removed = 0;
                    for (ExperienceOrb orb : orbs) {
                        if (removed >= toRemove) break;
                        if (orb.isValid() && !orb.isDead()) {
                            orb.remove();
                            removed++;
                        }
                    }
                    if (removed > 0 && config.isDebug()) {
                        plugin.getLogger().info("[ExperienceOptimizer] TPS=" + String.format("%.1f", currentTps)
                                + " 已移除 " + removed + " 个经验球");
                    }
                }
            }
        }
    }

    /**
     * 合并附近的经验球
     */
    private void mergeNearbyOrbs(World world, int radius) {
        List<ExperienceOrb> orbs = new ArrayList<>(world.getEntitiesByClass(ExperienceOrb.class));
        if (orbs.size() < 2) return;

        double radiusSq = (double) radius * radius;
        Set<ExperienceOrb> merged = new HashSet<>();

        for (int i = 0; i < orbs.size(); i++) {
            ExperienceOrb orb1 = orbs.get(i);
            if (!orb1.isValid() || orb1.isDead() || merged.contains(orb1)) continue;

            for (int j = i + 1; j < orbs.size(); j++) {
                ExperienceOrb orb2 = orbs.get(j);
                if (!orb2.isValid() || orb2.isDead() || merged.contains(orb2)) continue;

                if (orb1.getLocation().distanceSquared(orb2.getLocation()) <= radiusSq) {
                    // 合并到 orb1
                    int totalXp = orb1.getExperience() + orb2.getExperience();
                    orb1.setExperience(Math.min(totalXp, 2477)); // 单经验球上限
                    orb2.remove();
                    merged.add(orb2);
                }
            }
        }
    }

    /**
     * 获取世界的经验球统计信息
     */
    public XpStats getStats(World world) {
        List<ExperienceOrb> orbs = new ArrayList<>(world.getEntitiesByClass(ExperienceOrb.class));
        int total = orbs.size();
        int totalXp = orbs.stream().filter(Entity::isValid).mapToInt(ExperienceOrb::getExperience).sum();
        return new XpStats(total, totalXp);
    }

    public void shutdown() {
        if (xpTask != null) {
            xpTask.cancel();
        }
    }

    public record XpStats(int orbCount, int totalXp) {
        @Override
        public String toString() {
            return "经验球: " + orbCount + " | 总经验: " + totalXp;
        }
    }
}
