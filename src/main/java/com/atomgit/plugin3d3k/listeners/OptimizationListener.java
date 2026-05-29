package com.atomgit.plugin3d3k.listeners;

import com.atomgit.plugin3d3k.Plugin3d3k;
import com.atomgit.plugin3d3k.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.*;

/**
 * 综合优化监听器
 * - 处理各种事件级别的优化
 */
public class OptimizationListener implements Listener {

    private final Plugin3d3k plugin;
    private final ConfigManager config;

    // 掉落物节流 - 防止同一位置生成大量掉落物
    private final Map<String, Long> lastItemSpawnMap = new HashMap<>();

    public OptimizationListener(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    /**
     * 天气禁用
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWeatherChange(WeatherChangeEvent event) {
        if (config.isDisableWeather() && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    /**
     * 雷击禁用
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onThunderChange(ThunderChangeEvent event) {
        if (config.isDisableWeather() && event.toThunderState()) {
            event.setCancelled(true);
        }
    }

    /**
     * 闪电禁用
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        if (config.isDisableLightning()) {
            event.setCancelled(true);
        }
    }

    /**
     * 禁用盔甲架
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        if (config.isDisableArmorStands() && entity instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }

        if (config.isDisablePaintings() && entity instanceof Painting) {
            event.setCancelled(true);
        }
    }

    /**
     * 物品生成节流 - 防止掉落物刷爆
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (config.isEntityOptimizerEnabled()) {
            String locKey = getLocationKey(event.getLocation());
            long now = System.currentTimeMillis();
            Long lastTime = lastItemSpawnMap.get(locKey);

            if (lastTime != null && now - lastTime < 50) {
                // 同一位置 50ms 内生成多个掉落物，合并处理
                // 实际合并由 EntityOptimizer 的合并任务处理
            }

            lastItemSpawnMap.put(locKey, now);

            // 清理过期的记录
            if (lastItemSpawnMap.size() > 1000) {
                long threshold = now - 5000;
                lastItemSpawnMap.entrySet().removeIf(entry -> entry.getValue() < threshold);
            }
        }
    }

    /**
     * 生成一个位置键
     */
    private String getLocationKey(org.bukkit.Location loc) {
        return loc.getWorld().getName() + "@" +
                (loc.getBlockX() >> 4) + "," +
                (loc.getBlockZ() >> 4);
    }
}
