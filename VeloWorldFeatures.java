package com.velo.client;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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

    private static Vec3d lastCamPos = Vec3d.ZERO;
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
            int limit = 200;
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
     * 3D-outlines i världen via verifierad Minecraft 1.21.11 API (WorldRenderEvents.END_MAIN).
     */
    public static void renderWorld(WorldRenderContext context) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Vec3d cam = context.worldState().cameraRenderState.pos;
        lastCamPos = cam;

        if (espFoundBlocks.isEmpty() && baseFoundBlocks.isEmpty()
                && redstoneFarmBlocks.isEmpty() && spawnerFoundBlocks.isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrices();
        VertexConsumerProvider consumers = context.consumers();
        if (matrices == null || consumers == null) return;

        VertexConsumer lineConsumer = consumers.getBuffer(RenderLayers.lines());
        matrices.push();

        if (blockEspEnabled) {
            int color = VeloClient.getAccentColor();
            for (BlockPos pos : espFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        color, 2.5f
                );
            }
        }

        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFFFB020, 3.0f
                );
            }
            for (BlockPos pos : redstoneFarmBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFFF3B5C, 3.0f
                );
            }
        }

        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFE040FB, 3.0f
                );
            }
        }

        matrices.pop();
    }

    /**
     * Glowing Outline som syns GENOM väggar och djupt under marken via 3D->2D HUD-projektion.
     */
    public static void renderThroughWallGlow(DrawContext ctx, MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
        if (!blockEspEnabled && !baseFinderEnabled && !spawnerFinderEnabled) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (sw <= 0 || sh <= 0) return;

        Vec3d camPos = lastCamPos;
        if (camPos == null || camPos == Vec3d.ZERO) {
            camPos = new Vec3d(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        }

        float yaw = FreecamManager.isEnabled() ? FreecamManager.camYaw : mc.player.getYaw();
        float pitch = FreecamManager.isEnabled() ? FreecamManager.camPitch : mc.player.getPitch();
        double fov = mc.options.getFov().getValue();

        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);
        double sinYaw = Math.sin(yawRad);
        double cosYaw = Math.cos(yawRad);

        double fx = -sinYaw * cosPitch;
        double fy = -sinPitch;
        double fz = cosYaw * cosPitch;

        double rx = -cosYaw;
        double ry = 0.0;
        double rz = -sinYaw;

        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        double tanHalfFov = Math.tan(Math.toRadians(fov * 0.5));
        double aspect = (double) sw / (double) sh;

        if (blockEspEnabled) {
            int color = VeloClient.getAccentColor();
            for (BlockPos pos : espFoundBlocks) {
                drawGlowingBlockOutline(ctx, pos, color, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
        }

        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                drawGlowingBlockOutline(ctx, pos, 0xFFFFB020, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
            for (BlockPos pos : redstoneFarmBlocks) {
                drawGlowingBlockOutline(ctx, pos, 0xFFFF3B5C, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
        }

        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                drawGlowingBlockOutline(ctx, pos, 0xFFE040FB, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
        }
    }

    private static void drawGlowingBlockOutline(
            DrawContext ctx, BlockPos pos, int argb, Vec3d cam,
            double fx, double fy, double fz,
            double rx, double ry, double rz,
            double ux, double uy, double uz,
            double tanHalfFov, double aspect, int sw, int sh
    ) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int visibleCorners = 0;

        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    double wx = pos.getX() + dx - cam.x;
                    double wy = pos.getY() + dy - cam.y;
                    double wz = pos.getZ() + dz - cam.z;

                    double camZ = wx * fx + wy * fy + wz * fz;
                    if (camZ <= 0.15) continue;

                    double camX = wx * rx + wy * ry + wz * rz;
                    double camY = wx * ux + wy * uy + wz * uz;

                    double ndcX = camX / (camZ * tanHalfFov * aspect);
                    double ndcY = camY / (camZ * tanHalfFov);

                    double sx = (ndcX + 1.0) * 0.5 * sw;
                    double sy = (1.0 - ndcY) * 0.5 * sh;

                    if (sx < minX) minX = sx;
                    if (sy < minY) minY = sy;
                    if (sx > maxX) maxX = sx;
                    if (sy > maxY) maxY = sy;
                    visibleCorners++;
                }
            }
        }

        if (visibleCorners < 2) return;
        if (maxX < 0 || minX > sw || maxY < 0 || minY > sh) return;

        int x1 = Math.max(0, (int) Math.floor(minX));
        int y1 = Math.max(0, (int) Math.floor(minY));
        int x2 = Math.min(sw, (int) Math.ceil(maxX));
        int y2 = Math.min(sh, (int) Math.ceil(maxY));
        if (x2 - x1 < 2 || y2 - y1 < 2) return;

        int glowOuter = (0x55 << 24) | (argb & 0x00FFFFFF);
        int fillGlow = (0x22 << 24) | (argb & 0x00FFFFFF);
        int brightEdge = (0xFF << 24) | (argb & 0x00FFFFFF);

        // Mjuk glöd inuti och runt om
        ctx.fill(x1, y1, x2, y2, fillGlow);

        // Yttre glow-ram (2px)
        ctx.fill(x1 - 1, y1 - 1, x2 + 1, y1 + 1, glowOuter);
        ctx.fill(x1 - 1, y2 - 1, x2 + 1, y2 + 1, glowOuter);
        ctx.fill(x1 - 1, y1, x1 + 1, y2, glowOuter);
        ctx.fill(x2 - 1, y1, x2 + 1, y2, glowOuter);

        // Skarp lysande kant (1px)
        ctx.fill(x1, y1, x2, y1 + 1, brightEdge);
        ctx.fill(x1, y2 - 1, x2, y2, brightEdge);
        ctx.fill(x1, y1, x1 + 1, y2, brightEdge);
        ctx.fill(x2 - 1, y1, x2, y2, brightEdge);
    }
}
