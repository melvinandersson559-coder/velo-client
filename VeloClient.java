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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VeloClient implements ClientModInitializer {
    public static final String MOD_ID = "velocli";

    // Combat, HUD & Movement
    public static boolean totemWarning = true;
    public static boolean hudEnabled = true;
    public static boolean showFps = true;
    public static boolean showCoords = true;
    public static boolean toggleSprint = true;

    // Hand & Slow Swing
    public static boolean slowSwing = true;
    public static int swingDuration = 16; // 6 (normal) till 30 (långsam)
    public static boolean slimHand = true;
    public static float handScale = 0.56f;
    public static float handOffsetX = 0.10f;
    public static float handOffsetY = -0.08f;
    public static float handOffsetZ = -0.18f;

    // Blått färgtema (0 = Electric Blue, 1 = Ice Cyan, 2 = Royal Cobalt)
    public static int blueThemeIndex = 0;

    public static final int COLOR_BG = 0xD4070E1D;
    public static final int COLOR_TEXT = 0xFFE2E8F0;

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

    @Override
    public void onInitializeClient() {
        FavoriteServerManager.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        freecamKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.freecam",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new VeloMenuScreen(client.currentScreen));
            }

            while (freecamKey.wasPressed()) {
                FreecamManager.toggle();
            }

            FreecamManager.tick(client);
            VeloWorldFeatures.tick(client);

            if (toggleSprint && !FreecamManager.isEnabled() && client.player != null && client.options.forwardKey.isPressed()) {
                client.player.setSprinting(true);
            }
        });

        WorldRenderEvents.END_MAIN.register(VeloWorldFeatures::renderWorld);

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "velo_hud"), (context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            VeloWorldFeatures.renderThroughWallGlow(context, mc);
            renderVeloHud(context);
            renderTotemWarning(context, mc);
            VeloWorldFeatures.renderTopPopup(context, mc);
        });
    }

    private void renderTotemWarning(DrawContext context, MinecraftClient client) {
        if (!totemWarning || client.player == null || client.options.hudHidden) {
            return;
        }

        // Om spelaren redan har totem i offhand -> ingen varning
        if (client.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            return;
        }

        // Räkna hur många totems som ligger i inventoryt
        int totemCount = 0;
        int invSize = client.player.getInventory().size();
        for (int i = 0; i < invSize; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) {
                totemCount += stack.getCount();
            }
        }

        if (totemCount <= 0) {
            return;
        }

        // Mjuk pulserande modern varnings-pill
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

    private void renderVeloHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hudEnabled || client.player == null || client.options.hudHidden) {
            return;
        }

        int accent = getAccentColor();
        int x = 10;
        int y = 10;
        int width = 152;
        int lineHeight = 12;

        int lines = 1;
        if (showFps) lines++;
        if (showCoords) lines++;
        if (toggleSprint) lines++;
        if (FreecamManager.isEnabled()) lines++;
        if (VeloWorldFeatures.blockEspEnabled) lines++;
        if (VeloWorldFeatures.baseFinderEnabled) lines++;
        if (VeloWorldFeatures.spawnerFinderEnabled) lines++;

        int height = 16 + (lines * lineHeight);

        VeloUiRenderer.drawSmoothPanel(context, x, y, width, height, 9, COLOR_BG, (0x88 << 24) | (accent & 0x00FFFFFF), true);
        // Clean vertikal accent-linje på vänster sida
        VeloUiRenderer.drawSmoothRect(context, x + 5, y + 8, 2, height - 16, 1, accent);

        int tx = x + 13;
        int textY = y + 8;
        context.drawTextWithShadow(client.textRenderer, "§lVELO §r§7CLIENT", tx, textY, accent);
        textY += lineHeight + 2;

        if (showFps) {
            context.drawTextWithShadow(client.textRenderer, "§7FPS  §f" + client.getCurrentFps(), tx, textY, COLOR_TEXT);
            textY += lineHeight;
        }

        if (showCoords) {
            int cx = FreecamManager.isEnabled() ? (int) FreecamManager.camX : client.player.getBlockX();
            int cy = FreecamManager.isEnabled() ? (int) FreecamManager.camY : client.player.getBlockY();
            int cz = FreecamManager.isEnabled() ? (int) FreecamManager.camZ : client.player.getBlockZ();
            context.drawTextWithShadow(client.textRenderer, String.format("§7XYZ  §f%d, %d, %d", cx, cy, cz), tx, textY, COLOR_TEXT);
            textY += lineHeight;
        }

        if (toggleSprint) {
            context.drawTextWithShadow(client.textRenderer, "§7Sprint  §bPÅ", tx, textY, accent);
            textY += lineHeight;
        }

        if (FreecamManager.isEnabled()) {
            context.drawTextWithShadow(client.textRenderer, "§7Freecam  §bAKTIV", tx, textY, 0xFF60A5FA);
            textY += lineHeight;
        }

        if (VeloWorldFeatures.blockEspEnabled) {
            context.drawTextWithShadow(client.textRenderer, "§7Block ESP  §b" + VeloWorldFeatures.selectedEspBlocks.size() + " valda", tx, textY, accent);
            textY += lineHeight;
        }

        if (VeloWorldFeatures.baseFinderEnabled) {
            context.drawTextWithShadow(client.textRenderer, "§7Base Finder  §6PÅ", tx, textY, 0xFFFFB020);
            textY += lineHeight;
        }

        if (VeloWorldFeatures.spawnerFinderEnabled) {
            context.drawTextWithShadow(client.textRenderer, "§7Spawner  §d" + VeloWorldFeatures.getSelectedSpawnerLabel(), tx, textY, 0xFFE040FB);
        }
    }
}
