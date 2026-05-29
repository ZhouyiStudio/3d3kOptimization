package com.atomgit.plugin3d3k;

import com.atomgit.plugin3d3k.commands.MainCommand;
import com.atomgit.plugin3d3k.config.ConfigManager;
import com.atomgit.plugin3d3k.listeners.OptimizationListener;
import com.atomgit.plugin3d3k.monitor.PerformanceMonitor;
import com.atomgit.plugin3d3k.optimizers.ChunkOptimizer;
import com.atomgit.plugin3d3k.optimizers.EntityOptimizer;
import com.atomgit.plugin3d3k.optimizers.MobLimiter;
import com.atomgit.plugin3d3k.optimizers.RedstoneOptimizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * 3d3kOptimization - Purpur 1.21.1 服务器性能优化插件主类
 */
public class Plugin3d3k extends JavaPlugin {

    private static Plugin3d3k instance;

    private ConfigManager configManager;
    private EntityOptimizer entityOptimizer;
    private RedstoneOptimizer redstoneOptimizer;
    private ChunkOptimizer chunkOptimizer;
    private MobLimiter mobLimiter;
    private PerformanceMonitor performanceMonitor;
    private OptimizationListener optimizationListener;

    private boolean enabledAll;

    public static Plugin3d3k getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置
        saveDefaultConfig();
        reloadConfig();

        // 初始化配置管理器
        this.configManager = new ConfigManager(this);

        // 初始化优化模块
        initOptimizers();

        // 注册监听器
        registerListeners();

        // 注册命令
        registerCommands();

        // 启动性能监控
        startMonitoring();

        // 启动完成信息
        getServer().getConsoleSender().sendMessage(
                Component.text("┌──────────────────────────────────────────┐", NamedTextColor.AQUA)
        );
        getServer().getConsoleSender().sendMessage(
                Component.text("│  §b3d3kOptimization §fv" + getDescription().getVersion() + " §7已加载      │", NamedTextColor.AQUA)
        );
        getServer().getConsoleSender().sendMessage(
                Component.text("│  §7适配 §ePurpur 1.21.1 §7- §b3d3kmc §7服务器  │", NamedTextColor.AQUA)
        );
        getServer().getConsoleSender().sendMessage(
                Component.text("└──────────────────────────────────────────┘", NamedTextColor.AQUA)
        );

        getLogger().info("3d3kOptimization 已成功加载！所有优化模块已" +
                (enabledAll ? "全部" : "部分") + "启用。");
    }

    @Override
    public void onDisable() {
        // 停止监控
        if (performanceMonitor != null) {
            performanceMonitor.stop();
        }

        // 清理
        if (chunkOptimizer != null) {
            chunkOptimizer.cleanup();
        }

        getLogger().info("3d3kOptimization 已卸载。");
        instance = null;
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        if (configManager != null) {
            configManager.loadConfig(getConfig());
        }
    }

    private void initOptimizers() {
        // 实体优化
        if (configManager.isEntityOptimizerEnabled()) {
            this.entityOptimizer = new EntityOptimizer(this);
            entityOptimizer.init();
            getLogger().info("✓ 实体优化已加载");
        }

        // 红石优化
        if (configManager.isRedstoneOptimizerEnabled()) {
            this.redstoneOptimizer = new RedstoneOptimizer(this);
            redstoneOptimizer.init();
            getLogger().info("✓ 红石优化已加载");
        }

        // 区块优化
        if (configManager.isChunkOptimizerEnabled()) {
            this.chunkOptimizer = new ChunkOptimizer(this);
            chunkOptimizer.init();
            getLogger().info("✓ 区块优化已加载");
        }

        // 生物限制
        if (configManager.isMobLimiterEnabled()) {
            this.mobLimiter = new MobLimiter(this);
            mobLimiter.init();
            getLogger().info("✓ 生物限制已加载");
        }

        this.enabledAll =
                configManager.isEntityOptimizerEnabled() &&
                configManager.isRedstoneOptimizerEnabled() &&
                configManager.isChunkOptimizerEnabled() &&
                configManager.isMobLimiterEnabled();
    }

    private void registerListeners() {
        this.optimizationListener = new OptimizationListener(this);
        getServer().getPluginManager().registerEvents(optimizationListener, this);
    }

    private void registerCommands() {
        MainCommand mainCommand = new MainCommand(this);
        getCommand("3d3k").setExecutor(mainCommand);
        getCommand("3d3k").setTabCompleter(mainCommand);
    }

    private void startMonitoring() {
        if (configManager.isMonitoringEnabled()) {
            this.performanceMonitor = new PerformanceMonitor(this);
            performanceMonitor.start();
            getLogger().info("✓ 性能监控已启动");
        }
    }

    // ─── Getter 方法 ───

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public EntityOptimizer getEntityOptimizer() {
        return entityOptimizer;
    }

    public RedstoneOptimizer getRedstoneOptimizer() {
        return redstoneOptimizer;
    }

    public ChunkOptimizer getChunkOptimizer() {
        return chunkOptimizer;
    }

    public MobLimiter getMobLimiter() {
        return mobLimiter;
    }

    public PerformanceMonitor getPerformanceMonitor() {
        return performanceMonitor;
    }

    public OptimizationListener getOptimizationListener() {
        return optimizationListener;
    }
}
