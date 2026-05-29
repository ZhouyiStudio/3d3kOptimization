package com.atomgit.plugin3d3k.optimizers;

import com.atomgit.plugin3d3k.Plugin3d3k;
import com.atomgit.plugin3d3k.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.*;

/**
 * 红石优化器
 * - 检测和限制高频红石
 * - 可选禁用活塞
 */
public class RedstoneOptimizer implements Listener {

    private final Plugin3d3k plugin;
    private final ConfigManager config;

    // 高频红石检测：记录每个方块位置的红石事件时间戳
    private final Map<String, RedstoneActivity> activityMap = new HashMap<>();
    // 被惩罚的方块位置（在惩罚期内不再处理事件）
    private final Set<String> penalizedBlocks = new HashSet<>();

    public RedstoneOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        // 注册红石事件监听
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 定期清理过期的活动记录
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            activityMap.entrySet().removeIf(entry ->
                    now - entry.getValue().lastUpdate > 5000);
            penalizedBlocks.removeIf(loc -> {
                RedstoneActivity activity = activityMap.get(loc);
                return activity == null || now - activity.lastUpdate > config.getHighFrequencyPenalty() * 50L + 100;
            });
        }, 200L, 200L);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        if (!config.isRedstoneOptimizerEnabled()) return;

        Block block = event.getBlock();
        String locKey = blockKey(block);

        // 高频红石检测
        if (config.isLimitHighFrequency() && isHighFrequency(locKey, block)) {
            event.setNewCurrent(0); // 阻止红石信号
        }

        // 活塞禁用
        if (config.isDisablePistons() && isPistonRelated(block.getType())) {
            event.setNewCurrent(0);
        }
    }

    /**
     * 高频红石检测算法
     */
    private boolean isHighFrequency(String locKey, Block block) {
        long now = System.currentTimeMillis();

        // 被惩罚的方块直接阻止
        if (penalizedBlocks.contains(locKey)) {
            return true;
        }

        RedstoneActivity activity = activityMap.get(locKey);
        if (activity == null) {
            activity = new RedstoneActivity();
            activityMap.put(locKey, activity);
        }

        // 记录事件
        activity.addEvent(now);

        // 检查是否高频（在短时间内激活次数超过阈值）
        if (activity.getEventCount(1000) > config.getHighFrequencyThreshold()) {
            // 加入惩罚列表
            penalizedBlocks.add(locKey);
            if (config.isDebug()) {
                plugin.getLogger().warning("[Redstone] 检测到高频红石: " + locKey +
                        " (" + activity.getEventCount(1000) + "次/秒)");
            }
            return true;
        }

        return false;
    }

    /**
     * 判断是否与活塞相关的方块
     */
    private boolean isPistonRelated(Material material) {
        return material == Material.PISTON ||
                material == Material.PISTON_HEAD ||
                material == Material.STICKY_PISTON ||
                material == Material.MOVING_PISTON;
    }

    /**
     * 生成方块的唯一键
     */
    private String blockKey(Block block) {
        return block.getWorld().getName() + "@" +
                block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /**
     * 红石活动记录
     */
    private static class RedstoneActivity {
        private final List<Long> timestamps = new ArrayList<>();
        private long lastUpdate;

        public void addEvent(long time) {
            timestamps.add(time);
            lastUpdate = time;
            // 只保留最近1秒的记录
            timestamps.removeIf(t -> time - t > 1000);
        }

        /**
         * 获取指定时间窗口内的事件数
         */
        public int getEventCount(long windowMs) {
            long now = System.currentTimeMillis();
            return (int) timestamps.stream()
                    .filter(t -> now - t <= windowMs)
                    .count();
        }
    }
}
