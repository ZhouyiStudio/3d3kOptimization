package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 物品清理增强优化器
 * - 按世界配置不同的掉落物清理规则
 * - 支持按世界/按区域清理不同类型的掉落物
 * - 保留有价值的物品和白名单物品
 * - 清理时向管理员发送通知
 * - 可配置清理前的警告时间
 */
public class ItemCleanupOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable cleanupTask;

    // 各世界上次清理的时间
    private final Map<String, Long> lastCleanupTime = new HashMap<>();

    public ItemCleanupOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isItemCleanupEnabled()) return;

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                performCleanup();
            }
        };
        // 按配置的间隔执行清理（秒转 tick）
        int interval = config.getItemCleanupInterval() * 20;
        if (interval <= 0) interval = 6000; // 默认 5 分钟
        cleanupTask.runTaskTimer(plugin, interval, interval);
    }

    /**
     * 执行物品清理
     */
    private void performCleanup() {
        long now = System.currentTimeMillis();
        int totalRemoved = 0;
        int totalKept = 0;
        List<String> worldReports = new ArrayList<>();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            String worldName = world.getName();

            // 检查是否在黑名单世界中
            if (config.getItemCleanupDisabledWorlds().contains(worldName)) continue;

            // 检查清理间隔
            Long lastClean = lastCleanupTime.get(worldName);
            int worldInterval = config.getItemCleanupWorldInterval(worldName);
            if (lastClean != null && worldInterval > 0) {
                long elapsed = now - lastClean;
                if (elapsed < worldInterval * 1000L) continue;
            }

            // 收集该世界的掉落物
            List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
            if (items.isEmpty()) continue;

            int worldRemoved = 0;
            int worldKept = 0;

            // 获取该世界的白名单和黑名单
            Set<String> whitelist = config.getItemCleanupWhitelist(worldName);
            Set<String> blacklist = config.getItemCleanupBlacklist(worldName);
            boolean whitelistMode = config.isItemCleanupWhitelistMode(worldName);

            // 清理模式：false = 移除黑名单物品，true = 保留白名单物品
            for (Item item : items) {
                if (!item.isValid() || item.isDead()) continue;

                ItemStack stack = item.getItemStack();
                String itemName = stack.getType().name();

                // 跳过有价值的物品
                if (config.isValuableItem(itemName)) {
                    worldKept++;
                    continue;
                }

                // 检查是否靠近玩家（玩家附近的不清理）
                boolean nearPlayer = isNearPlayer(item, 8);
                if (nearPlayer) {
                    worldKept++;
                    continue;
                }

                boolean shouldRemove;
                if (whitelistMode) {
                    // 白名单模式：只有白名单中的物品会被清理
                    shouldRemove = whitelist.contains(itemName);
                    // 如果白名单为空，不移除任何物品
                    if (whitelist.isEmpty()) shouldRemove = false;
                } else {
                    // 黑名单模式：黑名单中的物品被清理
                    shouldRemove = blacklist.contains(itemName);
                    // 如果黑名单为空，清理所有非贵重物品
                    if (blacklist.isEmpty()) shouldRemove = true;
                }

                if (shouldRemove) {
                    item.remove();
                    worldRemoved++;
                    totalRemoved++;
                } else {
                    worldKept++;
                    totalKept++;
                }
            }

            if (worldRemoved > 0) {
                lastCleanupTime.put(worldName, now);
                worldReports.add(worldName + ": 清理 " + worldRemoved + " 个物品 (保留 " + worldKept + " 个)");
            }
        }

        // 输出清理报告
        if (!worldReports.isEmpty() && config.isDebug()) {
            plugin.getLogger().info("[ItemCleanup] 物品清理完成: " + String.join(" | ", worldReports));
        }

        // 向有权限的在线玩家发送通知
        if (totalRemoved > 0 && config.isItemCleanupNotifyOps()) {
            String msg = "§e[物品清理] §7已清理 §c" + totalRemoved + " §7个掉落物，保留 §a" + totalKept + " §7个";
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("3d3k.notify.cleanup")) {
                    player.sendMessage(msg);
                }
            }
        }
    }

    /**
     * 判断物品是否在玩家附近
     */
    private boolean isNearPlayer(Item item, int radius) {
        for (Player player : item.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(item.getLocation()) <= radius * radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * 立即执行一次清理（供命令调用）
     */
    public void cleanNow() {
        performCleanup();
    }

    /**
     * 获取指定世界的掉落物统计
     */
    public ItemCleanupStats getItemStats(World world) {
        List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
        int total = 0;
        Map<String, Integer> typeCount = new HashMap<>();
        for (Item item : items) {
            if (!item.isValid() || item.isDead()) continue;
            total++;
            String type = item.getItemStack().getType().name();
            typeCount.merge(type, 1, Integer::sum);
        }
        return new ItemCleanupStats(total, typeCount);
    }

    public void shutdown() {
        lastCleanupTime.clear();
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
    }

    /**
     * 物品清理统计
     */
    public record ItemCleanupStats(int total, Map<String, Integer> typeDistribution) {
        @Override
        public String toString() {
            return "掉落物总计: " + total + " | 类型数: " + typeDistribution.size();
        }
    }
}
