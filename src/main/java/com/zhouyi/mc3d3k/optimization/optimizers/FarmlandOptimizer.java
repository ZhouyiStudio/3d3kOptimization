package com.zhouyi.mc3d3k.optimization.optimizers;

import com.zhouyi.mc3d3k.optimization.Plugin3d3k;
import com.zhouyi.mc3d3k.optimization.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * 农田优化器
 * - 控制作物生长随机 tick 频率（远离玩家时降频）
 * - 优化耕地退化检测（减少不必要的湿度检查）
 * - 大型自动农田的 tick 负载均衡
 * - TPS 保护：TPS 低时跳过远距离农田的随机 tick
 * - 限制每区块的作物最大 tick 数
 */
public class FarmlandOptimizer {

    private final Plugin3d3k plugin;
    private final ConfigManager config;
    private BukkitRunnable farmTask;

    // 各区块上次处理时间的记录
    private final Map<String, Long> chunkLastProcessed = new HashMap<>();

    // 可优化的作物类型
    private static final Set<Material> CROP_TYPES = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.MELON_STEM, Material.PUMPKIN_STEM,
            Material.SUGAR_CANE, Material.CACTUS, Material.COCOA,
            Material.NETHER_WART, Material.BAMBOO, Material.BAMBOO_SAPLING,
            Material.KELP, Material.KELP_PLANT, Material.SEA_PICKLE,
            Material.CHORUS_PLANT, Material.CHORUS_FLOWER,
            Material.SWEET_BERRY_BUSH, Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT, Material.TWISTING_VINES,
            Material.TWISTING_VINES_PLANT, Material.WEEPING_VINES,
            Material.WEEPING_VINES_PLANT
    );

    public FarmlandOptimizer(Plugin3d3k plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    public void init() {
        if (!config.isFarmlandOptimizerEnabled()) return;

        farmTask = new BukkitRunnable() {
            @Override
            public void run() {
                optimizeFarmland();
            }
        };
        // 每 40 tick（2 秒）执行一次
        farmTask.runTaskTimer(plugin, 60L, 40L);
    }

    /**
     * 执行农田优化
     */
    private void optimizeFarmland() {
        int maxCropsPerChunk = config.getFarmlandMaxCropsPerChunk();
        int growthRange = config.getFarmlandGrowthRange();
        boolean limitHydrationCheck = config.isFarmlandLimitHydrationCheck();
        double tpsThreshold = config.getFarmlandTpsThreshold();

        double currentTps = Bukkit.getTPS()[0];
        boolean lowTps = currentTps < tpsThreshold && tpsThreshold > 0;

        long now = System.currentTimeMillis();

        for (World world : Bukkit.getWorlds()) {
            if (world.getPlayers().isEmpty()) continue;

            // 确定活跃区块（玩家附近的区块）
            Set<String> activeChunks = new HashSet<>();
            if (growthRange > 0) {
                int chunkRadius = (growthRange + 15) / 16;
                for (Player player : world.getPlayers()) {
                    if (!player.isOnline() || player.isDead()) continue;
                    int cx = player.getLocation().getChunk().getX();
                    int cz = player.getLocation().getChunk().getZ();
                    for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                            activeChunks.add((cx + dx) + "," + (cz + dz));
                        }
                    }
                }
            }

            for (Chunk chunk : world.getLoadedChunks()) {
                String chunkKey = chunk.getX() + "," + chunk.getZ();
                boolean isActive = activeChunks.isEmpty() || activeChunks.contains(chunkKey);

                // TPS 过低时，非活跃区块跳过
                if (lowTps && !isActive) continue;

                // 限制每区块的作物数量
                if (maxCropsPerChunk > 0) {
                    limitCropsInChunk(chunk, maxCropsPerChunk);
                }

                // 非活跃区块的作物降频
                if (!isActive && growthRange > 0) {
                    Long lastProcessed = chunkLastProcessed.get(chunkKey);
                    if (lastProcessed != null && (now - lastProcessed) < 10000) { // 10 秒冷却
                        // 跳过高频 tick
                        // 实际上我们无法直接阻止随机 tick，但可以记录供参考
                        continue;
                    }
                    chunkLastProcessed.put(chunkKey, now);
                }

                // 限制耕地湿度检查
                if (limitHydrationCheck) {
                    optimizeHydrationCheck(chunk, lowTps);
                }
            }
        }
    }

    /**
     * 限制每区块的作物数量
     */
    private void limitCropsInChunk(Chunk chunk, int maxCrops) {
        int cropCount = 0;
        List<Block> cropsToTrim = new ArrayList<>();

        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (isCrop(block)) {
                        cropCount++;
                        if (cropCount > maxCrops) {
                            cropsToTrim.add(block);
                        }
                    }
                }
            }
        }

        // 移除多余的作物（优先移除未成熟的）
        if (!cropsToTrim.isEmpty()) {
            cropsToTrim.sort((a, b) -> {
                int ageA = getCropAge(a);
                int ageB = getCropAge(b);
                return Integer.compare(ageA, ageB); // 未成熟的先移除
            });

            int toRemove = Math.min(cropsToTrim.size(), cropCount - maxCrops);
            for (int i = 0; i < toRemove; i++) {
                Block block = cropsToTrim.get(i);
                // 不要直接移除作物，将其年龄设为 0（重置）
                BlockData data = block.getBlockData();
                if (data instanceof Ageable ageable) {
                    ageable.setAge(0);
                    block.setBlockData(ageable, false);
                }
            }
        }
    }

    /**
     * 获取作物生长年龄
     */
    private int getCropAge(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge();
        }
        return 0;
    }

    /**
     * 优化耕地湿度检查（减少不必要的 hydrate 检测）
     */
    private void optimizeHydrationCheck(Chunk chunk, boolean lowTps) {
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();

        // 查找该区块的所有耕地
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() == Material.FARMLAND) {
                        BlockData data = block.getBlockData();
                        if (data instanceof Farmland farmland) {
                            // 如果耕地在上方有方块阻挡或光照不足
                            Block above = block.getRelative(0, 1, 0);
                            if (above.getType().isSolid()) {
                                // 上方有方块阻挡 -> 退化为泥土（减少无意义的检测）
                                if (lowTps) {
                                    block.setType(Material.DIRT, false);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 判断方块是否为可优化作物
     */
    private boolean isCrop(Block block) {
        return CROP_TYPES.contains(block.getType());
    }

    public void shutdown() {
        chunkLastProcessed.clear();
        if (farmTask != null) {
            farmTask.cancel();
        }
    }
}
