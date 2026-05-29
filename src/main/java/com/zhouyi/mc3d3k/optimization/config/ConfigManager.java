package com.zhouyi.mc3d3k.optimization.config;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * 配置管理器 - 加载和管理插件配置
 */
public class ConfigManager {

    private final Plugin3d3k plugin;

    // 通用设置
    private boolean debug;
    private int autoSaveInterval;
    private boolean checkUpdate;

    // ─── 漏斗优化 ───
    private boolean hopperOptimizerEnabled;
    private int hopperCooldown;
    private int hopperMaxPerTick;

    // ─── TNT 优化 ───
    private boolean tntOptimizerEnabled;
    private int tntChainLimitPerLocation;
    private boolean tntCancelExcessiveChain;
    private float tntExplosionRadius;
    private int tntMaxBlockBreak;
    private float tntYield;

    // ─── AI 优化 ───
    private boolean aiOptimizerEnabled;
    private int aiFreezeRange;

    // ─── 村民优化 ───
    private boolean villagerOptimizerEnabled;
    private double villagerSkipChance;
    private int villagerFreezeRange;
    private boolean villagerLimitGossip;
    private boolean villagerDisableBreeding;
    private boolean villagerLimitAITasks;

    // ─── 碰撞优化 ───
    private boolean collisionOptimizerEnabled;
    private int collisionCheckInterval;
    private boolean collisionLimitPerChunk;
    private int collisionMaxEntitiesPerChunk;
    private boolean collisionAutoScale;
    private double collisionTpsThreshold;

    // ─── 光照优化 ───
    private boolean lightOptimizerEnabled;
    private boolean lightThrottleUpdates;
    private int lightMaxQueueSize;
    private int lightUnloadRange;

    // ─── 玩家优化 ───
    private boolean playerOptimizerEnabled;
    private boolean playerAfkEnabled;
    private int playerAfkTimeout;
    private boolean playerAfkKick;
    private boolean playerAfkWarnBeforeKick;
    private int playerAfkWarnSeconds;
    private boolean playerDynamicViewDistanceEnabled;
    private int playerDynamicViewMinDistance;
    private double playerDynamicViewTpsThreshold;
    private boolean playerActionThrottleEnabled;
    private int playerActionContainerInterval;
    private int playerActionCommandInterval;

    // 实体优化
    private boolean entityOptimizerEnabled;
    private final Map<String, Integer> activationRanges = new HashMap<>();
    private int itemMergeInterval;
    private int defaultItemDespawnTime;
    private int valuableItemDespawnTime;
    private final Set<String> valuableItems = new HashSet<>();
    private int xpMergeRadius;
    private boolean disableArmorStands;
    private boolean disablePaintings;

    // 红石优化
    private boolean redstoneOptimizerEnabled;
    private boolean limitHighFrequency;
    private int highFrequencyThreshold;
    private int highFrequencyPenalty;
    private boolean disablePistons;

    // 区块优化
    private boolean chunkOptimizerEnabled;
    private int viewDistance;
    private int entityViewDistance;
    private int unloadDelay;
    private int maxUnloadsPerTick;

    // 生物限制
    private boolean mobLimiterEnabled;
    private int maxMobsPerChunk;
    private final Map<String, Integer> perTypeLimits = new HashMap<>();
    private int globalMonstersLimit;
    private int globalAnimalsLimit;
    private int globalWaterLimit;
    private int globalFlyingLimit;
    private int spawnSuppressThreshold;

    // 性能监控
    private boolean monitoringEnabled;
    private int monitorInterval;
    private double tpsAlertThreshold;
    private boolean printToConsole;
    private String broadcastPermission;

