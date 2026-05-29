package com.zhouyi.mc3d3k.optimization.monitor;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 异常检测管理器
 * - 掉落物堆积检测：同一位置大量掉落物堆积导致卡顿
 * - 实体密集区域检测：实体过于集中的区块
 * - 区块负载检测：高负载区块（实体多、红石活跃）
 * - 实体生成风暴检测：短时间内大量实体生成
 * - 玩家行为检测：检测可能导致卡顿的玩家行为
 */
public class DetectionManager {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable detectionTask;

    // ─── 检测告警冷却 ───
    private final Map<String, Long> lastAlerts = new HashMap<>();

    // ─── 实体生成风暴检测 ───
    private final LinkedList<Integer> spawnRateSamples = new LinkedList<>();
    private int currentSpawnCount = 0;

    // ─── 玩家行为检测 ───
    private final Map<UUID, PlayerActivity> playerActivities = new ConcurrentHashMap<>();

    // ─── 检测报告缓存 ───
    private DetectionReport lastReport;
    private long lastReportTime = 0;

    public DetectionManager(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void start() {
        if (!config.isDetectionEnabled()) return;

        detectionTask = new BukkitRunnable() {
            @Override
            public void run() {
                runAllDetections();
            }
        };
        detectionTask.runTaskTimer(plugin, config.getDetectionCheckInterval(), config.getDetectionCheckInterval());

        // 注册实体生成计数监听（通过定时器采样）
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            spawnRateSamples.addLast(currentSpawnCount);
            if (spawnRateSamples.size() > 20) { // 最多保留 20 个采样点
                spawnRateSamples.removeFirst();
            }
            currentSpawnCount = 0;
        }, 20L, 20L); // 每秒采样一次

