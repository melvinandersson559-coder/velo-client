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
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class VeloWorldFeatures {

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

    public static class LogoutSpot {
        public final String name;
        public final Vec3d pos;
        public final long timestamp;

        public LogoutSpot(String name, Vec3d pos) {
            this.name = name;
            this.pos = pos;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static PopupAlert activePopup = null;

    // --- BLOCK ESP ---
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

    // --- EXTRA METEOR-STYLE VISUALS ---
    public static boolean playerEsp = false;
    public static boolean mobEsp = false;
    public static boolean itemEsp = false;
    public static boolean tracers = false;
    public static boolean waypointsEnabled = true;
    public static boolean breadcrumbs = false;
    public static boolean logoutSpots = false;

    private static final List<BlockPos> espFoundBlocks = new ArrayList<>();
    private static final List<BlockPos> baseFoundBlocks = new ArrayList<>();
    private static final List<BlockPos> redstoneFarmBlocks = new ArrayList<>();
    private static final List<BlockPos> spawnerFoundBlocks = new ArrayList<>();

    private static final Deque<Vec3d> breadcrumbPoints = new ArrayDeque<>();
    private static final Map<UUID, Vec3d> trackedPlayers = new HashMap<>();
    private static final Map<UUID, String> trackedPlayerNames = new HashMap<>();
    private static final List<LogoutSpot> savedLogoutSpots = new ArrayList<>();

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
            breadcrumbPoints.clear();
            trackedPlayers.clear();
            trackedPlayerNames.clear();
            return;
        }

        // Breadcrumbs spår
        if (breadcrumbs) {
            Vec3d p = new Vec3d(mc.player.getX(), mc.player.getY() + 0.15, mc.player.getZ());
            if (breadcrumbPoints.isEmpty() || breadcrumbPoints.peekLast().squaredDistanceTo(p) > 0.35) {
                breadcrumbPoints.addLast(p);
                while (breadcrumbPoints.size() > 140) breadcrumbPoints.removeFirst();
            }
        } else if (!breadcrumbPoints.isEmpty()) {
            breadcrumbPoints.clear();
        }

        tickCounter++;
        if (tickCounter % 20 != 0) return;

        // LogoutSpots spårning
        if (logoutSpots) {
            Set<UUID> currentIds = new HashSet<>();
            for (PlayerEntity pe : mc.world.getPlayers()) {
                if (pe == mc.player) continue;
                currentIds.add(pe.getUuid());
                trackedPlayers.put(pe.getUuid(), new Vec3d(pe.getX(), pe.getY(), pe.getZ()));
                trackedPlayerNames.put(pe.getUuid(), pe.getName().getString());
            }
            Iterator<Map.Entry<UUID, Vec3d>> it = trackedPlayers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Vec3d> e = it.next();
                if (!currentIds.contains(e.getKey())) {
                    String pname = trackedPlayerNames.getOrDefault(e.getKey(), "Player");
                    Vec3d lpos = e.getValue();
                    savedLogoutSpots.add(new LogoutSpot(pname, lpos));
                    if (savedLogoutSpots.size() > 15) savedLogoutSpots.remove(0);
                    showPopup("LOGOUT SPOT", pname + " LOGGADE UT", "Spelaren försvann härifrån",
                            String.format("X: %d  Y: %d  Z: %d", (int) lpos.x, (int) lpos.y, (int) lpos.z), 0xFF38BDF8);
                    it.remove();
                    trackedPlayerNames.remove(e.getKey());
                }
            }
        }

        int chunkRadius = Math.min(mc.options.getClampedViewDistance(), 8);
        ChunkPos playerChunk = mc.player.getChunkPos();

        // 1. BASE FINDER & SPAWNER FINDER
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

        // 2. BLOCK ESP
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
                        if (outPositions.size() < 40) outPositions.add(pos.toImmutable());
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
                VertexRendering.drawOutline(matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, color, 2.0f);
            }
        }

        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                VertexRendering.drawOutline(matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, 0xFFFFB020, 2.2f);
            }
            for (BlockPos pos : redstoneFarmBlocks) {
                VertexRendering.drawOutline(matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, 0xFFFF4D6D, 2.2f);
            }
        }

        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                VertexRendering.drawOutline(matrices, lineConsumer, VoxelShapes.fullCube(),
                        pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z, 0xFFE040FB, 2.5f);
            }
        }

        matrices.pop();
    }

    public static void renderThroughWallGlow(DrawContext ctx, MinecraftClient mc) {
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;
        boolean anyActive = blockEspEnabled || baseFinderEnabled || spawnerFinderEnabled
                || playerEsp || mobEsp || itemEsp || tracers || waypointsEnabled || breadcrumbs || logoutSpots;
        if (!anyActive) return;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        if (sw <= 0 || sh <= 0) return;

        Vec3d camPos = lastCamPos;
        if (camPos == null || camPos == Vec3d.ZERO) {
            camPos = new Vec3d(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        }

        float yaw = FreecamManager.isEnabled() ? FreecamManager.camYaw
                : (FreecamManager.isFreeLookActive() ? FreecamManager.freeLookYaw : mc.player.getYaw());
        float pitch = FreecamManager.isEnabled() ? FreecamManager.camPitch
                : (FreecamManager.isFreeLookActive() ? FreecamManager.freeLookPitch : mc.player.getPitch());
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

        // Block ESP / Base Finder / Spawner Finder
        if (blockEspEnabled) {
            int color = VeloClient.getAccentColor();
            for (BlockPos pos : espFoundBlocks) {
                drawModernEspBox(ctx, pos.getX(), pos.getY(), pos.getZ(), 1, 1, 1, color, null,
                        camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh, false, mc);
            }
        }
        if (baseFinderEnabled) {
            for (BlockPos pos : baseFoundBlocks) {
                drawModernEspBox(ctx, pos.getX(), pos.getY(), pos.getZ(), 1, 1, 1, 0xFFFFB020, null,
                        camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh, false, mc);
            }
            for (BlockPos pos : redstoneFarmBlocks) {
                drawModernEspBox(ctx, pos.getX(), pos.getY(), pos.getZ(), 1, 1, 1, 0xFFFF4D6D, null,
                        camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh, false, mc);
            }
        }
        if (spawnerFinderEnabled) {
            for (BlockPos pos : spawnerFoundBlocks) {
                drawModernEspBox(ctx, pos.getX(), pos.getY(), pos.getZ(), 1, 1, 1, 0xFFE040FB, null,
                        camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh, false, mc);
            }
        }

        // Player ESP, Mob ESP, Item ESP & Tracers
        if (playerEsp || mobEsp || itemEsp || tracers) {
            for (Entity e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                if (e instanceof PlayerEntity pe && (playerEsp || tracers)) {
                    int dist = (int) Math.round(mc.player.distanceTo(pe));
                    String tag = playerEsp ? (pe.getName().getString() + " §c" + (int) Math.ceil(pe.getHealth()) + "❤ §7" + dist + "m") : null;
                    drawModernEspBox(ctx, pe.getX() - 0.35, pe.getY(), pe.getZ() - 0.35, 0.7, pe.getHeight(), 0.7,
                            0xFF38BDF8, tag, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh, tracers, mc);
                } else if (e instanceof HostileEntity he && mobEsp) {
                    String tag = he.getName().getString() + " §c" + (int) Math.ceil(he.getHealth()) + "❤";
                    drawModernEspBox(ctx, he.getX() - 0.35, he.getY(), he.getZ() - 0.35, 0.7, he.getHeight(), 0.7,
                            0xFFFF4D6D, tag, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh, false, mc);
                } else if (e instanceof ItemEntity ie && itemEsp) {
                    String tag = ie.getStack().getName().getString() + (ie.getStack().getCount() > 1 ? " x" + ie.getStack().getCount() : "");
                    drawModernEspBox(ctx, ie.getX() - 0.2, ie.getY(), ie.getZ() - 0.2, 0.4, 0.4, 0.4,
                            0xFF34D399, tag, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh, false, mc);
                }
            }
        }

        // Waypoints
        if (waypointsEnabled && !VeloConfigManager.data.waypoints.isEmpty()) {
            for (VeloConfigManager.Waypoint wp : VeloConfigManager.data.waypoints) {
                if (!wp.enabled) continue;
                double[] screen = projectPoint(wp.x + 0.5, wp.y + 1.2, wp.z + 0.5, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
                if (screen == null) continue;
                int dist = (int) Math.round(Math.sqrt(mc.player.squaredDistanceTo(wp.x + 0.5, wp.y + 0.5, wp.z + 0.5)));
                String label = "§b◆ §f" + wp.name + " §7[" + dist + "m]";
                int lw = mc.textRenderer.getWidth(label) + 12;
                int lx = (int) screen[0] - lw / 2;
                int ly = (int) screen[1] - 10;
                VeloUiRenderer.drawSmoothPanel(ctx, lx, ly, lw, 16, 6, 0xCC070E1D, wp.color, false);
                ctx.drawTextWithShadow(mc.textRenderer, label, lx + 6, ly + 4, 0xFFFFFFFF);
            }
        }

        // LogoutSpots
        if (logoutSpots && !savedLogoutSpots.isEmpty()) {
            for (LogoutSpot spot : savedLogoutSpots) {
                double[] screen = projectPoint(spot.pos.x, spot.pos.y + 1.0, spot.pos.z, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
                if (screen == null) continue;
                String label = "§c✕ §f" + spot.name + " §7(Logout)";
                int lw = mc.textRenderer.getWidth(label) + 10;
                int lx = (int) screen[0] - lw / 2;
                int ly = (int) screen[1] - 8;
                VeloUiRenderer.drawSmoothPanel(ctx, lx, ly, lw, 15, 5, 0xCC180A12, 0xFFFF4D6D, false);
                ctx.drawTextWithShadow(mc.textRenderer, label, lx + 5, ly + 4, 0xFFFFFFFF);
            }
        }

        // Breadcrumbs trail
        if (breadcrumbs && breadcrumbPoints.size() >= 2) {
            int accent = VeloClient.getAccentColor();
            double[] prev = null;
            for (Vec3d pt : breadcrumbPoints) {
                double[] cur = projectPoint(pt.x, pt.y, pt.z, camPos, fx, fy, fz, rx, ry, rz, ux, uy, uz, tanHalfFov, aspect, sw, sh);
                if (prev != null && cur != null) {
                    draw2DLine(ctx, (int) prev[0], (int) prev[1], (int) cur[0], (int) cur[1], accent);
                }
                prev = cur;
            }
        }
    }

    private static double[] projectPoint(
            double wx0, double wy0, double wz0, Vec3d cam,
            double fx, double fy, double fz,
            double rx, double ry, double rz,
            double ux, double uy, double uz,
            double tanHalfFov, double aspect, int sw, int sh
    ) {
        double wx = wx0 - cam.x;
        double wy = wy0 - cam.y;
        double wz = wz0 - cam.z;
        double camZ = wx * fx + wy * fy + wz * fz;
        if (camZ <= 0.15) return null;
        double camX = wx * rx + wy * ry + wz * rz;
        double camY = wx * ux + wy * uy + wz * uz;
        double ndcX = camX / (camZ * tanHalfFov * aspect);
        double ndcY = camY / (camZ * tanHalfFov);
        double sx = (ndcX + 1.0) * 0.5 * sw;
        double sy = (1.0 - ndcY) * 0.5 * sh;
        if (sx < -60 || sx > sw + 60 || sy < -60 || sy > sh + 60) return null;
        return new double[]{sx, sy};
    }

    private static void draw2DLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int steps = Math.max(dx, dy);
        if (steps <= 0 || steps > 1200) return;
        int stride = Math.max(1, steps / 90);
        for (int i = 0; i <= steps; i += stride) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawModernEspBox(
            DrawContext ctx, double bx, double by, double bz, double bw, double bh, double bd,
            int argb, String label, Vec3d cam,
            double fx, double fy, double fz,
            double rx, double ry, double rz,
            double ux, double uy, double uz,
            double tanHalfFov, double aspect, int sw, int sh, boolean drawTracer, MinecraftClient mc
    ) {
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        int visibleCorners = 0;

        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = 0; dz <= 1; dz++) {
                    double wx = bx + dx * bw - cam.x;
                    double wy = by + dy * bh - cam.y;
                    double wz = bz + dz * bd - cam.z;
                    double camZ = wx * fx + wy * fy + wz * fz;
                    if (camZ <= 0.15) continue;
                    double camX = wx * rx + wy * ry + wz * rz;
                    double camY = wx * ux + wy * uy + wz * uz;
                    double sx = (camX / (camZ * tanHalfFov * aspect) + 1.0) * 0.5 * sw;
                    double sy = (1.0 - camY / (camZ * tanHalfFov)) * 0.5 * sh;
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
        int w = x2 - x1, h = y2 - y1;
        if (w < 3 || h < 3) return;

        int rgb = argb & 0x00FFFFFF;
        int softFill = (0x1A << 24) | rgb;
        int faintBorder = (0x55 << 24) | rgb;
        int brightCorner = (0xEE << 24) | rgb;

        VeloUiRenderer.drawSmoothPanel(ctx, x1, y1, w, h, Math.min(4, Math.min(w, h) / 3), softFill, faintBorder, false);

        int cl = Math.max(3, Math.min(8, Math.min(w, h) / 3));
        ctx.fill(x1, y1, x1 + cl, y1 + 1, brightCorner);
        ctx.fill(x1, y1, x1 + 1, y1 + cl, brightCorner);
        ctx.fill(x2 - cl, y1, x2, y1 + 1, brightCorner);
        ctx.fill(x2 - 1, y1, x2, y1 + cl, brightCorner);
        ctx.fill(x1, y2 - 1, x1 + cl, y2, brightCorner);
        ctx.fill(x1, y2 - cl, x1 + 1, y2, brightCorner);
        ctx.fill(x2 - cl, y2 - 1, x2, y2, brightCorner);
        ctx.fill(x2 - 1, y2 - cl, x2, y2, brightCorner);

        if (drawTracer) {
            draw2DLine(ctx, sw / 2, sh / 2, (x1 + x2) / 2, (y1 + y2) / 2, (0xAA << 24) | rgb);
        }

        if (label != null) {
            int lw = mc.textRenderer.getWidth(label) + 8;
            int lx = (x1 + x2 - lw) / 2;
            int ly = Math.max(2, y1 - 14);
            VeloUiRenderer.drawSmoothPanel(ctx, lx, ly, lw, 12, 4, 0xCC070E1D, (0x99 << 24) | rgb, false);
            ctx.drawTextWithShadow(mc.textRenderer, label, lx + 4, ly + 2, 0xFFFFFFFF);
        }
    }

    public static void renderTopPopup(DrawContext ctx, MinecraftClient mc) {
        PopupAlert popup = activePopup;
        if (popup == null) return;

        long elapsed = System.currentTimeMillis() - popup.createdAt;
        if (elapsed >= popup.durationMs) {
            activePopup = null;
            return;
        }

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
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 5, boxY + 8, 3, boxH - 16, 2, popup.accentColor);

        int tagW = mc.textRenderer.getWidth(popup.tag) + 10;
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 14, boxY + 7, tagW, 12, 4, (0x33 << 24) | accentRgb);
        ctx.drawTextWithShadow(mc.textRenderer, popup.tag, boxX + 19, boxY + 9, popup.accentColor);
        ctx.drawTextWithShadow(mc.textRenderer, "§l" + popup.title, boxX + 14 + tagW + 6, boxY + 9, 0xFFF8FAFC);
        ctx.drawTextWithShadow(mc.textRenderer, popup.details, boxX + 14, boxY + 23, 0xFFCBD5E1);
        ctx.drawTextWithShadow(mc.textRenderer, popup.coords, boxX + 14, boxY + 34, 0xFF94A3B8);

        float remaining = 1.0f - MathHelper.clamp((float) elapsed / (float) popup.durationMs, 0.0f, 1.0f);
        int barMaxW = boxW - 22;
        int barW = Math.max(4, Math.round(barMaxW * remaining));
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 11, boxY + boxH - 3, barMaxW, 2, 1, 0x331E293B);
        VeloUiRenderer.drawSmoothRect(ctx, boxX + 11, boxY + boxH - 3, barW, 2, 1, popup.accentColor);
    }
}
