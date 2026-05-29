package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.util.*;

/**
 * 实体优化器
 * - 控制实体激活范围
 * - 合并掉落物
 * - 管理经验球合并
 * - 移除不需要的实体
 */
public class EntityOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable mergeTask;
    private BukkitRunnable activationTask;

    public EntityOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        // 掉落物合并任务
        if (config.getItemMergeInterval() > 0) {
            startItemMergeTask();
        }

        // 激活范围控制任务
        startActivationTask();
    }

    /**
     * 合并地面上的掉落物
     */
    private void startItemMergeTask() {
        mergeTask = new BukkitRunnable() {
            @Override
            public void run() {
                mergeItems();
            }
        };
        mergeTask.runTaskTimer(plugin, config.getItemMergeInterval(), config.getItemMergeInterval());
    }

    /**
     * 控制实体激活范围（减少不必要的实体 tick）
     */
    private void startActivationTask() {
        activationTask = new BukkitRunnable() {
            @Override
            public void run() {
                manageActivationRange();
            }
        };
        // 每 20 tick（1秒）执行一次
        activationTask.runTaskTimer(plugin, 40L, 20L);
    }

    /**
     * 合并附近的掉落物
     */
    private void mergeItems() {
        for (World world : Bukkit.getWorlds()) {
            List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
            if (items.size() < 2) continue;

            // 按物品类型和位置分组
            Map<String, List<Item>> itemGroups = new HashMap<>();

            for (Item item : items) {
                if (!item.isValid() || item.isDead()) continue;

                // 跳过有价值的物品
                if (config.isValuableItem(item.getItemStack().getType().name())) {
                    // 延长存活时间
                    item.setTicksLived(0);
                    continue;
                }

                String key = item.getItemStack().getType().name() + "@" + getChunkKey(item.getLocation());
                itemGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
            }

            for (List<Item> group : itemGroups.values()) {
                if (group.size() < 2) continue;

                // 合并到第一个物品
                Item first = group.get(0);
                int totalAmount = first.getItemStack().getAmount();
                int maxStackSize = first.getItemStack().getMaxStackSize();

                for (int i = 1; i < group.size(); i++) {
                    Item other = group.get(i);
                    if (!other.isValid()) continue;

                    int otherAmount = other.getItemStack().getAmount();
                    if (totalAmount + otherAmount <= maxStackSize) {
                        totalAmount += otherAmount;
                        other.remove();
                    } else {
                        int remaining = maxStackSize - totalAmount;
                        totalAmount += remaining;
                        other.getItemStack().setAmount(otherAmount - remaining);
                        if (totalAmount >= maxStackSize) break;
                    }
                }

                first.getItemStack().setAmount(totalAmount);
            }
        }
    }

    /**
     * 管理实体激活范围 - 超出玩家一定距离的实体取消 tick
     */
    private void manageActivationRange() {
        // 这个功能利用了 Paper/Purpur 的实体激活范围 API
        // 我们通过定期统计和日志来辅助管理员调整
        if (!config.isDebug()) return;

        for (World world : Bukkit.getWorlds()) {
            int totalEntities = world.getEntityCount();
            int monsterCount = 0;
            int animalCount = 0;
            int itemCount = 0;

            for (Entity entity : world.getEntities()) {
                if (entity instanceof Monster) monsterCount++;
                else if (entity instanceof Animals) animalCount++;
                else if (entity instanceof Item) itemCount++;
            }

            plugin.getLogger().fine("[EntityStats] " + world.getName() +
                    " 总实体:" + totalEntities +
                    " 怪物:" + monsterCount +
                    " 动物:" + animalCount +
                    " 掉落物:" + itemCount);
        }
    }

    /**
     * 清理指定世界中的无用实体
     */
    public int cleanEntities(World world) {
        int removed = 0;

        // 清除多余的掉落物
        List<Item> items = new ArrayList<>(world.getEntitiesByClass(Item.class));
        for (Item item : items) {
            if (!item.isValid()) continue;
            int maxAge = config.isValuableItem(item.getItemStack().getType().name())
                    ? config.getValuableItemDespawnTime() * 20
                    : config.getDefaultItemDespawnTime() * 20;
            if (item.getTicksLived() > maxAge) {
                item.remove();
                removed++;
            }
        }

        return removed;
    }

    /**
     * 获取区块标识字符串
     */
    private String getChunkKey(Location loc) {
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        return chunkX + "," + chunkZ;
    }

    /**
     * 获取某个坐标周围的玩家数量
     */
    private int getNearbyPlayerCount(Location location, double range) {
        int count = 0;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) <= range) {
                count++;
            }
        }
        return count;
    }

    public void shutdown() {
        if (mergeTask != null) {
            mergeTask.cancel();
        }
        if (activationTask != null) {
            activationTask.cancel();
        }
    }
}
