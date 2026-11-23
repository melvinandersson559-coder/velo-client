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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class VeloWorldFeatures {

    // --- POPUP TOAST SYSTEM (Toppen av skärmen istället för chatt) ---
    public static class PopupAlert {
        public final String tag;
        public final String title;
        public final String details;
        public final String coords;
        public final int accentColor;
        public final long createdAt;
        public final long durationMs;

        public PopupAlert(String tag, String title, String details, String coords, int accentColor, long durationMs) {
            this.tag = tag;
            this.title = title;
            this.details = details;
            this.coords = coords;
            this.accentColor = accentColor;
            this.createdAt = System.currentTimeMillis();
            this.durationMs = durationMs;
        }
    }

    private static PopupAlert activePopup = null;

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

    public static void showPopup(String tag, String title, String details, String coords, int accentColor) {
        activePopup = new PopupAlert(tag, title, details, coords, accentColor, 6500L);
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
                    int shulkerCount = 0;
                    int redstoneBeCount = 0;
                    int chunkSpawnerCount = 0;
                    BlockPos sampleBasePos = null;
                    BlockPos sampleSpawnerPos = null;
                    String lastMatchedMob = "unknown";
                    List<BlockPos> chunkRedstonePositions = new ArrayList<>();

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getPos();
                        Block block = chunk.getBlockState(pos).getBlock();

                        if (baseFinderEnabled) {
                            if (block instanceof ChestBlock || block instanceof TrappedChestBlock
                                    || block instanceof EnderChestBlock || block instanceof BarrelBlock) {
                                storageCount++;
                                if (sampleBasePos == null) sampleBasePos = pos;
                                baseFoundBlocks.add(pos);
                            } else if (block instanceof ShulkerBoxBlock) {
                                storageCount++;
                                shulkerCount++;
                                if (sampleBasePos == null) sampleBasePos = pos;
                                baseFoundBlocks.add(pos);
                            } else if (block instanceof HopperBlock || block instanceof DispenserBlock
                                    || block instanceof DropperBlock || block instanceof ComparatorBlock) {
                                redstoneBeCount++;
                                chunkRedstonePositions.add(pos);
                            }
                        }

                        if (be instanceof MobSpawnerBlockEntity spawnerBe) {
                            chunkSpawnerCount++;
                            if (spawnerFinderEnabled) {
                                String mobId = getSpawnerMobId(mc, spawnerBe);
                                String wanted = SPAWNER_TYPES[selectedSpawnerTypeIndex % SPAWNER_TYPES.length];
                                if (wanted.equals("ALL") || mobId.equalsIgnoreCase(wanted)) {
                                    spawnerFoundBlocks.add(pos);
                                    sampleSpawnerPos = pos;
                                    lastMatchedMob = mobId;
                                }
                            }
                        }
                    }

                    int redstoneCount = 0;
                    if (baseFinderEnabled) {
                        redstoneCount = redstoneBeCount + collectRedstoneInChunk(chunk, chunkRedstonePositions);
                        if (redstoneCount >= redstoneFarmThreshold) {
                            int cap = Math.min(chunkRedstonePositions.size(), 36);
                            for (int i = 0; i < cap; i++) {
                                redstoneFarmBlocks.add(chunkRedstonePositions.get(i));
                            }
                        }
                    }

                    // Popup för Base Finder (visar storlek, kistor/shulkers, redstone och spawners)
                    if (baseFinderEnabled && storageCount >= baseChestThreshold && sampleBasePos != null) {
                        if (alertedBaseChunks.add(chunkKey)) {
                            String sizeLabel = classifyBaseSize(storageCount, shulkerCount, redstoneCount, chunkSpawnerCount);
                            String details = storageCount + " kistor"
                                    + (shulkerCount > 0 ? " (" + shulkerCount + " shulkers)" : "")
                                    + "  •  " + redstoneCount + " redstone"
                                    + "  •  " + chunkSpawnerCount + " spawners";
                            String coords = "X: " + sampleBasePos.getX() + "  Y: " + sampleBasePos.getY() + "  Z: " + sampleBasePos.getZ();
                            showPopup("BASE FINDER", sizeLabel + " HITTAD", details, coords, 0xFFFFB020);
                        }
                    } else if (baseFinderEnabled && redstoneCount >= redstoneFarmThreshold) {
                        if (alertedRedstoneChunks.add(chunkKey)) {
                            int wx = (cx << 4) + 8;
                            int wz = (cz << 4) + 8;
                            String sizeLabel = redstoneCount >= 50 ? "STOR REDSTONE FARM" : "REDSTONE FARM";
                            String details = redstoneCount + " redstone  •  " + storageCount + " kistor  •  " + chunkSpawnerCount + " spawners";
                            String coords = "Chunk X: " + wx + "  Z: " + wz;
                            showPopup("BASE FINDER", sizeLabel, details, coords, 0xFFFF4D6D);
                        }
                    }

                    // Popup för Spawner Finder
                    if (spawnerFinderEnabled && sampleSpawnerPos != null) {
                        long spawnerKey = sampleSpawnerPos.asLong();
                        if (alertedSpawners.add(spawnerKey)) {
                            String mobTitle = lastMatchedMob.replace('_', ' ').toUpperCase(Locale.ROOT) + " SPAWNER";
                            String details = chunkSpawnerCount + (chunkSpawnerCount == 1 ? " spawner i chunk" : " spawners i chunk")
                                    + (storageCount > 0 ? "  •  " + storageCount + " kistor" : "");
                            String coords = "X: " + sampleSpawnerPos.getX() + "  Y: " + sampleSpawnerPos.getY() + "  Z: " + sampleSpawnerPos.getZ();
                            showPopup("SPAWNER FINDER", mobTitle, details, coords, 0xFFE040FB);
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

    private static String classifyBaseSize(int storage, int shulkers, int redstone, int spawners) {
        int score = storage + shulkers * 3 + (redstone / 4) + spawners * 6;
        if (score >= 45) return "MEGA BAS";
        if (score >= 22) return "STOR BAS";
        if (score >= 10) return "MEDEL BAS";
        return "LITEN BAS";
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
                        if (count >= 120) return count;
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

    /**
     * 3D-outlines i världen via verifierad Minecraft 1.21.11 API.
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
                        color, 2.0f
                );
            }
        }

        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFFFB020, 2.2f
                );
            }
            for (BlockPos pos : redstoneFarmBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFFF4D6D, 2.2f
                );
            }
        }

        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                VertexRendering.drawOutline(
                        matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z,
                        0xFFE040FB, 2.5f
                );
            }
        }

        matrices.pop();
    }

    /**
     * Snygg, clean genom-väggar ESP med mjuka glödande hörnhakar + subtil fyllning.
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
                drawModernEspBox(ctx, pos, color, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
        }

        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                drawModernEspBox(ctx, pos, 0xFFFFB020, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
            for (BlockPos pos : redstoneFarmBlocks) {
                drawModernEspBox(ctx, pos, 0xFFFF4D6D, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
        }

        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                drawModernEspBox(ctx, pos, 0xFFE040FB, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
            }
        }
    }

    private static void drawModernEspBox(
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
        int w = x2 - x1;
        int h = y2 - y1;
        if (w < 3 || h < 3) return;

        int rgb = argb & 0x00FFFFFF;
        int softFill = (0x1A << 24) | rgb;
        int faintBorder = (0x55 << 24) | rgb;
        int brightCorner = (0xEE << 24) | rgb;

        // Mjuk anti-aliased inner-glow
        VeloUiRenderer.drawSmoothPanel(ctx, x1, y1, w, h, Math.min(4, Math.min(w, h) / 3), softFill, faintBorder, false);

        // Moderna lysande hörnhakar (corner brackets)
        int cl = Math.max(3, Math.min(8, Math.min(w, h) / 3));
        // Top-left
        ctx.fill(x1, y1, x1 + cl, y1 + 1, brightCorner);
        ctx.fill(x1, y1, x1 + 1, y1 + cl, brightCorner);
        // Top-right
        ctx.fill(x2 - cl, y1, x2, y1 + 1, brightCorner);
        ctx.fill(x2 - 1, y1, x2, y1 + cl, brightCorner);
        // Bottom-left
        ctx.fill(x1, y2 - 1, x1 + cl, y2, brightCorner);
        ctx.fill(x1, y2 - cl, x1 + 1, y2, brightCorner);
        // Bottom-right
        ctx.fill(x2 - cl, y2 - 1, x2, y2, brightCorner);
        ctx.fill(x2 - 1, y2 - cl, x2, y2, brightCorner);
    }

    /**
     * Modern animerad pop-up på toppen av skärmen (ersätter chatt-meddelanden).
     */
    public static void renderTopPopup(DrawContext ctx, MinecraftClient mc) {
        PopupAlert popup = activePopup;
        if (popup == null) return;

        long elapsed = System.currentTimeMillis() - popup.createdAt;
        if (elapsed >= popup.durationMs) {
            activePopup = null;
            return;
        }

        // Smooth slide-in (320ms) och slide-out (350ms)
        float anim;
        if (elapsed < 320L) {
            float t = elapsed / 320.0f;
            anim = 1.0f - (float) Math.pow(1.0f - t, 3.0);
        } else if (elapsed > popup.durationMs - 350L) {
            float t = (popup.durationMs - elapsed) / 350.0f;
            anim = MathHelper.clamp(t * t, 0.0f, 1.0f);
        } else {
            anim = 1.0f;
        }

        int sw = mc.getWindow().getScaledWidth();
        int titleW = mc.textRenderer.getWidth(popup.tag + "  " + popup.title);
        int detailsW = mc.textRenderer.getWidth(popup.details);
        int coordsW = mc.textRenderer.getWidth(popup.coords);
        int boxW = Math.max(210, Math.max(titleW, Math.max(detailsW, coordsW)) + 34);
        int boxH = 48;
        int boxX = (sw - boxW) / 2;
        int boxY = (int) (-boxH - 8 + (boxH + 20) * anim);

        int accentRgb = popup.accentColor & 0x00FFFFFF;
        int borderCol = (0xCC << 24) | accentRgb;

        VeloUiRenderer.drawSmoothPanel(ctx, boxX, boxY, boxW, boxH, 9, 0xEC070E1D, borderCol, true);

        // Vänster accent-stapel
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 5, boxY + 8, 3, boxH - 16, 2, popup.accentColor);

        // Liten pill-badge för taggen (BASE FINDER / SPAWNER FINDER)
        int tagW = mc.textRenderer.getWidth(popup.tag) + 10;
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 14, boxY + 7, tagW, 12, 4, (0x33 << 24) | accentRgb);
        ctx.drawTextWithShadow(mc.textRenderer, popup.tag, boxX + 19, boxY + 9, popup.accentColor);

        // Rubrik (t.ex. STOR BAS HITTAD / SKELETON SPAWNER)
        ctx.drawTextWithShadow(mc.textRenderer, "§l" + popup.title, boxX + 14 + tagW + 6, boxY + 9, 0xFFF8FAFC);

        // Detaljer (kistor, redstone, spawners)
        ctx.drawTextWithShadow(mc.textRenderer, popup.details, boxX + 14, boxY + 23, 0xFFCBD5E1);

        // Koordinater
        ctx.drawTextWithShadow(mc.textRenderer, popup.coords, boxX + 14, boxY + 34, 0xFF94A3B8);

        // Tunn progress-bar längst ner
        float remaining = 1.0f - MathHelper.clamp((float) elapsed / (float) popup.durationMs, 0.0f, 1.0f);
        int barMaxW = boxW - 22;
        int barW = Math.max(4, Math.round(barMaxW * remaining));
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 11, boxY + boxH - 3, barMaxW, 2, 1, 0x331E293B);
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 11, boxY + boxH - 3, barW, 2, 1, popup.accentColor);
    }
}
