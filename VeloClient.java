package com.velo.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class VeloClient implements ClientModInitializer {
    public static final String MOD_ID = "velocli";

    // Combat, HUD & Movement
    public static boolean totemWarning = true;
    public static boolean hudEnabled = true;
    public static boolean toggleSprint = true;
    public static boolean noHurtCam = true;
    public static boolean lowFire = true;
    public static float lowFireOffset = 0.34f;

    // ViewModel & Slow Swing
    public static boolean slowSwing = true;
    public static int swingDuration = 16;
    public static boolean slimHand = true;
    public static float handScale = 0.56f;
    public static float handOffsetX = 0.10f;
    public static float handOffsetY = -0.08f;
    public static float handOffsetZ = -0.18f;
    public static float handRotX = 0.0f;
    public static float handRotY = 0.0f;
    public static float handRotZ = 0.0f;

    // Blått färgtema (0 = Electric Blue, 1 = Ice Cyan, 2 = Royal Cobalt)
    public static int blueThemeIndex = 0;

    public static final int COLOR_BG = 0xD4070E1D;

    public static int getPrimaryBlue() {
        return switch (blueThemeIndex) {
            case 1 -> 0xFF0284C7;
            case 2 -> 0xFF1D4ED8;
            default -> 0xFF2563EB;
        };
    }

    public static int getAccentColor() {
        return switch (blueThemeIndex) {
            case 1 -> 0xFF22D3EE;
            case 2 -> 0xFF60A5FA;
            default -> 0xFF38BDF8;
        };
    }

    public static String getThemeName() {
        return switch (blueThemeIndex) {
            case 1 -> "Ice Cyan";
            case 2 -> "Royal Cobalt";
            default -> "Electric Blue";
        };
    }

    private static KeyBinding openMenuKey;
    private static KeyBinding freecamKey;
    private static KeyBinding hudEditorKey;
    private static KeyBinding zoomKey;
    private static KeyBinding freeLookKey;

    @Override
    public void onInitializeClient() {
        FavoriteServerManager.load();
        VeloConfigManager.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyBinding.Category.MISC));
        freecamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.freecam", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, KeyBinding.Category.MISC));
        hudEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.hud_editor", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, KeyBinding.Category.MISC));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, KeyBinding.Category.MISC));
        freeLookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, KeyBinding.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new VeloMenuScreen(client.currentScreen));
            }
            while (freecamKey.wasPressed()) {
                FreecamManager.toggle();
            }
            while (hudEditorKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new VeloHudEditorScreen(null));
                }
            }

            FreecamManager.setFreeLookActive(freeLookKey.isPressed() && client.currentScreen == null, client);
            FreecamManager.tick(client);
            VeloWorldFeatures.tick(client);
            VeloUtilityModules.tick(client, zoomKey.isPressed());

            if (toggleSprint && !FreecamManager.isEnabled() && client.player != null && client.options.forwardKey.isPressed()) {
                client.player.setSprinting(true);
            }
        });

        WorldRenderEvents.END_MAIN.register(VeloWorldFeatures::renderWorld);

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "velo_hud"), (context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            VeloUtilityModules.updateSmoothZoomFrame(mc, zoomKey.isPressed());
            VeloWorldFeatures.renderThroughWallGlow(context, mc);
            if (hudEnabled && mc.player != null && !mc.options.hudHidden && !(mc.currentScreen instanceof VeloHudEditorScreen)) {
                renderModularHud(context, mc);
            }
            renderTotemWarning(context, mc);
            VeloWorldFeatures.renderTopPopup(context, mc);
        });
    }

    private void renderModularHud(DrawContext ctx, MinecraftClient mc) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int accent = getAccentColor();
        int border = (0x88 << 24) | (accent & 0x00FFFFFF);
        VeloConfigManager.ConfigData d = VeloConfigManager.data;

        // 1. WATERMARK & STATS (FPS + Speed b/s)
        if (d.watermarkHud.enabled) {
            int x = MathHelper.clamp(d.watermarkHud.x, 4, sw - 156);
            int y = MathHelper.clamp(d.watermarkHud.y, 4, sh - 50);
            double dx = mc.player.getX() - mc.player.lastX;
            double dz = mc.player.getZ() - mc.player.lastZ;
            double bps = Math.sqrt(dx * dx + dz * dz) * 20.0;

            VeloUiRenderer.drawSmoothPanel(ctx, x, y, 152, 44, 9, COLOR_BG, border, true);
            VeloUiRenderer.drawSmoothRect(ctx, x + 5, y + 8, 2, 28, 1, accent);
            ctx.drawTextWithShadow(mc.textRenderer, "§lVELO §r§b2.2", x + 13, y + 7, accent);
            ctx.drawTextWithShadow(mc.textRenderer, String.format(Locale.ROOT, "§7FPS §f%d  §7•  §f%.1f b/s", mc.getCurrentFps(), bps), x + 13, y + 20, 0xFFE2E8F0);
            ctx.drawTextWithShadow(mc.textRenderer, "§7H: §bHUD Editor  §7RShift: §bMenu", x + 13, y + 31, 0xFF94A3B8);
        }

        // 2. COORDS + NETHER CONVERTER
        if (d.coordsHud.enabled) {
            int x = MathHelper.clamp(d.coordsHud.x, 4, sw - 170);
            int y = MathHelper.clamp(d.coordsHud.y, 4, sh - 40);
            int cx = FreecamManager.isEnabled() ? (int) FreecamManager.camX : mc.player.getBlockX();
            int cy = FreecamManager.isEnabled() ? (int) FreecamManager.camY : mc.player.getBlockY();
            int cz = FreecamManager.isEnabled() ? (int) FreecamManager.camZ : mc.player.getBlockZ();
            boolean inNether = mc.world != null && mc.world.getRegistryKey().getValue().getPath().contains("nether");
            int ox = inNether ? cx * 8 : cx / 8;
            int oz = inNether ? cz * 8 : cz / 8;
            String altTag = inNether ? "OW" : "Nether";

            VeloUiRenderer.drawSmoothPanel(ctx, x, y, 166, 34, 8, COLOR_BG, border, true);
            ctx.drawTextWithShadow(mc.textRenderer, String.format("§7XYZ  §f%d, %d, %d", cx, cy, cz), x + 9, y + 7, 0xFFFFFFFF);
            ctx.drawTextWithShadow(mc.textRenderer, String.format("§7%s  §b%d, %d", altTag, ox, oz), x + 9, y + 19, 0xFF94A3B8);
        }

        // 3. ACTIVE MODULES (Meteor-style ArrayList)
        if (d.arrayListHud.enabled) {
            List<String> active = getActiveModuleNames();
            if (!active.isEmpty()) {
                active.sort(Comparator.comparingInt((String s) -> mc.textRenderer.getWidth(s)).reversed());
                boolean alignRight = d.arrayListHud.x == -1 || d.arrayListHud.x > sw / 2;
                int baseX = d.arrayListHud.x == -1 ? sw - 10 : d.arrayListHud.x;
                int y = MathHelper.clamp(d.arrayListHud.y, 4, sh - 30);

                for (int i = 0; i < active.size(); i++) {
                    String mod = active.get(i);
                    int mw = mc.textRenderer.getWidth(mod) + 14;
                    int mx = alignRight ? (baseX - mw) : baseX;
                    int col = VeloUiRenderer.lerpColor(accent, getPrimaryBlue(), (float) i / Math.max(1, active.size() - 1));
                    VeloUiRenderer.drawSmoothPanel(ctx, mx, y, mw, 14, 4, 0xCC070E1D, (0x66 << 24) | (col & 0x00FFFFFF), false);
                    int barX = alignRight ? (mx + mw - 3) : (mx + 1);
                    VeloUiRenderer.drawSmoothRect(ctx, barX, y + 2, 2, 10, 1, col);
                    ctx.drawTextWithShadow(mc.textRenderer, mod, mx + (alignRight ? 5 : 7), y + 3, 0xFFF8FAFC);
                    y += 16;
                }
            }
        }

        // 4. ARMOR & TOTEM COUNTER HUD
        if (d.armorHud.enabled) {
            int x = MathHelper.clamp(d.armorHud.x, 4, sw - 132);
            int y = MathHelper.clamp(d.armorHud.y, 4, sh - 38);
            int totems = countTotems(mc);
            VeloUiRenderer.drawSmoothPanel(ctx, x, y, 128, 30, 8, COLOR_BG, border, true);
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < slots.length; i++) {
                ItemStack armor = mc.player.getEquippedStack(slots[i]);
                int ix = x + 6 + i * 20;
                if (!armor.isEmpty()) {
                    ctx.drawItem(armor, ix, y + 5);
                    if (armor.isDamageable()) {
                        float pct = 1.0f - (float) armor.getDamage() / Math.max(1, armor.getMaxDamage());
                        int barCol = pct > 0.5f ? 0xFF34D399 : (pct > 0.25f ? 0xFFFBBF24 : 0xFFFF4D6D);
                        ctx.fill(ix + 1, y + 23, ix + 15, y + 25, 0xFF1E293B);
                        ctx.fill(ix + 1, y + 23, ix + 1 + Math.round(14 * pct), y + 25, barCol);
                    }
                }
            }
            ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x + 90, y + 6);
            ctx.drawTextWithShadow(mc.textRenderer, "§f" + totems, x + 108, y + 11, totems > 0 ? 0xFFFDE68A : 0xFF94A3B8);
        }

        // 5. INVENTORY VIEWER
        if (d.inventoryHud.enabled) {
            int x = MathHelper.clamp(d.inventoryHud.x, 4, sw - 172);
            int y = MathHelper.clamp(d.inventoryHud.y, 4, sh - 66);
            VeloUiRenderer.drawSmoothPanel(ctx, x, y, 168, 60, 8, COLOR_BG, border, true);
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    ItemStack st = mc.player.getInventory().getStack(9 + row * 9 + col);
                    if (!st.isEmpty()) {
                        int ix = x + 4 + col * 18;
                        int iy = y + 4 + row * 18;
                        ctx.drawItem(st, ix, iy);
                        ctx.drawStackOverlay(mc.textRenderer, st, ix, iy);
                    }
                }
            }
        }

        // 6. TARGET HUD (Visas när du siktar på en spelare eller mob)
        if (d.targetHud.enabled && mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity target) {
            int x = d.targetHud.x == -2 ? (sw / 2 + 24) : MathHelper.clamp(d.targetHud.x, 4, sw - 156);
            int y = MathHelper.clamp(d.targetHud.y, 4, sh - 46);
            float hp = target.getHealth();
            float maxHp = Math.max(1.0f, target.getMaxHealth());
            float pct = MathHelper.clamp(hp / maxHp, 0.0f, 1.0f);
            int dist = (int) Math.round(mc.player.distanceTo(target));

            VeloUiRenderer.drawSmoothPanel(ctx, x, y, 152, 40, 9, COLOR_BG, accent, true);
            String tname = mc.textRenderer.trimToWidth(target.getName().getString(), 98);
            ctx.drawTextWithShadow(mc.textRenderer, "§l" + tname, x + 10, y + 7, 0xFFFFFFFF);
            ctx.drawTextWithShadow(mc.textRenderer, "§7" + dist + "m", x + 142 - mc.textRenderer.getWidth(dist + "m"), y + 7, 0xFF94A3B8);
            ctx.drawTextWithShadow(mc.textRenderer, String.format(Locale.ROOT, "§c%.1f §7/ %.0f ❤", hp, maxHp), x + 10, y + 18, 0xFFFFFFFF);
            VeloUiRenderer.drawSmoothRect(ctx, x + 10, y + 30, 132, 4, 2, 0xFF1E293B);
            VeloUiRenderer.drawSmoothRect(ctx, x + 10, y + 30, Math.max(4, Math.round(132 * pct)), 4, 2, accent);
        }

        // 7. COMPASS BAR
        if (d.compassHud.enabled) {
            int w = 180;
            int x = d.compassHud.x == -3 ? (sw - w) / 2 : MathHelper.clamp(d.compassHud.x, 4, sw - w - 4);
            int y = MathHelper.clamp(d.compassHud.y, 4, sh - 26);
            float yaw = ((mc.player.getYaw() % 360.0f) + 360.0f) % 360.0f;
            String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
            int idx = Math.round(yaw / 45.0f) % 8;
            String left = dirs[(idx + 7) % 8];
            String mid = dirs[idx];
            String right = dirs[(idx + 1) % 8];

            VeloUiRenderer.drawSmoothPanel(ctx, x, y, w, 20, 8, 0xBB070E1D, border, false);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, "§7" + left + "    §b§l" + mid + "    §r§7" + right + "  §8(" + Math.round(yaw) + "°)", x + w / 2, y + 6, 0xFFFFFFFF);
        }
    }

    private List<String> getActiveModuleNames() {
        List<String> list = new ArrayList<>();
        if (VeloUtilityModules.fullbright) list.add("Fullbright");
        if (noHurtCam) list.add("No Hurt Cam");
        if (lowFire) list.add("Low Fire");
        if (VeloWorldFeatures.baseFinderEnabled) list.add("Base Finder");
        if (VeloWorldFeatures.blockEspEnabled) list.add("Block ESP");
        if (VeloWorldFeatures.spawnerFinderEnabled) list.add("Spawner Finder");
        if (VeloWorldFeatures.playerEsp) list.add("Player ESP");
        if (VeloWorldFeatures.mobEsp) list.add("Mob ESP");
        if (VeloWorldFeatures.itemEsp) list.add("Item ESP");
        if (VeloWorldFeatures.tracers) list.add("Tracers");
        if (VeloWorldFeatures.breadcrumbs) list.add("Breadcrumbs");
        if (VeloWorldFeatures.logoutSpots) list.add("LogoutSpots");
        if (FreecamManager.isEnabled()) list.add("Freecam");
        if (VeloUtilityModules.chestCleaner) list.add("ChestCleaner");
        if (VeloUtilityModules.chestStealer) list.add("ChestStealer");
        if (VeloUtilityModules.autoTool) list.add("AutoTool");
        if (VeloUtilityModules.autoReplenish) list.add("AutoReplenish");
        if (VeloUtilityModules.autoRespawn) list.add("AutoRespawn");
        if (VeloUtilityModules.antiAfk) list.add("AntiAFK");
        if (VeloUtilityModules.middleClickPearl) list.add("MiddleClickPearl");
        if (slowSwing) list.add("Slow Swing");
        if (toggleSprint) list.add("ToggleSprint");
        return list;
    }

    private int countTotems(MinecraftClient client) {
        int count = 0;
        int invSize = client.player.getInventory().size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        return count;
    }

    private void renderTotemWarning(DrawContext context, MinecraftClient client) {
        if (!totemWarning || client.player == null || client.options.hudHidden) return;
        if (client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        int totemCount = countTotems(client);
        if (totemCount <= 0) return;

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 220.0);
        int borderCol = VeloUiRenderer.lerpColor(0xBBF59E0B, 0xFFFF3B5C, pulse);
        int bgCol = VeloUiRenderer.lerpColor(0xDF160B12, 0xEB260D16, pulse);

        String title = "INGEN TOTEM I OFFHAND";
        String sub = "Du har " + totemCount + (totemCount == 1 ? " totem" : " totems") + " i inventoryt!";

        int textW = Math.max(client.textRenderer.getWidth(title), client.textRenderer.getWidth(sub));
        int boxW = textW + 44;
        int boxH = 34;
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        int boxX = (sw - boxW) / 2;
        int boxY = Math.max(66, sh / 6);

        VeloUiRenderer.drawSmoothPanel(context, boxX, boxY, boxW, boxH, 9, bgCol, borderCol, true);
        context.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), boxX + 10, boxY + 9);
        context.drawTextWithShadow(client.textRenderer, "§l" + title, boxX + 32, boxY + 7, 0xFFFF6B81);
        context.drawTextWithShadow(client.textRenderer, sub, boxX + 32, boxY + 19, 0xFFFDE68A);
    }
}
