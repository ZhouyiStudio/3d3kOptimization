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
        this.monitorInterval = config.getInt("monitoring.interval", 100);
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
}