    // ─── 异常检测 ───
    private boolean detectionEnabled;
    private int detectionCheckInterval;
    private boolean itemPileDetectionEnabled;
    private int itemPileMaxPerChunk;
    private int itemPileAlertCooldown;
    private boolean entityDensityDetectionEnabled;
    private int entityDensityThreshold;
    private int entityDensityAlertCooldown;
    private boolean chunkLoadDetectionEnabled;
    private int chunkLoadEntityThreshold;
    private int chunkLoadRedstoneThreshold;
    private boolean spawnStormDetectionEnabled;
    private int spawnStormMaxRate;
    private int spawnStormSampleWindow;
    private boolean playerBehaviorDetectionEnabled;
    private int playerBehaviorMaxCommandsPerSecond;
    private boolean playerBehaviorDetectHighFrequencyInteraction;

    // 杂项
    private boolean disableVillagerTradeGlitch;
    private boolean disableVineGrowth;
    private boolean disableLeafDecay;
    private boolean disableWeather;
    private boolean disableLightning;
    private boolean mergeXp;

    public ConfigManager(Plugin3d3k plugin) {
        this.plugin = plugin;
        loadConfig(plugin.getConfig());
    }

    public void loadConfig(FileConfiguration config) {
        // 通用设置
        this.debug = config.getBoolean("settings.debug", false);
        this.autoSaveInterval = config.getInt("settings.auto-save-interval", 10);
        this.checkUpdate = config.getBoolean("settings.check-update", true);

        // 实体优化
        this.entityOptimizerEnabled = config.getBoolean("entity.enabled", true);
        ConfigurationSection activationSection = config.getConfigurationSection("entity.activation-range");
        if (activationSection != null) {
            for (String key : activationSection.getKeys(false)) {
                activationRanges.put(key.toUpperCase(), activationSection.getInt(key));
            }
        }
        this.itemMergeInterval = config.getInt("entity.item-merge-interval", 20);
        this.defaultItemDespawnTime = config.getInt("entity.item-despawn-time.default", 300);
        this.valuableItemDespawnTime = config.getInt("entity.item-despawn-time.valuable", 600);
        this.valuableItems.clear();
        this.valuableItems.addAll(config.getStringList("entity.item-despawn-time.valuable-items"));
        this.xpMergeRadius = config.getInt("entity.xp-merge-radius", 8);
        this.disableArmorStands = config.getBoolean("entity.disable-armor-stands", false);
        this.disablePaintings = config.getBoolean("entity.disable-paintings", false);

        // 红石优化
        this.redstoneOptimizerEnabled = config.getBoolean("redstone.enabled", true);
        this.limitHighFrequency = config.getBoolean("redstone.limit-high-frequency", true);
        this.highFrequencyThreshold = config.getInt("redstone.high-frequency-threshold", 15);
        this.highFrequencyPenalty = config.getInt("redstone.high-frequency-penalty", 4);
        this.disablePistons = config.getBoolean("redstone.disable-pistons", false);

        // 区块优化
        this.chunkOptimizerEnabled = config.getBoolean("chunk.enabled", true);
        this.viewDistance = config.getInt("chunk.view-distance", 8);
        this.entityViewDistance = config.getInt("chunk.entity-view-distance", 4);
        this.unloadDelay = config.getInt("chunk.unload-delay", 15);
        this.maxUnloadsPerTick = config.getInt("chunk.max-unloads-per-tick", 20);

        // 生物限制
        this.mobLimiterEnabled = config.getBoolean("mob-limiter.enabled", true);
        this.maxMobsPerChunk = config.getInt("mob-limiter.max-mobs-per-chunk", 25);
        ConfigurationSection perTypeSection = config.getConfigurationSection("mob-limiter.per-type");
        if (perTypeSection != null) {
            for (String key : perTypeSection.getKeys(false)) {
                perTypeLimits.put(key.toUpperCase(), perTypeSection.getInt(key));
            }
        }
        ConfigurationSection globalSection = config.getConfigurationSection("mob-limiter.global");
        if (globalSection != null) {
            this.globalMonstersLimit = globalSection.getInt("monsters", 200);
            this.globalAnimalsLimit = globalSection.getInt("animals", 100);
            this.globalWaterLimit = globalSection.getInt("water", 40);
            this.globalFlyingLimit = globalSection.getInt("flying", 60);
        }
        this.spawnSuppressThreshold = config.getInt("mob-limiter.spawn-suppress-threshold", 40);

        // 性能监控
        this.monitoringEnabled = config.getBoolean("monitoring.enabled", true);
        this.monitorInterval = config.getInt("monitoring.interval", 12000);
        this.tpsAlertThreshold = config.getDouble("monitoring.tps-alert-threshold", 16.0);
        this.printToConsole = config.getBoolean("monitoring.print-to-console", true);
        this.broadcastPermission = config.getString("monitoring.broadcast-permission", "3d3k.status");

        // 杂项
        this.disableVillagerTradeGlitch = config.getBoolean("misc.disable-villager-trade-glitch", true);
        this.disableVineGrowth = config.getBoolean("misc.disable-vine-growth", false);
        this.disableLeafDecay = config.getBoolean("misc.disable-leaf-decay", false);
        this.disableWeather = config.getBoolean("misc.disable-weather", false);
        this.disableLightning = config.getBoolean("misc.disable-lightning", false);
        this.mergeXp = config.getBoolean("misc.merge-xp", true);

        // 漏斗优化
        this.hopperOptimizerEnabled = config.getBoolean("hopper.enabled", true);
        this.hopperCooldown = config.getInt("hopper.cooldown", 2);
        this.hopperMaxPerTick = config.getInt("hopper.max-per-tick", 20);

        // TNT 优化
        this.tntOptimizerEnabled = config.getBoolean("tnt.enabled", true);
        this.tntChainLimitPerLocation = config.getInt("tnt.chain-limit-per-location", 3);
        this.tntCancelExcessiveChain = config.getBoolean("tnt.cancel-excessive-chain", true);
        this.tntExplosionRadius = (float) config.getDouble("tnt.explosion-radius", 0.0);
        this.tntMaxBlockBreak = config.getInt("tnt.max-block-break", 0);
        this.tntYield = (float) config.getDouble("tnt.yield", -1.0);

        // AI 优化
        this.aiOptimizerEnabled = config.getBoolean("ai.enabled", true);
        this.aiFreezeRange = config.getInt("ai.freeze-range", 48);

        // 村民优化
        this.villagerOptimizerEnabled = config.getBoolean("villager.enabled", true);
        this.villagerSkipChance = config.getDouble("villager.gossip-skip-chance", 0.3);
        this.villagerFreezeRange = config.getInt("villager.freeze-range", 32);
        this.villagerLimitGossip = config.getBoolean("villager.limit-gossip", true);
        this.villagerDisableBreeding = config.getBoolean("villager.disable-breeding", false);
        this.villagerLimitAITasks = config.getBoolean("villager.limit-ai-tasks", true);

        // 碰撞优化
        this.collisionOptimizerEnabled = config.getBoolean("collision.enabled", true);
        this.collisionCheckInterval = config.getInt("collision.check-interval", 5);
        this.collisionLimitPerChunk = config.getBoolean("collision.limit-per-chunk", true);
        this.collisionMaxEntitiesPerChunk = config.getInt("collision.max-entities-per-chunk", 24);
        this.collisionAutoScale = config.getBoolean("collision.auto-scale", true);
        this.collisionTpsThreshold = config.getDouble("collision.tps-threshold", 17.0);

        // 光照优化
        this.lightOptimizerEnabled = config.getBoolean("light.enabled", true);
        this.lightThrottleUpdates = config.getBoolean("light.throttle-updates", true);
        this.lightMaxQueueSize = config.getInt("light.max-queue-size", 500);
        this.lightUnloadRange = config.getInt("light.unload-range", 12);

        // 玩家优化
        this.playerOptimizerEnabled = config.getBoolean("player.enabled", true);
        this.playerAfkEnabled = config.getBoolean("player.auto-afk.enabled", true);
        this.playerAfkTimeout = config.getInt("player.auto-afk.timeout", 300);
        this.playerAfkKick = config.getBoolean("player.auto-afk.kick", true);
        this.playerAfkWarnBeforeKick = config.getBoolean("player.auto-afk.warn-before-kick", true);
        this.playerAfkWarnSeconds = config.getInt("player.auto-afk.warn-seconds", 30);
        this.playerDynamicViewDistanceEnabled = config.getBoolean("player.dynamic-view-distance.enabled", true);
        this.playerDynamicViewMinDistance = config.getInt("player.dynamic-view-distance.min-view-distance", 4);
        this.playerDynamicViewTpsThreshold = config.getDouble("player.dynamic-view-distance.tps-threshold", 17.0);
        this.playerActionThrottleEnabled = config.getBoolean("player.action-throttle.enabled", true);
        this.playerActionContainerInterval = config.getInt("player.action-throttle.container-open-interval", 10);
        this.playerActionCommandInterval = config.getInt("player.action-throttle.command-interval", 2);

        // ─── 异常检测 ───
        this.detectionEnabled = config.getBoolean("detection.enabled", true);
        this.detectionCheckInterval = config.getInt("detection.check-interval", 100);
        this.itemPileDetectionEnabled = config.getBoolean("detection.item-pile.enabled", true);
        this.itemPileMaxPerChunk = config.getInt("detection.item-pile.max-per-chunk", 100);
        this.itemPileAlertCooldown = config.getInt("detection.item-pile.alert-cooldown", 60);
        this.entityDensityDetectionEnabled = config.getBoolean("detection.entity-density.enabled", true);
        this.entityDensityThreshold = config.getInt("detection.entity-density.dense-threshold", 35);
        this.entityDensityAlertCooldown = config.getInt("detection.entity-density.alert-cooldown", 120);
        this.chunkLoadDetectionEnabled = config.getBoolean("detection.chunk-load.enabled", true);
        this.chunkLoadEntityThreshold = config.getInt("detection.chunk-load.entity-threshold", 50);
        this.chunkLoadRedstoneThreshold = config.getInt("detection.chunk-load.redstone-threshold", 80);
        this.spawnStormDetectionEnabled = config.getBoolean("detection.spawn-storm.enabled", true);
        this.spawnStormMaxRate = config.getInt("detection.spawn-storm.max-rate", 40);
        this.spawnStormSampleWindow = config.getInt("detection.spawn-storm.sample-window", 100);
        this.playerBehaviorDetectionEnabled = config.getBoolean("detection.player-behavior.enabled", true);
        this.playerBehaviorMaxCommandsPerSecond = config.getInt("detection.player-behavior.max-commands-per-second", 10);
        this.playerBehaviorDetectHighFrequencyInteraction = config.getBoolean("detection.player-behavior.detect-high-frequency-interaction", true);
    }

