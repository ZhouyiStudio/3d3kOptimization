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
import com.zhouyi.mc3d3k.optimization.optimizers.PlayerOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.TNTOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.VillagerOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.ProjectileOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.VehicleOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.WaterMobOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.SpawnerOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.ItemCleanupOptimizer;
import com.zhouyi.mc3d3k.optimization.optimizers.ContainerOptimizer;
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
    private PlayerOptimizer playerOptimizer;
    private DetectionManager detectionManager;
    private ProjectileOptimizer projectileOptimizer;
    private VehicleOptimizer vehicleOptimizer;
    private WaterMobOptimizer waterMobOptimizer;
    private SpawnerOptimizer spawnerOptimizer;
    private ItemCleanupOptimizer itemCleanupOptimizer;
    private ContainerOptimizer containerOptimizer;
    private ExperienceOptimizer experienceOptimizer;
    private PhysicsOptimizer physicsOptimizer;
    private TickDistributor tickDistributor;
    private FarmlandOptimizer farmlandOptimizer;

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
        if (playerOptimizer != null) {
            playerOptimizer.shutdown();
        }
        if (projectileOptimizer != null) {
            projectileOptimizer.shutdown();
        }
        if (vehicleOptimizer != null) {
            vehicleOptimizer.shutdown();
        }
        if (waterMobOptimizer != null) {
            waterMobOptimizer.shutdown();
        }
        if (spawnerOptimizer != null) {
            spawnerOptimizer.shutdown();
        }
        if (itemCleanupOptimizer != null) {
            itemCleanupOptimizer.shutdown();
        }
        if (containerOptimizer != null) {
            containerOptimizer.shutdown();
        }
        if (experienceOptimizer != null) {
            experienceOptimizer.shutdown();
        }
        if (physicsOptimizer != null) {
            physicsOptimizer.shutdown();
        }
        if (tickDistributor != null) {
            tickDistributor.shutdown();
        }
        if (farmlandOptimizer != null) {
            farmlandOptimizer.shutdown();
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

        // 玩家优化
        if (configManager.isPlayerOptimizerEnabled()) {
            this.playerOptimizer = new PlayerOptimizer(this);
            playerOptimizer.init();
        }

        // 抛射物优化
        if (configManager.isProjectileOptimizerEnabled()) {
            this.projectileOptimizer = new ProjectileOptimizer(this);
            projectileOptimizer.init();
            getLogger().info("✓ 抛射物优化已加载");
        }

        // 交通工具优化
        if (configManager.isVehicleOptimizerEnabled()) {
            this.vehicleOptimizer = new VehicleOptimizer(this);
            vehicleOptimizer.init();
            getLogger().info("✓ 交通工具优化已加载");
        }

        // 水生生物优化
        if (configManager.isWaterMobOptimizerEnabled()) {
            this.waterMobOptimizer = new WaterMobOptimizer(this);
            waterMobOptimizer.init();
            getLogger().info("✓ 水生生物优化已加载");
        }

        // 刷怪笼优化
        if (configManager.isSpawnerOptimizerEnabled()) {
            this.spawnerOptimizer = new SpawnerOptimizer(this);
            spawnerOptimizer.init();
            getLogger().info("✓ 刷怪笼优化已加载");
        }

        // 物品清理增强
        if (configManager.isItemCleanupEnabled()) {
            this.itemCleanupOptimizer = new ItemCleanupOptimizer(this);
            itemCleanupOptimizer.init();
            getLogger().info("✓ 物品清理增强已加载");
        }

        // 容器优化
        if (configManager.isContainerOptimizerEnabled()) {
            this.containerOptimizer = new ContainerOptimizer(this);
            containerOptimizer.init();
            getLogger().info("✓ 容器优化已加载");
        }

        // 经验球优化
        if (configManager.isExperienceOptimizerEnabled()) {
            this.experienceOptimizer = new ExperienceOptimizer(this);
            experienceOptimizer.init();
            getLogger().info("✓ 经验球优化已加载");
        }

        // 方块物理优化
        if (configManager.isPhysicsOptimizerEnabled()) {
            this.physicsOptimizer = new PhysicsOptimizer(this);
            physicsOptimizer.init();
            getLogger().info("✓ 方块物理优化已加载");
        }

        // Tick 分布优化
        if (configManager.isTickDistributorEnabled()) {
            this.tickDistributor = new TickDistributor(this);
            tickDistributor.init();
            getLogger().info("✓ Tick 分布优化已加载");
        }

        // 农田优化
        if (configManager.isFarmlandOptimizerEnabled()) {
            this.farmlandOptimizer = new FarmlandOptimizer(this);
            farmlandOptimizer.init();
            getLogger().info("✓ 农田优化已加载");
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
                configManager.isLightOptimizerEnabled() &&
                configManager.isPlayerOptimizerEnabled() &&
                configManager.isProjectileOptimizerEnabled() &&
                configManager.isVehicleOptimizerEnabled() &&
                configManager.isWaterMobOptimizerEnabled() &&
                configManager.isSpawnerOptimizerEnabled() &&
                configManager.isItemCleanupEnabled() &&
                configManager.isContainerOptimizerEnabled() &&
                configManager.isExperienceOptimizerEnabled() &&
                configManager.isPhysicsOptimizerEnabled() &&
                configManager.isTickDistributorEnabled() &&
                configManager.isFarmlandOptimizerEnabled();
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

    public PlayerOptimizer getPlayerOptimizer() {
        return playerOptimizer;
    }

    public ProjectileOptimizer getProjectileOptimizer() {
        return projectileOptimizer;
    }

    public VehicleOptimizer getVehicleOptimizer() {
        return vehicleOptimizer;
    }

    public WaterMobOptimizer getWaterMobOptimizer() {
        return waterMobOptimizer;
    }

    public SpawnerOptimizer getSpawnerOptimizer() {
        return spawnerOptimizer;
    }

    public ItemCleanupOptimizer getItemCleanupOptimizer() {
        return itemCleanupOptimizer;
    }

    public ContainerOptimizer getContainerOptimizer() {
        return containerOptimizer;
    }

    public ExperienceOptimizer getExperienceOptimizer() {
        return experienceOptimizer;
    }

    public PhysicsOptimizer getPhysicsOptimizer() {
        return physicsOptimizer;
    }

    public TickDistributor getTickDistributor() {
        return tickDistributor;
    }

    public FarmlandOptimizer getFarmlandOptimizer() {
        return farmlandOptimizer;
    }
}
