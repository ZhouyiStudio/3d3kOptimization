package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 玩家优化器
 * - AFK 检测：长时间挂机自动踢出
 * - 动态视距：TPS 低时自动缩减玩家视距
 * - 交互节流：限制容器打开等操作频率
 * - 低TPS抑制：TPS 极低时阻止某些操作
 */
public class PlayerOptimizer implements Listener {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable checkTask;

    // AFK 跟踪
    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Set<UUID> afkWarned = new HashSet<>();

    // 交互节流
    private final Map<UUID, Long> lastContainerOpen = new HashMap<>();
    private final Map<UUID, Long> lastCommandTime = new HashMap<>();

    // 当前视距（用于动态缩减后的恢复）
    private int defaultViewDistance;
    private int currentAppliedViewDistance;
    private boolean viewDistanceReduced = false;

    public PlayerOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.defaultViewDistance = Bukkit.getViewDistance();
        this.currentAppliedViewDistance = defaultViewDistance;
    }

    public void init() {
        if (!config.isPlayerOptimizerEnabled()) return;

        // 注册事件监听
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // 启动定时检查任务
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        // 每 20 tick（1秒）检查一次
        checkTask.runTaskTimer(plugin, 100L, 20L);

        plugin.getLogger().info("✓ 玩家优化已加载");
    }

    private void tick() {
        checkAFK();
        checkDynamicViewDistance();
    }

    // ══════════════════════════════════════════
    //  AFK 检测
    // ══════════════════════════════════════════

    private void checkAFK() {
        if (!config.isPlayerAfkEnabled()) return;
        long timeoutMs = config.getPlayerAfkTimeout() * 1000L;
        long warnMs = config.getPlayerAfkWarnSeconds() * 1000L;
        long now = System.currentTimeMillis();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("3d3k.bypass.afk")) continue;

            UUID uuid = player.getUniqueId();
            long inactive = now - lastActivity.getOrDefault(uuid, now);

            if (inactive >= timeoutMs) {
                // 超时踢出
                if (config.isPlayerAfkKick()) {
                    player.kick(Component.text("§c您因长时间挂机已被踢出服务器。\n§7如需回来请重新加入。"));
                    plugin.getLogger().info("[AFK] 踢出挂机玩家: " + player.getName());
                }
                // 不移除 lastActivity，防止反复踢出（玩家重进后 UUID 相同但 Bukkit.getOnlinePlayers 会更新）
                continue;
            }

            // 警告提醒
            if (config.isPlayerAfkWarnBeforeKick() && inactive >= timeoutMs - warnMs) {
                if (!afkWarned.contains(uuid)) {
                    int secondsLeft = (int) ((timeoutMs - inactive) / 1000);
                    player.sendMessage(
                            Component.text("§e⚠ 您已挂机 " + (inactive / 1000) + " 秒，", NamedTextColor.YELLOW)
                                    .append(Component.text("将在 " + secondsLeft + " 秒后被踢出！", NamedTextColor.RED))
                    );
                    afkWarned.add(uuid);
                }
            } else {
                afkWarned.remove(uuid);
            }
        }
    }

    /**
     * 记录玩家活动（由事件调用）
     */
    public void recordActivity(Player player) {
        UUID uuid = player.getUniqueId();
        lastActivity.put(uuid, System.currentTimeMillis());
        afkWarned.remove(uuid);
    }

    // ══════════════════════════════════════════
    //  动态视距管理
    // ══════════════════════════════════════════

    private void checkDynamicViewDistance() {
        if (!config.isPlayerDynamicViewDistanceEnabled()) return;

        double tps = Bukkit.getTPS()[0];
        double threshold = config.getPlayerDynamicViewTpsThreshold();

        if (tps < threshold) {
            // TPS 低，缩减视距
            int reduced = Math.max(config.getPlayerDynamicViewMinDistance(), 2);
            if (reduced < currentAppliedViewDistance || !viewDistanceReduced) {
                applyViewDistance(reduced);
                viewDistanceReduced = true;
                if (config.isDebug()) {
                    plugin.getLogger().info("[动态视距] TPS=" + String.format("%.1f", tps) + " < " + threshold + "，缩减视距至 " + reduced);
                }
            }
        } else {
            // TPS 恢复，还原视距
            if (viewDistanceReduced) {
                applyViewDistance(defaultViewDistance);
                viewDistanceReduced = false;
                if (config.isDebug()) {
                    plugin.getLogger().info("[动态视距] TPS 恢复至 " + String.format("%.1f", tps) + "，还原视距至 " + defaultViewDistance);
                }
            }
        }
    }

    private void applyViewDistance(int distance) {
        currentAppliedViewDistance = distance;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            world.setViewDistance(distance);
        }
    }

    // ══════════════════════════════════════════
    //  事件监听（交互节流 + 活动记录）
    // ══════════════════════════════════════════

    /**
     * 玩家移动 → 记录活动（反 AFK）
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        recordActivity(event.getPlayer());
    }

    /**
     * 玩家交互（右键点击方块/实体）→ 记录活动
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        recordActivity(event.getPlayer());
    }

    /**
     * 玩家聊天 → 记录活动
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        recordActivity(event.getPlayer());
    }

    /**
     * 容器打开节流 — 防止高频点击容器导致卡顿
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!config.isPlayerActionThrottleEnabled()) return;
        if (player.hasPermission("3d3k.bypass.throttle")) return;

        int interval = config.getPlayerActionContainerInterval();
        if (interval <= 0) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastContainerOpen.get(uuid);

        if (lastTime != null && (now - lastTime) < interval * 50L) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("§c请稍后再打开容器", NamedTextColor.RED));
        } else {
            lastContainerOpen.put(uuid, now);
        }
    }

    /**
     * 命令执行节流 — 防止高频命令
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        recordActivity(player);

        if (!config.isPlayerActionThrottleEnabled()) return;
        if (player.hasPermission("3d3k.bypass.throttle")) return;

        int interval = config.getPlayerActionCommandInterval();
        if (interval <= 0) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastTime = lastCommandTime.get(uuid);

        if (lastTime != null && (now - lastTime) < interval * 50L) {
            event.setCancelled(true);
            player.sendMessage(Component.text("§c命令执行过快，请稍后再试。", NamedTextColor.RED));
        } else {
            lastCommandTime.put(uuid, now);
        }
    }

    /**
     * 玩家加入 → 初始化活动时间
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * 玩家退出 → 清理缓存
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastActivity.remove(uuid);
        afkWarned.remove(uuid);
        lastContainerOpen.remove(uuid);
        lastCommandTime.remove(uuid);
    }

    // ══════════════════════════════════════════
    //  生命周期
    // ══════════════════════════════════════════

    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }
        // 恢复默认视距
        if (viewDistanceReduced) {
            applyViewDistance(defaultViewDistance);
            viewDistanceReduced = false;
        }
        // 清理缓存
        lastActivity.clear();
        afkWarned.clear();
        lastContainerOpen.clear();
        lastCommandTime.clear();
    }

    public int getAfkCount() {
        if (!config.isPlayerAfkEnabled()) return 0;
        long timeoutMs = config.getPlayerAfkTimeout() * 1000L;
        long now = System.currentTimeMillis();
        return (int) Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.hasPermission("3d3k.bypass.afk"))
                .filter(p -> (now - lastActivity.getOrDefault(p.getUniqueId(), now)) >= timeoutMs * 0.8)
                .count();
    }

    public boolean isViewDistanceReduced() {
        return viewDistanceReduced;
    }

    public int getCurrentViewDistance() {
        return currentAppliedViewDistance;
    }
}
