package com.atomgit.plugin3d3k.monitor;

import com.atomgit.plugin3d3k.Plugin3d3k;
import com.atomgit.plugin3d3k.config.ConfigManager;
import com.atomgit.plugin3d3k.optimizers.MobLimiter;
import com.atomgit.plugin3d3k.optimizers.ChunkOptimizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 * 性能监控器
 * - TPS 监控
 * - 内存监控
 * - 实体/区块统计
 * - 低 TPS 告警
 */
public class PerformanceMonitor {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable monitorTask;

    // TPS 历史记录
    private final double[] tpsHistory = new double[10];
    private int historyIndex = 0;

    public PerformanceMonitor(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void start() {
        monitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                collectAndReport();
            }
        };
        monitorTask.runTaskTimer(plugin, config.getMonitorInterval(), config.getMonitorInterval());
    }

    public void stop() {
        if (monitorTask != null) {
            monitorTask.cancel();
        }
    }

    /**
     * 收集性能数据并报告
     */
    private void collectAndReport() {
        double tps = Bukkit.getTPS()[0]; // 过去 1 分钟的平均 TPS
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long usedMemory = heapUsage.getUsed() / (1024 * 1024);
        long maxMemory = heapUsage.getMax() / (1024 * 1024);
        double memoryPercent = (double) usedMemory / maxMemory * 100;

        int totalPlayers = Bukkit.getOnlinePlayers().size();
        int totalEntities = 0;
        int totalChunks = 0;

        for (World world : Bukkit.getWorlds()) {
            totalEntities += world.getEntityCount();
            totalChunks += world.getLoadedChunks().length;
        }

        // 记录 TPS 历史
        tpsHistory[historyIndex % tpsHistory.length] = tps;
        historyIndex++;

        // 构建性能报告
        if (config.isPrintToConsole()) {
            printReport(tps, usedMemory, maxMemory, memoryPercent,
                    totalPlayers, totalEntities, totalChunks);
        }

        // TPS 告警
        if (tps < config.getTpsAlertThreshold()) {
            alertLowTps(tps);
        }
    }

    /**
     * 打印性能报告到控制台
     */
    private void printReport(double tps, long usedMem, long maxMem,
                             double memPercent, int players, int entities, int chunks) {
        TextColor tpsColor = tps >= 19.0 ? NamedTextColor.GREEN :
                tps >= 16.0 ? NamedTextColor.YELLOW : NamedTextColor.RED;

        TextColor memColor = memPercent < 60 ? NamedTextColor.GREEN :
                memPercent < 80 ? NamedTextColor.YELLOW : NamedTextColor.RED;

        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8┌── §73d3k性能报告 §8─────────────────────┐")
        );
        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8│ ")
                        .append(Component.text(" §7TPS: ", NamedTextColor.GRAY))
                        .append(Component.text(String.format("%.1f", tps), tpsColor))
                        .append(Component.text(" §8| §7内存: ", NamedTextColor.GRAY))
                        .append(Component.text(usedMem + "MB/" + maxMem + "MB", memColor))
                        .append(Component.text(String.format(" (%.0f%%)", memPercent), NamedTextColor.GRAY))
        );
        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8│ ")
                        .append(Component.text(" §7玩家: ", NamedTextColor.GRAY))
                        .append(Component.text(players, NamedTextColor.AQUA))
                        .append(Component.text(" §8| §7实体: ", NamedTextColor.GRAY))
                        .append(Component.text(entities, NamedTextColor.AQUA))
                        .append(Component.text(" §8| §7区块: ", NamedTextColor.GRAY))
                        .append(Component.text(chunks, NamedTextColor.AQUA))
        );
        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8│ ")
                        .append(Component.text(" §7平均TPS(10次): ", NamedTextColor.GRAY))
                        .append(Component.text(String.format("%.1f", getAverageTps()),
                                getAverageTps() >= 19.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW))
        );
        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8└─────────────────────────────────────┘")
        );
    }

    /**
     * 低 TPS 告警
     */
    private void alertLowTps(double tps) {
        String message = "§c§l⚠ §4TPS过低！当前: " + String.format("%.1f", tps) +
                " §c建议检查红石机器或实体数量";

        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8┌── §c§l⚠ TPS 告警 §8──────────────────────┐")
        );
        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8│ §c当前 TPS: " + String.format("%.1f", tps) +
                        " §8| §7阈値: " + config.getTpsAlertThreshold())
        );
        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8│ §7建议: 检查高频红石、实体数量、区块加载")
        );
        Bukkit.getConsoleSender().sendMessage(
                Component.text("§8└─────────────────────────────────────┘")
        );

        // 广播给有权限的玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(config.getBroadcastPermission())) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * 获取详细的性能数据
     */
    public PerformanceData getPerformanceData() {
        double tps = Bukkit.getTPS()[0];
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

        long usedMemory = heapUsage.getUsed() / (1024 * 1024);
        long maxMemory = heapUsage.getMax() / (1024 * 1024);

        int totalEntities = 0;
        int totalChunks = 0;

        for (World world : Bukkit.getWorlds()) {
            totalEntities += world.getEntityCount();
            totalChunks += world.getLoadedChunks().length;
        }

        return new PerformanceData(
                tps,
                getAverageTps(),
                usedMemory,
                maxMemory,
                Bukkit.getOnlinePlayers().size(),
                totalEntities,
                totalChunks
        );
    }

    /**
     * 获取平均 TPS
     */
    public double getAverageTps() {
        double sum = 0;
        int count = 0;
        for (double t : tpsHistory) {
            if (t > 0) {
                sum += t;
                count++;
            }
        }
        return count > 0 ? sum / count : 20.0;
    }

    /**
     * 性能数据记录
     */
    public record PerformanceData(
            double currentTps,
            double averageTps,
            long usedMemoryMB,
            long maxMemoryMB,
            int onlinePlayers,
            int totalEntities,
            int totalChunks
    ) {
        @Override
        public String toString() {
            return String.format(
                    "§6§l=== 3d3k 性能状态 ===\n" +
                    " §7TPS: §b%.1f §7(平均: §b%.1f§7)\n" +
                    " §7内存: §b%dMB §7/ §b%dMB\n" +
                    " §7在线玩家: §b%d\n" +
                    " §7实体总数: §b%d\n" +
                    " §7区块总数: §b%d",
                    currentTps, averageTps,
                    usedMemoryMB, maxMemoryMB,
                    onlinePlayers, totalEntities, totalChunks
            );
        }
    }
}