        plugin.getLogger().info("✓ 异常检测已启动");
    }

    public void stop() {
        if (detectionTask != null) {
            detectionTask.cancel();
        }
    }

    /**
     * 记录实体生成（由监听器调用）
     */
    public void recordEntitySpawn() {
        currentSpawnCount++;
    }

    /**
     * 记录玩家命令执行
     */
    public void recordPlayerCommand(Player player) {
        if (!config.isPlayerBehaviorDetectionEnabled()) return;

        PlayerActivity activity = playerActivities.computeIfAbsent(
                player.getUniqueId(), k -> new PlayerActivity());
        activity.addCommand();
    }

    /**
     * 运行所有检测
     */
    private void runAllDetections() {
        if (!config.isDetectionEnabled()) return;

        List<String> alerts = new ArrayList<>();

        // 掉落物堆积检测
        if (config.isItemPileDetectionEnabled()) {
            checkItemPile(alerts);
        }

        // 实体密集区域检测
        if (config.isEntityDensityDetectionEnabled()) {
            checkEntityDensity(alerts);
        }

        // 区块负载检测
        if (config.isChunkLoadDetectionEnabled()) {
            checkChunkLoad(alerts);
        }

        // 实体生成风暴检测
        if (config.isSpawnStormDetectionEnabled()) {
            checkSpawnStorm(alerts);
        }

        // 玩家行为检测
        if (config.isPlayerBehaviorDetectionEnabled()) {
            checkPlayerBehavior(alerts);
        }

        // 输出告警
        if (!alerts.isEmpty()) {
            for (String alert : alerts) {
                plugin.getLogger().warning("[检测] " + alert);
                // 广播给管理员
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("3d3k.admin") || player.isOp()) {
                        player.sendMessage(
                                Component.text("§c⚠ [异常检测] ", NamedTextColor.RED)
                                        .append(Component.text(alert, NamedTextColor.YELLOW))
                        );
                    }
                }
            }
        }

        // 更新检测报告
        buildReport();
    }

    /**
     * 掉落物堆积检测
     */
    private void checkItemPile(List<String> alerts) {
        int maxPerChunk = config.getItemPileMaxPerChunk();
        int cooldown = config.getItemPileAlertCooldown();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            // 按区块统计掉落物
            Map<String, Integer> itemCountByChunk = new HashMap<>();
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!item.isValid()) continue;
                String chunkKey = getChunkKey(item.getLocation());
                itemCountByChunk.merge(chunkKey, 1, Integer::sum);
            }

            // 检查是否有超出阈值的区块
            for (Map.Entry<String, Integer> entry : itemCountByChunk.entrySet()) {
                if (entry.getValue() > maxPerChunk) {
                    String alertKey = "item-pile:" + world.getName() + "@" + entry.getKey();
                    if (canAlert(alertKey, cooldown)) {
                        alerts.add(String.format(
                                "§e[掉落物堆积] §f%s §7区块 %s 有 §b%d §7个掉落物(阈值:%d)",
                                world.getName(), entry.getKey(), entry.getValue(), maxPerChunk
                        ));
                    }
                }
            }
        }
    }

    /**
     * 实体密集区域检测
     */
    private void checkEntityDensity(List<String> alerts) {
        int threshold = config.getEntityDensityThreshold();
        int cooldown = config.getEntityDensityAlertCooldown();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            // 按区块统计实体
            Map<String, Integer> entityCountByChunk = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player || entity.isDead() || !entity.isValid()) continue;
                String chunkKey = getChunkKey(entity.getLocation());
                entityCountByChunk.merge(chunkKey, 1, Integer::sum);
            }

            // 检查高密度区块
            for (Map.Entry<String, Integer> entry : entityCountByChunk.entrySet()) {
                if (entry.getValue() > threshold) {
                    String alertKey = "entity-density:" + world.getName() + "@" + entry.getKey();
                    if (canAlert(alertKey, cooldown)) {
                        alerts.add(String.format(
                                "§e[实体密集] §f%s §7区块 %s 有 §b%d §7个实体(阈值:%d)",
                                world.getName(), entry.getKey(), entry.getValue(), threshold
                        ));
                    }
                }
            }
        }
    }

    /**
     * 区块负载检测
     */
    private void checkChunkLoad(List<String> alerts) {
        int entityThreshold = config.getChunkLoadEntityThreshold();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                // 检查区块实体数
                Entity[] entities = chunk.getEntities();
                if (entities.length > entityThreshold) {
                    String alertKey = "chunk-load:" + world.getName() + "@" +
                            chunk.getX() + "," + chunk.getZ();
                    if (canAlert(alertKey, 120)) { // 120 秒冷却
                        // 统计实体类型分布
                        Map<String, Long> typeCount = Arrays.stream(entities)
                                .filter(e -> !(e instanceof Player))
                                .collect(Collectors.groupingBy(
                                        e -> e.getType().name(), Collectors.counting()));

                        // 找出最多的 3 种类型
                        String topTypes = typeCount.entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .limit(3)
                                .map(e -> e.getKey() + ":" + e.getValue())
                                .collect(Collectors.joining(", "));

                        alerts.add(String.format(
                                "§e[区块高负载] §f%s §7[%d,%d] §b%d §7实体 | 主要类型: %s",
                                world.getName(), chunk.getX(), chunk.getZ(),
                                entities.length, topTypes
                        ));
                    }
                }
            }
        }
    }

    /**
     * 实体生成风暴检测
     */
    private void checkSpawnStorm(List<String> alerts) {
        if (spawnRateSamples.isEmpty()) return;

        int maxRate = config.getSpawnStormMaxRate();

        // 计算最近 N 秒的平均生成速率
        int sampleSize = Math.min(spawnRateSamples.size(),
                config.getSpawnStormSampleWindow() / 20);
        if (sampleSize <= 0) return;

        int totalSpawns = 0;
        int count = 0;
        Iterator<Integer> it = spawnRateSamples.descendingIterator();
        while (it.hasNext() && count < sampleSize) {
            totalSpawns += it.next();
            count++;
        }

        double averageRate = (double) totalSpawns / count;
        if (averageRate > maxRate) {
            String alertKey = "spawn-storm:global";
            if (canAlert(alertKey, 120)) {
                alerts.add(String.format(
                        "§e[生成风暴] §7最近 %d 秒平均实体生成速率 §b%.1f §7/秒(阈值:%d/秒)",
                        count, averageRate, maxRate
                ));
            }
        }
    }

    /**
     * 玩家行为检测
     */
    private void checkPlayerBehavior(List<String> alerts) {
        int maxCommands = config.getPlayerBehaviorMaxCommandsPerSecond();
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, PlayerActivity> entry : playerActivities.entrySet()) {
            PlayerActivity activity = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            // 检查命令频率
            double cmdRate = activity.getCommandRate();
            if (cmdRate > maxCommands) {
                String alertKey = "player-cmd:" + entry.getKey();
                if (canAlert(alertKey, 30)) { // 30 秒冷却
                    alerts.add(String.format(
                            "§e[玩家行为] §f%s §7高频命令 §b%.1f §7次/秒(阈值:%d)",
                            player.getName(), cmdRate, maxCommands
                    ));
                }
            }

            // 清理过期数据
            activity.cleanup();
        }
    }

    /**
     * 检查告警冷却
     */
    private boolean canAlert(String key, int cooldownSeconds) {
        long now = System.currentTimeMillis();
        Long lastTime = lastAlerts.get(key);
        if (lastTime != null && now - lastTime < cooldownSeconds * 1000L) {
            return false;
        }
        lastAlerts.put(key, now);
        return true;
    }

    /**
     * 生成区块键
     */
    private String getChunkKey(Location loc) {
        return (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
    }

    /**
     * 构建检测报告
     */
    private void buildReport() {
        int totalItems = 0;
        int itemPileChunks = 0;
        int denseChunks = 0;
        int highLoadChunks = 0;
        int denseThreshold = config.getEntityDensityThreshold();
        int loadThreshold = config.getChunkLoadEntityThreshold();
        int pileThreshold = config.getItemPileMaxPerChunk();

        for (World world : Bukkit.getWorlds()) {
            // 掉落物统计
            Map<String, Integer> itemsByChunk = new HashMap<>();
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (!item.isValid()) continue;
                totalItems++;
                String key = getChunkKey(item.getLocation());
                if (itemsByChunk.merge(key, 1, Integer::sum) > pileThreshold) {
                    itemPileChunks++;
                }
            }

            // 实体密度/负载统计
            Map<String, Integer> entitiesByChunk = new HashMap<>();
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Player || entity.isDead()) continue;
                String key = getChunkKey(entity.getLocation());
                int count = entitiesByChunk.merge(key, 1, Integer::sum);
                if (count > loadThreshold) {
                    highLoadChunks++;
                }
                if (count > denseThreshold) {
                    denseChunks++;
                }
            }
        }

        double avgSpawnRate = 0;
        if (!spawnRateSamples.isEmpty()) {
            avgSpawnRate = spawnRateSamples.stream()
                    .mapToInt(Integer::intValue).average().orElse(0);
        }

        this.lastReport = new DetectionReport(
                totalItems, itemPileChunks, denseChunks, highLoadChunks,
                playerActivities.size(), avgSpawnRate
        );
        this.lastReportTime = System.currentTimeMillis();
    }

    /**
     * 获取最新检测报告
     */
    public DetectionReport getReport() {
        if (lastReport == null || System.currentTimeMillis() - lastReportTime > 5000) {
            buildReport();
        }
        return lastReport;
    }

    /**
     * 玩家活动记录
     */
    private static class PlayerActivity {
        private final LinkedList<Long> commandTimestamps = new LinkedList<>();

        public void addCommand() {
            commandTimestamps.addLast(System.currentTimeMillis());
        }

        public double getCommandRate() {
            cleanup();
            if (commandTimestamps.isEmpty()) return 0;
            long oldest = commandTimestamps.getFirst();
            long now = System.currentTimeMillis();
            long window = now - oldest;
            if (window <= 0) return commandTimestamps.size();
            return (double) commandTimestamps.size() / (window / 1000.0);
        }

        public void cleanup() {
            long threshold = System.currentTimeMillis() - 5000; // 保留 5 秒内的记录
            commandTimestamps.removeIf(t -> t < threshold);
        }
    }

    /**
     * 检测报告
     */
    public record DetectionReport(
            int totalItems,              // 全服掉落物总数
            int itemPileChunks,          // 掉落物堆积区块数
            int denseChunks,             // 实体密集区块数
            int highLoadChunks,          // 高负载区块数
            int monitoredPlayers,        // 被监控的玩家数
            double avgSpawnRate          // 平均实体生成速率
    ) {
        @Override
        public String toString() {
            return String.format(
                    "§6§l=== 异常检测报告 ===\n" +
                    " §7掉落物: §b%d §7(堆积区块: §c%d§7)\n" +
                    " §7密集区块: §c%d §7| §7高负载区块: §c%d\n" +
                    " §7实体生成速率: §b%.1f §7/秒\n" +
                    " §7监控玩家数: §b%d",
                    totalItems, itemPileChunks,
                    denseChunks, highLoadChunks,
                    avgSpawnRate, monitoredPlayers
            );
        }
    }
}