    // ─── Getter 方法 ───

    public boolean isDebug() {
        return debug;
    }

    public boolean isEntityOptimizerEnabled() {
        return entityOptimizerEnabled;
    }

    public int getActivationRange(String type) {
        return activationRanges.getOrDefault(type.toUpperCase(), 32);
    }

    public int getItemMergeInterval() {
        return itemMergeInterval;
    }

    public int getDefaultItemDespawnTime() {
        return defaultItemDespawnTime;
    }

    public int getValuableItemDespawnTime() {
        return valuableItemDespawnTime;
    }

    public boolean isValuableItem(String materialName) {
        return valuableItems.contains(materialName);
    }

    public int getXpMergeRadius() {
        return xpMergeRadius;
    }

    public boolean isDisableArmorStands() {
        return disableArmorStands;
    }

    public boolean isDisablePaintings() {
        return disablePaintings;
    }

    public boolean isRedstoneOptimizerEnabled() {
        return redstoneOptimizerEnabled;
    }

    public boolean isLimitHighFrequency() {
        return limitHighFrequency;
    }

    public int getHighFrequencyThreshold() {
        return highFrequencyThreshold;
    }

    public int getHighFrequencyPenalty() {
        return highFrequencyPenalty;
    }

    public boolean isDisablePistons() {
        return disablePistons;
    }

