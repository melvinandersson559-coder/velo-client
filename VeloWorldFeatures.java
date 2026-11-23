package com.velo.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class VeloWorldFeatures {

    // --- BLOCK ESP ---
    public static boolean blockEspEnabled = false;
    public static int blockEspRange = 48;
    public static int selectedEspBlockIndex = 0;

    public static final Block[] ESP_BLOCKS = new Block[]{
            Blocks.DIAMOND_ORE,
            Blocks.DEEPSLATE_DIAMOND_ORE,
            Blocks.ANCIENT_DEBRIS,
            Blocks.CHEST,
            Blocks.ENDER_CHEST,
            Blocks.SPAWNER,
            Blocks.EMERALD_ORE,
            Blocks.GOLD_ORE,
            Blocks.OBSIDIAN,
            Blocks.SHULKER_BOX
    };

    public static final String[] ESP_BLOCK_NAMES = new String[]{
            "Diamond Ore",
            "Deepslate Diamond",
            "Ancient Debris",
            "Chest",
            "Ender Chest",
            "Monster Spawner",
            "Emerald Ore",
            "Gold Ore",
            "Obsidian",
            "Shulker Box"
    };

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

    // Cachade resultat för rendering & anti-spam
    private static final List<BlockPos> espFoundBlocks = new ArrayList<>();
    private static final List<BlockPos> baseFoundBlocks = new ArrayList<>();
    private static final List<BlockPos> spawnerFoundBlocks = new ArrayList<>();

    private static final Set<Long> alertedBaseChunks = new HashSet<>();
    private static final Set<Long> alertedRedstoneChunks = new HashSet<>();
    private static final Set<Long> alertedSpawners = new HashSet<>();

    public static String lastAlertMessage = "";
    public static long lastAlertTime = 0;

    private static int tickCounter = 0;

    public static String getSelectedEspBlockName() {
        return ESP_BLOCK_NAMES[selectedEspBlockIndex % ESP_BLOCK_NAMES.length];
    }

    public static Block getSelectedEspBlock() {
        return ESP_BLOCKS[selectedEspBlockIndex % ESP_BLOCKS.length];
    }

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
            spawnerFoundBlocks.clear();
            return;
        }

        tickCounter++;
        // Skanna var 20:e tick (1 gång per sekund) så FPS förblir hög
        if (tickCounter % 20 != 0) return;

        int chunkRadius = Math.min(mc.options.getClampedViewDistance(), 8);
        ChunkPos playerChunk = mc.player.getChunkPos();

        // 1. BASE FINDER & SPAWNER FINDER (laddade chunks)
        if (baseFinderEnabled || spawnerFinderEnabled) {
            baseFoundBlocks.clear();
            spawnerFoundBlocks.clear();

            for (int cx = playerChunk.x - chunkRadius; cx <= playerChunk.x + chunkRadius; cx++) {
                for (int cz = playerChunk.z - chunkRadius; cz <= playerChunk.z + chunkRadius; cz++) {
                    WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                    if (chunk == null) continue;

                    long chunkKey = ChunkPos.toLong(cx, cz);
                    int storageCount = 0;
                    int redstoneBeCount = 0;
                    BlockPos sampleBasePos = null;

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        Block block = chunk.getBlockState(pos).getBlock();

                        // Base Finder: Kistor, Ender Chests, Shulker Boxes
                        if (baseFinderEnabled) {
                            if (block instanceof ChestBlock || block instanceof TrappedChestBlock
                                    || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock) {
                                storageCount++;
                                if (sampleBasePos == null) sampleBasePos = pos;
                                baseFoundBlocks.add(pos);
                            } else if (block instanceof HopperBlock || block instanceof DispenserBlock
                                    || block instanceof DropperBlock || block instanceof ComparatorBlock) {
                                redstoneBeCount++;
                            }
                        }

                        // Spawner Finder
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

                    // Base Finder: kontrollera bas-varning
                    if (baseFinderEnabled && storageCount >= baseChestThreshold && sampleBasePos != null) {
                        if (alertedBaseChunks.add(chunkKey)) {
                            sendAlert(mc, "§b[Base Finder] §fMöjlig §bBAS §f(" + storageCount
                                    + " kistor/shulkers) vid §b" + sampleBasePos.getX() + ", " + sampleBasePos.getY() + ", " + sampleBasePos.getZ());
                        }
                    }

                    // Base Finder: skanna efter redstone-block i chunken (pistons, observers, repeaters m.m.)
                    if (baseFinderEnabled && !alertedRedstoneChunks.contains(chunkKey)) {
                        int redstoneCount = redstoneBeCount + countRedstoneInChunk(chunk);
                        if (redstoneCount >= redstoneFarmThreshold) {
                            alertedRedstoneChunks.add(chunkKey);
                            int wx = (cx << 4) + 8;
                            int wz = (cz << 4) + 8;
                            sendAlert(mc, "§c[Base Finder] §fMöjlig §cREDSTONE FARM §f(" + redstoneCount
                                    + " redstone-block) i chunk vid §bX:" + wx + " Z:" + wz);
                        }
                    }
                }
            }
        } else {
            baseFoundBlocks.clear();
            spawnerFoundBlocks.clear();
        }

        // 2. BLOCK ESP (skannar valt block runt spelaren)
        espFoundBlocks.clear();
        if (blockEspEnabled) {
            Block target = getSelectedEspBlock();
            BlockPos pPos = mc.player.getBlockPos();
            int r = blockEspRange;
            int minY = Math.max(mc.world.getBottomY(), pPos.getY() - 32);
            int maxY = Math.min(mc.world.getTopYInclusive(), pPos.getY() + 32);

            BlockPos.Mutable mut = new BlockPos.Mutable();
            int limit = 250;
            outer:
            for (int x = pPos.getX() - r; x <= pPos.getX() + r; x++) {
                for (int z = pPos.getZ() - r; z <= pPos.getZ() + r; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        mut.set(x, y, z);
                        Block b = mc.world.getBlockState(mut).getBlock();
                        boolean match = (b == target)
                                || (target == Blocks.SHULKER_BOX && b instanceof ShulkerBoxBlock);
                        if (match) {
                            espFoundBlocks.add(mut.toImmutable());
                            if (espFoundBlocks.size() >= limit) break outer;
                        }
                    }
                }
            }
        }
    }

    private static int countRedstoneInChunk(WorldChunk chunk) {
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
     * Ritar 3D-outlines för Block ESP, Base Finder och Spawner Finder (1.21.11 WorldRenderEvents.END_MAIN).
     */
    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (espFoundBlocks.isEmpty() && baseFoundBlocks.isEmpty() && spawnerFoundBlocks.isEmpty()) return;

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        Vec3d cam = context.worldState().cameraRenderState.pos;
        VertexConsumer lineConsumer = consumers.getBuffer(RenderLayers.lines());

        matrices.push();

        // Block ESP (Cyan/Blå)
        if (blockEspEnabled) {
            int color = VeloClient.getAccentColor();
            for (BlockPos pos : espFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        color, 2.0f
                );
            }
        }

        // Base Finder (Guld/Orange för kistor/shulkers)
        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFFFA500, 2.0f
                );
            }
        }

        // Spawner Finder (Lila/Magenta)
        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFD946EF, 2.5f
                );
            }
        }

        matrices.pop();
    }
}
