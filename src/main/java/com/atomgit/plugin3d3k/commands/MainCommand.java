package com.atomgit.plugin3d3k.commands;

import com.atomgit.plugin3d3k.Plugin3d3k;
import com.atomgit.plugin3d3k.monitor.PerformanceMonitor;
import com.atomgit.plugin3d3k.optimizers.ChunkOptimizer;
import com.atomgit.plugin3d3k.optimizers.EntityOptimizer;
import com.atomgit.plugin3d3k.optimizers.MobLimiter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

/**
 * 3d3k 主命令处理器
 * 用法: /3d3k <reload|status|gc|entities|chunks|help>
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    private final Plugin3d3k plugin;

    private static final List<String> SUB_COMMANDS = List.of(
            "reload", "status", "gc", "entities", "chunks", "help"
    );

    public MainCommand(Plugin3d3k plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "gc" -> handleGc(sender);
            case "entities" -> handleEntities(sender);
            case "chunks" -> handleChunks(sender);
            case "help" -> sendHelp(sender);
            default -> sender.sendMessage(
                    Component.text("未知子命令。使用 /3d3k help 查看帮助。", NamedTextColor.RED)
            );
        }

        return true;
    }

    /**
     * 重新加载配置
     */
    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("3d3k.admin")) {
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return;
        }

        long start = System.currentTimeMillis();
        plugin.reloadConfig();
        long elapsed = System.currentTimeMillis() - start;

        sender.sendMessage(
                Component.text("§a✓ 配置文件已重新加载 (" + elapsed + "ms)")
        );
        plugin.getLogger().info("配置由 " + sender.getName() + " 重新加载。");
    }

    /**
     * 查看服务器性能状态
     */
    private void handleStatus(CommandSender sender) {
        PerformanceMonitor monitor = plugin.getPerformanceMonitor();
        if (monitor == null) {
            sender.sendMessage(Component.text("性能监控未启用。", NamedTextColor.RED));
            return;
        }

        PerformanceMonitor.PerformanceData data = monitor.getPerformanceData();
        sender.sendMessage(Component.text(data.toString()));
    }

    /**
     * 手动触发垃圾回收
     */
    private void handleGc(CommandSender sender) {
        if (!sender.hasPermission("3d3k.admin")) {
            sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(
                Component.text("§e正在执行垃圾回收...")
        );

        long before = Runtime.getRuntime().freeMemory();
        System.gc();
        long after = Runtime.getRuntime().freeMemory();

        long freed = (after - before) / (1024 * 1024);

        // 同时卸载未使用的区块
        int unloadedChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.Chunk chunk : world.getLoadedChunks()) {
                if (world.unloadChunk(chunk.getX(), chunk.getZ(), true)) {
                    unloadedChunks++;
                }
            }
        }

        sender.sendMessage(
                Component.text(String.format(
                        "§a✓ GC完成: 释放 §b%dMB §a内存, 卸载 §b%d §a个区块",
                        freed, unloadedChunks
                ))
        );
    }

    /**
     * 查看实体统计
     */
    private void handleEntities(CommandSender sender) {
        EntityOptimizer optimizer = plugin.getEntityOptimizer();
        MobLimiter mobLimiter = plugin.getMobLimiter();
        if (optimizer == null) {
            sender.sendMessage(Component.text("实体优化未启用。", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("§6§l=== 实体统计 ==="));

        for (World world : Bukkit.getWorlds()) {
            int total = world.getEntityCount();
            int players = world.getPlayers().size();

            if (mobLimiter != null) {
                MobLimiter.MobStats mobStats = mobLimiter.getMobStats(world);
                sender.sendMessage(
                        Component.text(String.format(
                                " §e%s§7: §b%d §7实体 (§b%d §7玩家) %s",
                                world.getName(), total, players, mobStats
                        ))
                );
            } else {
                sender.sendMessage(
                        Component.text(String.format(
                                " §e%s§7: §b%d §7实体 (§b%d §7玩家)",
                                world.getName(), total, players
                        ))
                );
            }
        }

        int totalGlobal = 0;
        for (World world : Bukkit.getWorlds()) {
            totalGlobal += world.getEntityCount();
        }
        sender.sendMessage(
                Component.text(" §7全服实体总数: §b" + totalGlobal)
        );
    }

    /**
     * 查看区块统计
     */
    private void handleChunks(CommandSender sender) {
        ChunkOptimizer optimizer = plugin.getChunkOptimizer();
        if (optimizer == null) {
            sender.sendMessage(Component.text("区块优化未启用。", NamedTextColor.RED));
            return;
        }

        sender.sendMessage(Component.text("§6§l=== 区块统计 ==="));

        int totalChunks = 0;
        for (World world : Bukkit.getWorlds()) {
            ChunkOptimizer.ChunkStats stats = optimizer.getChunkStats(world);
            totalChunks += stats.loadedChunks();
            sender.sendMessage(
                    Component.text(String.format(
                            " §e%s§7: §b%d §7区块 | §b%d §7实体",
                            world.getName(), stats.loadedChunks(), stats.totalEntities()
                    ))
            );
        }

        sender.sendMessage(
                Component.text(" §7全服区块总数: §b" + totalChunks)
        );
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("§6§l=== 3d3kOptimization 帮助 ==="));
        sender.sendMessage(Component.text(" §7/3d3k §ehelp §7- 显示此帮助"));
        sender.sendMessage(Component.text(" §7/3d3k §estatus §7- 查看服务器性能状态"));
        sender.sendMessage(Component.text(" §7/3d3k §eentities §7- 查看实体统计"));
        sender.sendMessage(Component.text(" §7/3d3k §echunks §7- 查看区块统计"));

        if (sender.hasPermission("3d3k.admin")) {
            sender.sendMessage(Component.text(""));
            sender.sendMessage(Component.text(" §c=== 管理员命令 ==="));
            sender.sendMessage(Component.text(" §7/3d3k §creload §7- 重新加载配置文件"));
            sender.sendMessage(Component.text(" §7/3d3k §cgc §7- 手动执行垃圾回收并卸载区块"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUB_COMMANDS, new ArrayList<>());
        }
        return List.of();
    }
}
