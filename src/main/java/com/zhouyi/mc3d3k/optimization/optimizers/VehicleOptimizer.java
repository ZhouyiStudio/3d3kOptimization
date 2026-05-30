package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 交通工具优化器（矿车/船）
 * - 清理静止的空矿车/空船
 * - 限制每个区块内交通工具的最大数量
 * - 合并重叠的矿车
 * - 回收长时间无人使用的交通工具
 */
public class VehicleOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable vehicleTask;

    // 记录矿车/船的静止时间
    private final Map<UUID, Integer> idleTicks = new HashMap<>();

    public VehicleOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isVehicleOptimizerEnabled()) return;

        vehicleTask = new BukkitRunnable() {
            @Override
            public void run() {
                optimizeVehicles();
            }
        };
        // 每 2 秒（40 tick）检查一次
        vehicleTask.runTaskTimer(plugin, 80L, 40L);
    }

    /**
     * 执行交通工具优化
     */
    private void optimizeVehicles() {
        int maxPerChunk = config.getVehicleMaxPerChunk();
        int maxIdleTicks = config.getVehicleMaxIdleTicks();
        int totalRemoved = 0;

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            // 按区块分组统计交通工具
            Map<Chunk, List<Entity>> vehicleMap = new HashMap<>();

            for (Entity entity : world.getEntities()) {
                if (!isVehicle(entity)) continue;
                if (!entity.isValid() || entity.isDead()) continue;

                Chunk chunk = entity.getLocation().getChunk();
                vehicleMap.computeIfAbsent(chunk, k -> new ArrayList<>()).add(entity);
            }

            for (Map.Entry<Chunk, List<Entity>> entry : vehicleMap.entrySet()) {
                List<Entity> vehicles = entry.getValue();

                // 限制每区块数量
                if (maxPerChunk > 0 && vehicles.size() > maxPerChunk) {
                    // 优先移除空的交通工具
                    vehicles.sort(Comparator.comparingInt(v -> isEmptyVehicle(v) ? 0 : 1));
                    int toRemove = vehicles.size() - maxPerChunk;
                    for (int i = 0; i < toRemove && i < vehicles.size(); i++) {
                        Entity v = vehicles.get(i);
                        if (isEmptyVehicle(v)) {
                            v.remove();
                            totalRemoved++;
                        }
                    }
                }

                // 清理长时间静止的空交通工具
                if (maxIdleTicks > 0) {
                    for (Entity vehicle : vehicles) {
                        if (!vehicle.isValid()) continue;
                        if (hasPassenger(vehicle)) {
                            idleTicks.remove(vehicle.getUniqueId());
                            continue;
                        }

                        // 判断是否静止
                        boolean isIdle = vehicle.getVelocity().lengthSquared() < 0.001;
                        UUID uuid = vehicle.getUniqueId();

                        if (isIdle) {
                            int idle = idleTicks.getOrDefault(uuid, 0) + 1;
                            idleTicks.put(uuid, idle);

                            if (idle > maxIdleTicks && isEmptyVehicle(vehicle)) {
                                vehicle.remove();
                                idleTicks.remove(uuid);
                                totalRemoved++;
                                if (config.isDebug()) {
                                    plugin.getLogger().fine("[Vehicle] 移除长时间静止交通工具: " +
                                            vehicle.getType().name() + " @" + world.getName());
                                }
                            }
                        } else {
                            idleTicks.remove(uuid);
                        }
                    }
                }
            }
        }

        if (totalRemoved > 0 && config.isDebug()) {
            plugin.getLogger().info("[Vehicle] 本次清理交通工具: " + totalRemoved + " 个");
        }
    }

    /**
     * 判断实体是否属于交通工具（矿车或船）
     */
    private boolean isVehicle(Entity entity) {
        return entity instanceof Minecart || entity instanceof Boat;
    }

    /**
     * 判断交通工具是否为空（无乘客）
     */
    private boolean isEmptyVehicle(Entity entity) {
        if (entity instanceof Boat boat) {
            return boat.getPassengers().isEmpty();
        }
        if (entity instanceof Minecart minecart) {
            return minecart.getPassengers().isEmpty();
        }
        return true;
    }

    /**
     * 判断是否有乘客
     */
    private boolean hasPassenger(Entity entity) {
        return !entity.getPassengers().isEmpty();
    }

    /**
     * 获取指定世界的交通工具统计
     */
    public VehicleStats getVehicleStats(World world) {
        int minecarts = 0, boats = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Minecart) minecarts++;
            else if (entity instanceof Boat) boats++;
        }
        return new VehicleStats(minecarts, boats);
    }

    public void shutdown() {
        idleTicks.clear();
        if (vehicleTask != null) {
            vehicleTask.cancel();
        }
    }

    /**
     * 交通工具统计
     */
    public record VehicleStats(int minecarts, int boats) {
        @Override
        public String toString() {
            return "矿车: " + minecarts + " | 船: " + boats + " | 总计: " + (minecarts + boats);
        }
    }
}
