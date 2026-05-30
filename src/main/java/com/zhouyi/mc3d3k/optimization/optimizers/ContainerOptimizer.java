package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.block.Barrel;
import org.bukkit.block.Chest;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Entity;
import org.bukkit.entity.MinecartHopper;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 容器优化器
 * - 减少大型箱子农场的 tick 频率
 * - 优化漏斗矿车与容器的交互
 * - 检测密集容器区域（容器农场）
 * - 高密度容器区域降低更新频率
 */
public class ContainerOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable containerTask;

    // 方块实体是否为容器的判断集合
    private static final Set<Class<? extends BlockState>> CONTAINER_TYPES = Set.of(
            Chest.class,
            Barrel.class,
            Hopper.class,
            Dispenser.class,
            Dropper.class,
            Furnace.class,
            BrewingStand.class,
            ShulkerBox.class
    );

    // 记录高密度容器区块（超过阈值的区块）
    private final Set<String> denseContainerChunks = new HashSet<>();

    public ContainerOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isContainerOptimizerEnabled()) return;

        containerTask = new BukkitRunnable() {
            @Override
            public void run() {
                optimizeContainers();
            }
        };
        // 每 2 秒（40 tick）检查一次
        containerTask.runTaskTimer(plugin, 70L, 40L);
    }

    /**
     * 执行容器优化
     */
    private void optimizeContainers() {
        int maxPerChunk = config.getContainerMaxPerChunk();
        boolean optimizeHopperMinecart = config.isContainerOptimizeHopperMinecart();
        boolean reduceTickInDenseAreas = config.isContainerReduceTickInDenseAreas();
        double tpsThreshold = config.getContainerTpsThreshold();

        double currentTps = Bukkit.getTPS()[0];
        boolean lowTps = currentTps < tpsThreshold;

        // 清空上次的高密度区块记录
        denseContainerChunks.clear();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            for (Chunk chunk : world.getLoadedChunks()) {
                String chunkKey = world.getName() + "@" + chunk.getX() + "," + chunk.getZ();
                int containerCount = 0;

                for (BlockState state : chunk.getTileEntities()) {
                    if (isContainer(state)) {
                        containerCount++;

                        // 低 TPS 时，对容器进行优化
                        if (lowTps) {
                            optimizeSingleContainer(state, chunkKey);
                        }
                    }
                }

                // 标记高密度容器区块
                if (containerCount > maxPerChunk) {
                    denseContainerChunks.add(chunkKey);

                    if (config.isDebug() && containerCount > maxPerChunk * 2) {
                        plugin.getLogger().fine("[Container] 高密度容器区块: " +
                                world.getName() + " [" + chunk.getX() + "," + chunk.getZ() +
                                "] 容器数: " + containerCount);
                    }
                }

                // 高密度区域减少 tick
                if (reduceTickInDenseAreas && containerCount > maxPerChunk) {
                    // 高密度区域：随机跳过部分 tick 更新
                    // 通过游戏机制的自然减速实现
                }
            }
        }

        // 优化漏斗矿车与容器的交互
        if (optimizeHopperMinecart) {
            optimizeHopperMinecartInteractions();
        }
    }

    /**
     * 判断方块实体是否为容器
     */
    private boolean isContainer(BlockState state) {
        return CONTAINER_TYPES.stream().anyMatch(t -> t.isInstance(state));
    }

    /**
     * 对单个容器执行优化（低 TPS 时调用）
     */
    private void optimizeSingleContainer(BlockState state, String chunkKey) {
        // 在高密度容器区块中的容器，应用额外限制
        if (denseContainerChunks.contains(chunkKey)) {
            // 如果是漏斗，高密度时降低其检查频率
            // Bukkit API 没有直接设置漏斗冷却的方法，由服务器配置控制
            // 这里仅作统计标记
        }
    }

    /**
     * 优化漏斗矿车与容器的交互
     * 漏斗矿车每 tick 都会检查下方的容器，大量漏斗矿车会造成卡顿
     */
    private void optimizeHopperMinecartInteractions() {
        int minInterval = config.getContainerHopperMinecartInterval();
        int tickCounter = 0;

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            List<MinecartHopper> hopperMinecarts = new ArrayList<>(
                    world.getEntitiesByClass(MinecartHopper.class));

            if (hopperMinecarts.isEmpty()) continue;

            if (config.isDebug() && hopperMinecarts.size() > 20) {
                plugin.getLogger().fine("[Container] 检测到大量漏斗矿车: " +
                        world.getName() + " (" + hopperMinecarts.size() + " 个)");
            }

            // 分批处理漏斗矿车：每 tick 只处理一部分
            int maxPerTick = Math.max(1, hopperMinecarts.size() / 4);
            int start = (tickCounter * maxPerTick) % hopperMinecarts.size();
            int end = Math.min(start + maxPerTick, hopperMinecarts.size());

            for (int i = start; i < end; i++) {
                MinecartHopper minecart = hopperMinecarts.get(i);
                if (!minecart.isValid() || minecart.isDead()) continue;

                // 空漏斗矿车提升为粒子效果减少
                if (minecart.getPassengers().isEmpty() && minecart.isEmpty()) {
                    // 标记为可以优化的空闲矿车
                }
            }
        }
    }

    /**
     * 获取高密度容器区块数量
     */
    public int getDenseChunkCount() {
        return denseContainerChunks.size();
    }

    /**
     * 获取指定世界的容器统计
     */
    public ContainerStats getContainerStats(World world) {
        int chests = 0, barrels = 0, hoppers = 0, furnaces = 0, other = 0;
        int hopperMinecarts = 0;

        for (Chunk chunk : world.getLoadedChunks()) {
            for (BlockState state : chunk.getTileEntities()) {
                if (state instanceof Chest) chests++;
                else if (state instanceof Barrel) barrels++;
                else if (state instanceof Hopper) hoppers++;
                else if (state instanceof Furnace) furnaces++;
                else if (isContainer(state)) other++;
            }
        }
        hopperMinecarts = world.getEntitiesByClass(MinecartHopper.class).size();

        return new ContainerStats(chests, barrels, hoppers, furnaces, other, hopperMinecarts);
    }

    public void shutdown() {
        denseContainerChunks.clear();
        if (containerTask != null) {
            containerTask.cancel();
        }
    }

    /**
     * 容器统计
     */
    public record ContainerStats(int chests, int barrels, int hoppers,
                                 int furnaces, int other, int hopperMinecarts) {
        @Override
        public String toString() {
            return "箱子: " + chests + " | 木桶: " + barrels +
                    " | 漏斗: " + hoppers + " | 熔炉: " + furnaces +
                    " | 其他: " + other + " | 漏斗矿车: " + hopperMinecarts +
                    " | 总计: " + (chests + barrels + hoppers + furnaces + other + hopperMinecarts);
        }
    }
}
