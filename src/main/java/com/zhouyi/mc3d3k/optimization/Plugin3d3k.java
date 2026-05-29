package com.zhouyi.mc3d3k.optimization;

import com.zhouyi.mc3d3k.optimization.commands.MainCommand;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import com.zhouyi.mc3d3k.optimization.listeners.OptimizationListener;
import com.zhouyi.mc3d3k.optimization.monitor.DetectionManager;
import com.zhouyi.mc3d3k.optimization.monitor.PerformanceMonitor;
import com.zhouyi.mc3d3k.optimization.optimizers.AIOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.ChunkOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.EntityOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.HopperOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.MobLimiter;
import com.zhouyi.mc3d3k.optimization.optimizers.RedstoneOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.CollisionOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.LightOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.TNTOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.VillagerOptimizer;
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
    private HopperOptimizer hopperOptimizer;
    private TNTOptimizer tntOptimizer;
    private AIOptimizer aiOptimizer;
    private VillagerOptimizer villagerOptimizer;
    private CollisionOptimizer collisionOptimizer;
    private LightOptimizer lightOptimizer;
    private DetectionManager detectionManager;

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

        // 启动异常检测
        startDetection();

        // 启动完成信息
        getServer().getConsoleSender().sendMessage(
                Component.text("┌──────────────────────────────────────────┐", NamedTextColor.AQUA)
        );
        getServer().getConsoleSender().sendMessage(
                Component.text("│  §b3d3kOptimization §fv" + getDescription().getVersion() + " §7已加载      │", NamedTextColor.AQUA)
        );
        getServer().getConsoleSender().sendMessage(
                Component.text("│  §7适配 §ePurpur 1.21.1 §7- §b3d3k §7服务器  │", NamedTextColor.AQUA)
        );
        getServer().getConsoleSender().sendMessage(
                Component.text("└──────────────────────────────────────────┘", NamedTextColor.AQUA)
        );

        getLogger().info("3d3kOptimization 已成功加载！所有优化模块已" +
                (enabledAll ? "全部" : "部分") + "启用。");
    }

    @Override
    public void onDisable() {
        // 停止异常检测
        if (detectionManager != null) {
            detectionManager.stop();
        }

        // 停止监控
        if (performanceMonitor != null) {
            performanceMonitor.stop();
        }

        // 清理
        if (chunkOptimizer != null) {
            chunkOptimizer.cleanup();
        }

        // 关闭优化器
        if (entityOptimizer != null) {
            entityOptimizer.shutdown();
        }
        if (hopperOptimizer != null) {
            hopperOptimizer.shutdown();
        }
        if (tntOptimizer != null) {
            tntOptimizer.shutdown();
        }
        if (aiOptimizer != null) {
            aiOptimizer.shutdown();
        }
        if (villagerOptimizer != null) {
            villagerOptimizer.shutdown();
        }
        if (collisionOptimizer != null) {
            collisionOptimizer.shutdown();
        }
        if (lightOptimizer != null) {
            lightOptimizer.shutdown();
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

        // 漏斗优化
        if (configManager.isHopperOptimizerEnabled()) {
            this.hopperOptimizer = new HopperOptimizer(this);
            hopperOptimizer.init();
            getLogger().info("✓ 漏斗优化已加载");
        }

        // TNT 优化
        if (configManager.isTntOptimizerEnabled()) {
            this.tntOptimizer = new TNTOptimizer(this);
            tntOptimizer.init();
            getLogger().info("✓ TNT 优化已加载");
        }

        // AI 优化
        if (configManager.isAiOptimizerEnabled()) {
            this.aiOptimizer = new AIOptimizer(this);
            aiOptimizer.init();
            getLogger().info("✓ AI 优化已加载");
        }

        // 村民优化
        if (configManager.isVillagerOptimizerEnabled()) {
            this.villagerOptimizer = new VillagerOptimizer(this);
            villagerOptimizer.init();
            getLogger().info("✓ 村民优化已加载");
        }

        // 碰撞箱优化
        if (configManager.isCollisionOptimizerEnabled()) {
            this.collisionOptimizer = new CollisionOptimizer(this);
            collisionOptimizer.init();
            getLogger().info("✓ 碰撞箱优化已加载");
        }

        // 光照优化
        if (configManager.isLightOptimizerEnabled()) {
            this.lightOptimizer = new LightOptimizer(this);
            lightOptimizer.init();
            getLogger().info("✓ 光照优化已加载");
        }

        this.enabledAll =
                configManager.isEntityOptimizerEnabled() &&
                configManager.isRedstoneOptimizerEnabled() &&
                configManager.isChunkOptimizerEnabled() &&
                configManager.isMobLimiterEnabled() &&
                configManager.isHopperOptimizerEnabled() &&
                configManager.isTntOptimizerEnabled() &&
                configManager.isAiOptimizerEnabled() &&
                configManager.isVillagerOptimizerEnabled() &&
                configManager.isCollisionOptimizerEnabled() &&
                configManager.isLightOptimizerEnabled();
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

    private void startDetection() {
        if (configManager.isDetectionEnabled()) {
            this.detectionManager = new DetectionManager(this);
            detectionManager.start();
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

    public DetectionManager getDetectionManager() {
        return detectionManager;
    }
}