    public boolean isChunkOptimizerEnabled() {
        return chunkOptimizerEnabled;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public int getEntityViewDistance() {
        return entityViewDistance;
    }

    public int getUnloadDelay() {
        return unloadDelay;
    }

    public int getMaxUnloadsPerTick() {
        return maxUnloadsPerTick;
    }

    public boolean isMobLimiterEnabled() {
        return mobLimiterEnabled;
    }

    public int getMaxMobsPerChunk() {
        return maxMobsPerChunk;
    }

    public int getPerTypeLimit(String type) {
        return perTypeLimits.getOrDefault(type.toUpperCase(), -1);
    }

    public int getGlobalMonstersLimit() {
        return globalMonstersLimit;
    }

    public int getGlobalAnimalsLimit() {
        return globalAnimalsLimit;
    }

    public int getGlobalWaterLimit() {
        return globalWaterLimit;
    }

    public int getGlobalFlyingLimit() {
        return globalFlyingLimit;
    }

    public int getSpawnSuppressThreshold() {
        return spawnSuppressThreshold;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public int getMonitorInterval() {
        return monitorInterval;
    }

    public double getTpsAlertThreshold() {
        return tpsAlertThreshold;
    }

    public boolean isPrintToConsole() {
        return printToConsole;
    }

    public String getBroadcastPermission() {
        return broadcastPermission;
    }

    public boolean isDisableVillagerTradeGlitch() {
        return disableVillagerTradeGlitch;
    }

    public boolean isDisableVineGrowth() {
        return disableVineGrowth;
    }

    public boolean isDisableLeafDecay() {
        return disableLeafDecay;
    }

    public boolean isDisableWeather() {
        return disableWeather;
    }

    public boolean isDisableLightning() {
        return disableLightning;
    }

    public boolean isMergeXp() {
        return mergeXp;
    }

    // ─── 漏斗优化 Getter ───

    public boolean isHopperOptimizerEnabled() {
        return hopperOptimizerEnabled;
    }

    public int getHopperCooldown() {
        return hopperCooldown;
    }

    public int getHopperMaxPerTick() {
        return hopperMaxPerTick;
    }

    // ─── TNT 优化 Getter ───

    public boolean isTntOptimizerEnabled() {
        return tntOptimizerEnabled;
    }

    public int getTntChainLimitPerLocation() {
        return tntChainLimitPerLocation;
    }

    public boolean isTntCancelExcessiveChain() {
        return tntCancelExcessiveChain;
    }

    public float getTntExplosionRadius() {
        return tntExplosionRadius;
    }

    public int getTntMaxBlockBreak() {
        return tntMaxBlockBreak;
    }

    public float getTntYield() {
        return tntYield;
    }

    // ─── AI 优化 Getter ───

    public boolean isAiOptimizerEnabled() {
        return aiOptimizerEnabled;
    }

    public int getAiFreezeRange() {
        return aiFreezeRange;
    }

    // ─── 村民优化 Getter ───

    public boolean isVillagerOptimizerEnabled() {
        return villagerOptimizerEnabled;
    }

    public boolean isVillagerLimitGossip() {
        return villagerLimitGossip;
    }

    public boolean isVillagerDisableBreeding() {
        return villagerDisableBreeding;
    }

    public boolean isVillagerLimitAITasks() {
        return villagerLimitAITasks;
    }

    public double getVillagerGossipSkipChance() {
        return villagerSkipChance;
    }

    public int getVillagerFreezeRange() {
        return villagerFreezeRange;
    }

    // ─── 碰撞优化 Getter ───

    public boolean isCollisionOptimizerEnabled() {
        return collisionOptimizerEnabled;
    }

    public int getCollisionCheckInterval() {
        return collisionCheckInterval;
    }

    public boolean isCollisionLimitPerChunk() {
        return collisionLimitPerChunk;
    }

    public int getCollisionMaxEntitiesPerChunk() {
        return collisionMaxEntitiesPerChunk;
    }

    public boolean isCollisionAutoScale() {
        return collisionAutoScale;
    }

    public double getCollisionTpsThreshold() {
        return collisionTpsThreshold;
    }

    // ─── 光照优化 Getter ───

    public boolean isLightOptimizerEnabled() {
        return lightOptimizerEnabled;
    }

    public boolean isLightThrottleUpdates() {
        return lightThrottleUpdates;
    }

    public boolean isLightUnloadFarChunks() {
        return lightUnloadRange > 0;
    }

    public int getLightMaxQueueSize() {
        return lightMaxQueueSize;
    }

    public int getLightUnloadRange() {
        return lightUnloadRange;
    }

    // ─── 玩家优化 Getter ───

    public boolean isPlayerOptimizerEnabled() {
        return playerOptimizerEnabled;
    }

    public boolean isPlayerAfkEnabled() {
        return playerAfkEnabled;
    }

    public int getPlayerAfkTimeout() {
        return playerAfkTimeout;
    }

    public boolean isPlayerAfkKick() {
        return playerAfkKick;
    }

    public boolean isPlayerAfkWarnBeforeKick() {
        return playerAfkWarnBeforeKick;
    }

    public int getPlayerAfkWarnSeconds() {
        return playerAfkWarnSeconds;
    }

    public boolean isPlayerDynamicViewDistanceEnabled() {
        return playerDynamicViewDistanceEnabled;
    }

    public int getPlayerDynamicViewMinDistance() {
        return playerDynamicViewMinDistance;
    }

    public double getPlayerDynamicViewTpsThreshold() {
        return playerDynamicViewTpsThreshold;
    }

    public boolean isPlayerActionThrottleEnabled() {
        return playerActionThrottleEnabled;
    }

    public int getPlayerActionContainerInterval() {
        return playerActionContainerInterval;
    }

    public int getPlayerActionCommandInterval() {
        return playerActionCommandInterval;
    }

    // ─── 异常检测 Getter ───

    public boolean isDetectionEnabled() {
        return detectionEnabled;
    }

    public int getDetectionCheckInterval() {
        return detectionCheckInterval;
    }

    public boolean isItemPileDetectionEnabled() {
        return itemPileDetectionEnabled;
    }

    public int getItemPileMaxPerChunk() {
        return itemPileMaxPerChunk;
    }

    public int getItemPileAlertCooldown() {
        return itemPileAlertCooldown;
    }

    public boolean isEntityDensityDetectionEnabled() {
        return entityDensityDetectionEnabled;
    }

    public int getEntityDensityThreshold() {
        return entityDensityThreshold;
    }

    public int getEntityDensityAlertCooldown() {
        return entityDensityAlertCooldown;
    }

    public boolean isChunkLoadDetectionEnabled() {
        return chunkLoadDetectionEnabled;
    }

    public int getChunkLoadEntityThreshold() {
        return chunkLoadEntityThreshold;
    }

    public int getChunkLoadRedstoneThreshold() {
        return chunkLoadRedstoneThreshold;
    }

    public boolean isSpawnStormDetectionEnabled() {
        return spawnStormDetectionEnabled;
    }

    public int getSpawnStormMaxRate() {
        return spawnStormMaxRate;
    }

    public int getSpawnStormSampleWindow() {
        return spawnStormSampleWindow;
    }

    public boolean isPlayerBehaviorDetectionEnabled() {
        return playerBehaviorDetectionEnabled;
    }

    public int getPlayerBehaviorMaxCommandsPerSecond() {
        return playerBehaviorMaxCommandsPerSecond;
    }

    public boolean isPlayerBehaviorDetectHighFrequencyInteraction() {
        return playerBehaviorDetectHighFrequencyInteraction;
    }
}
