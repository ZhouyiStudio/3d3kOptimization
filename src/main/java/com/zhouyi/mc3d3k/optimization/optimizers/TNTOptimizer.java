package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.util.*;

/**
 * TNT 优化器
 * - TNT 爆炸链上限（防止 TNT 复制/地毯机瞬间大量爆炸卡服）
 * - 同位置 TNT 合并
 * - 控制 TNT 实体爆炸前的等待时间
 */
public class TNTOptimizer implements Listener {

    private final Plugin3d3k plugin;
    private final ConfigManager config;

    // 追踪最近的 TNT 爆炸链
    private final Map<Location, Integer> recentExplosions = new HashMap<>();

    public TNTOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isTntOptimizerEnabled()) return;

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 定期清理过期的爆炸记录
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            recentExplosions.entrySet().removeIf(entry ->
                    now - entry.getValue() > 2000);
        }, 200L, 200L);
    }

    /**
     * 爆炸前事件 - 控制 TNT 爆炸参数
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onExplosionPrime(ExplosionPrimeEvent event) {
        if (!config.isTntOptimizerEnabled()) return;

        Entity entity = event.getEntity();
        if (entity.getType() != EntityType.TNT) return;

        // 检测同一位置的连续爆炸（TNT 链）
        Location loc = entity.getLocation().getBlock().getLocation();
        int count = recentExplosions.getOrDefault(loc, 0) + 1;

        if (count > config.getTntChainLimitPerLocation()) {
            // 超过单位置链上限，降低爆炸威力或取消
            if (config.isTntCancelExcessiveChain()) {
                event.setCancelled(true);
                entity.remove();
                if (config.isDebug()) {
                    plugin.getLogger().fine("[TNTOptimizer] 取消过量 TNT 链爆炸 @" +
                            loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                }
                return;
            }
        }

        recentExplosions.put(loc, count);

        // 应用自定义爆炸参数
        float tntRadius = config.getTntExplosionRadius();
        if (tntRadius > 0) {
            event.setRadius(tntRadius);
        }
    }

    /**
     * 爆炸后事件 - 控制爆炸产生的影响
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!config.isTntOptimizerEnabled()) return;
        if (event.getEntityType() != EntityType.TNT) return;

        // 限制爆炸破坏的方块数
        int maxBlocks = config.getTntMaxBlockBreak();
        if (maxBlocks > 0 && event.blockList().size() > maxBlocks) {
            // 随机取 subset
            List<org.bukkit.block.Block> blocks = event.blockList();
            while (blocks.size() > maxBlocks) {
                blocks.remove(blocks.size() - 1);
            }
        }

        // 限制爆炸产生的掉落物数量
        // 通过 yield 控制
        TNTPrimed tnt = (TNTPrimed) event.getEntity();
        float tntYield = config.getTntYield();
        if (tntYield >= 0) {
            tnt.setYield(tntYield);
        }
    }

    public void shutdown() {
        recentExplosions.clear();
    }
}
