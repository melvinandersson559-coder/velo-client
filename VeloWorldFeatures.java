package com.velo.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.DrawStyle;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

import java.util.*;

public class VeloWorldFeatures {

    // --- BLOCK ESP (Alla Minecraft-block via VeloBlockPickerScreen) ---
    public static boolean blockEspEnabled = false;
    public static int blockEspRange = 48;
    public static final Set<Block> selectedEspBlocks = new LinkedHashSet<>(List.of(
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS
    ));

    // --- BASE FINDER ---
    public static boolean baseFinderEnabled = false;
    public static int baseChestThreshold = 4;
    public static int redstoneFarmThreshold = 20;

    // --- SPAWNER FINDER ---
    public static boolean spawnerFinderEnabled = false;
    public static int selectedSpawnerTypeIndex = 0;
    public static final String[] SPAWNER_TYPES = new String[]{
            "ALL", "skeleton", "zombie", "spider", "cave_spider", "blaze", "magma_cube", "silverfish"
    };
    public static final String[] SPAWNER_LABELS = new String[]{
            "Alla Spawners", "Skeleton", "Zombie", "Spider", "Cave Spider", "Blaze", "Magma Cube", "Silverfish"
    };

    // Cachade resultat för glowing 3D-outlines & anti-spam
    private static final List<BlockPos> espFoundBlocks = new ArrayList<>();
    private static final List<BlockPos> baseFoundBlocks = new ArrayList<>();
    private static final List<BlockPos> redstoneFarmBlocks = new ArrayList<>();
    private static final List<BlockPos> spawnerFoundBlocks = new ArrayList<>();

    private static final Set<Long> alertedBaseChunks = new HashSet<>();
    private static final Set<Long> alertedRedstoneChunks = new HashSet<>();
    private static final Set<Long> alertedSpawners = new HashSet<>();

    public static String lastAlertMessage = "";
    public static long lastAlertTime = 0;

    private static int tickCounter = 0;

    public static String getSelectedSpawnerLabel() {
        return SPAWNER_LABELS[selectedSpawnerTypeIndex % SPAWNER_LABELS.length];
    }

    public static void clearAlerts() {
        alertedBaseChunks.clear();
        alertedRedstoneChunks.clear();
        alertedSpawners.clear();
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            espFoundBlocks.clear();
            baseFoundBlocks.clear();
            redstoneFarmBlocks.clear();
            spawnerFoundBlocks.clear();
            return;
        }

        tickCounter++;
        if (tickCounter % 20 != 0) return;

        int chunkRadius = Math.min(mc.options.getClampedViewDistance(), 8);
        ChunkPos playerChunk = mc.player.getChunkPos();

        // 1. BASE FINDER & SPAWNER FINDER (laddade chunks)
        if (baseFinderEnabled || spawnerFinderEnabled) {
            baseFoundBlocks.clear();
            redstoneFarmBlocks.clear();
            spawnerFoundBlocks.clear();

            for (int cx = playerChunk.x - chunkRadius; cx <= playerChunk.x + chunkRadius; cx++) {
                for (int cz = playerChunk.z - chunkRadius; cz <= playerChunk.z + chunkRadius; cz++) {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;

                    long chunkKey = ChunkPos.toLong(cx, cz);
                    int storageCount = 0;
                    int redstoneBeCount = 0;
                    BlockPos sampleBasePos = null;
                    List<BlockPos> chunkRedstonePositions = new ArrayList<>();

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        Block block = chunk.getBlockState(pos).getBlock();

                        if (baseFinderEnabled) {
                            if (block instanceof ChestBlock || block instanceof TrappedChestBlock
                                    || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock
                                    || block instanceof BarrelBlock) {
                                storageCount++;
                                if (sampleBasePos == null) sampleBasePos = pos;
                                baseFoundBlocks.add(pos);
                            } else if (block instanceof HopperBlock || block instanceof DispenserBlock
                                    || block instanceof DropperBlock || block instanceof ComparatorBlock) {
                                redstoneBeCount++;
                                chunkRedstonePositions.add(pos);
                            }
                        }

                        if (spawnerFinderEnabled && be instanceof MobSpawnerBlockEntity spawnerBe) {
                            String mobId = getSpawnerMobId(mc, spawnerBe);
                            String wanted = SPAWNER_TYPES[selectedSpawnerTypeIndex % SPAWNER_TYPES.length];
                            if (wanted.equals("ALL") || mobId.equalsIgnoreCase(wanted)) {
                                spawnerFoundBlocks.add(pos);
                                long posKey = pos.asLong();
                                if (alertedSpawners.add(posKey)) {
                                    sendAlert(mc, "§d[Spawner Finder] §fHittade §b" + mobId.toUpperCase(Locale.ROOT)
                                            + " §fspawner vid §b" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
                                }
                            }
                        }
                    }

                    if (baseFinderEnabled && storageCount >= baseChestThreshold && sampleBasePos != null) {
                        if (alertedBaseChunks.add(chunkKey)) {
                            sendAlert(mc, "§b[Base Finder] §fMöjlig §bBAS §f(" + storageCount
                                    + " kistor/shulkers) vid §b" + sampleBasePos.getX() + ", " + sampleBasePos.getY() + ", " + sampleBasePos.getZ());
                        }
                    }

                    if (baseFinderEnabled) {
                        int redstoneCount = redstoneBeCount + collectRedstoneInChunk(chunk, chunkRedstonePositions);
                        if (redstoneCount >= redstoneFarmThreshold) {
                            int cap = Math.min(chunkRedstonePositions.size(), 40);
                            for (int i = 0; i < cap; i++) {
                                redstoneFarmBlocks.add(chunkRedstonePositions.get(i));
                            }
                            if (alertedRedstoneChunks.add(chunkKey)) {
                                int wx = (cx << 4) + 8;
                                int wz = (cz << 4) + 8;
                                sendAlert(mc, "§c[Base Finder] §fMöjlig §cREDSTONE FARM §f(" + redstoneCount
                                        + " redstone-block) i chunk vid §bX:" + wx + " Z:" + wz);
                            }
                        }
                    }
                }
            }
        } else {
            baseFoundBlocks.clear();
            redstoneFarmBlocks.clear();
            spawnerFoundBlocks.clear();
        }

        // 2. BLOCK ESP (skannar alla valda block från VeloBlockPickerScreen)
        espFoundBlocks.clear();
        if (blockEspEnabled && !selectedEspBlocks.isEmpty()) {
            BlockPos pPos = mc.player.getBlockPos();
            int r = blockEspRange;
            int minY = Math.max(mc.world.getBottomY(), pPos.getY() - 40);
            int maxY = Math.min(mc.world.getTopYInclusive(), pPos.getY() + 40);

            BlockPos.Mutable mut = new BlockPos.Mutable();
            int limit = 300;
            outer:
            for (int x = pPos.getX() - r; x <= pPos.getX() + r; x++) {
                for (int z = pPos.getZ() - r; z <= pPos.getZ() + r; z++) {
                    if (!mc.world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        mut.set(x, y, z);
                        Block b = mc.world.getBlockState(mut).getBlock();
                        if (b == Blocks.AIR || b == Blocks.STONE || b == Blocks.DEEPSLATE || b == Blocks.DIRT) continue;
                        if (selectedEspBlocks.contains(b)) {
                            espFoundBlocks.add(mut.toImmutable());
                            if (espFoundBlocks.size() >= limit) break outer;
                        }
                    }
                }
            }
        }
    }

    private static int collectRedstoneInChunk(WorldChunk chunk, List<BlockPos> outPositions) {
        int count = 0;
        int bottomY = chunk.getBottomY();
        int topY = chunk.getTopYInclusive();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();

        for (int y = bottomY; y <= topY; y += 2) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    pos.set(startX + x, y, startZ + z);
                    Block b = chunk.getBlockState(pos).getBlock();
                    if (b instanceof PistonBlock || b instanceof ObserverBlock || b instanceof RepeaterBlock
                            || b instanceof ComparatorBlock || b instanceof RedstoneWireBlock
                            || b == Blocks.REDSTONE_BLOCK || b == Blocks.SLIME_BLOCK || b == Blocks.HONEY_BLOCK) {
                        count++;
                        if (outPositions.size() < 40) {
                            outPositions.add(pos.toImmutable());
                        }
                        if (count >= redstoneFarmThreshold) return count;
                    }
                }
            }
        }
        return count;
    }

    private static String getSpawnerMobId(MinecraftClient mc, MobSpawnerBlockEntity spawner) {
        try {
            Entity rendered = spawner.getLogic().getRenderedEntity(mc.world, spawner.getPos());
            if (rendered != null) {
                return Registries.ENTITY_TYPE.getId(rendered.getType()).getPath();
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private static void sendAlert(MinecraftClient mc, String msg) {
        lastAlertMessage = msg;
        lastAlertTime = System.currentTimeMillis();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("§9§lVELO §8» " + msg), false);
        }
    }

    /**
     * Glowing Outline genom väggar & under marken med GizmoDrawing + ignoreOcclusion() (Minecraft 1.21.11).
     */
    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        if (blockEspEnabled) {
            int color = VeloClient.getAccentColor();
            for (BlockPos pos : espFoundBlocks) {
                GizmoDrawing.box(new Box(pos), DrawStyle.stroked(color, 2.5f)).ignoreOcclusion();
            }
        }

        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                GizmoDrawing.box(new Box(pos), DrawStyle.stroked(0xFFFFB020, 3.0f)).ignoreOcclusion();
            }
            for (BlockPos pos : redstoneFarmBlocks) {
                GizmoDrawing.box(new Box(pos), DrawStyle.stroked(0xFFFF3B5C, 3.0f)).ignoreOcclusion();
            }
        }

        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                GizmoDrawing.box(new Box(pos), DrawStyle.stroked(0xFFE040FB, 3.0f)).ignoreOcclusion();
            }
        }
    }
}
