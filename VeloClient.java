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
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VeloClient implements ClientModInitializer {
    public static final String MOD_ID = "velocli";

    // HUD & Movement inställningar
    public static boolean hudEnabled = true;
    public static boolean showFps = true;
    public static boolean showCoords = true;
    public static boolean toggleSprint = true;

    // Slim Hand (Viewmodel)
    public static boolean slimHand = true;
    public static float handScale = 0.65f;
    public static float handOffsetX = 0.05f;
    public static float handOffsetY = -0.04f;

    // Blått färgtema (0 = Electric Blue, 1 = Ice Cyan, 2 = Royal Cobalt)
    public static int blueThemeIndex = 0;

    public static final int COLOR_BG = 0xD90A1224;
    public static final int COLOR_BORDER = 0xFF2563EB;
    public static final int COLOR_ACCENT = 0xFF38BDF8;
    public static final int COLOR_TEXT = 0xFFE0F2FE;

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
            renderVeloHud(context);
        });
    }

    private void renderVeloHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hudEnabled || client.player == null || client.options.hudHidden) {
            return;
        }

        int accent = getAccentColor();

        int x = 10;
        int y = 10;
        int width = 154;
        int lineHeight = 12;

        int lines = 1;
        if (showFps) lines++;
        if (showCoords) lines++;
        if (toggleSprint) lines++;
        if (FreecamManager.isEnabled()) lines++;
        if (VeloWorldFeatures.blockEspEnabled) lines++;
        if (VeloWorldFeatures.baseFinderEnabled) lines++;
        if (VeloWorldFeatures.spawnerFinderEnabled) lines++;

        int height = 12 + (lines * lineHeight);

        VeloUiRenderer.drawRoundedBorder(context, x, y, width, height, 6, accent, COLOR_BG);

        int textY = y + 7;
        context.drawTextWithShadow(client.textRenderer, "§lVELO §r§bCLIENT", x + 8, textY, accent);
        textY += lineHeight;

        if (showFps) {
            context.drawTextWithShadow(client.textRenderer, "FPS: " + client.getCurrentFps(), x + 8, textY, COLOR_TEXT);
            textY += lineHeight;
        }

        if (showCoords) {
            int cx = FreecamManager.isEnabled() ? (int) FreecamManager.camX : client.player.getBlockX();
            int cy = FreecamManager.isEnabled() ? (int) FreecamManager.camY : client.player.getBlockY();
            int cz = FreecamManager.isEnabled() ? (int) FreecamManager.camZ : client.player.getBlockZ();
            context.drawTextWithShadow(client.textRenderer, String.format("XYZ: %d, %d, %d", cx, cy, cz), x + 8, textY, COLOR_TEXT);
            textY += lineHeight;
        }

        if (toggleSprint) {
            context.drawTextWithShadow(client.textRenderer, "Sprint: [AKTIV]", x + 8, textY, accent);
            textY += lineHeight;
        }

        if (FreecamManager.isEnabled()) {
            context.drawTextWithShadow(client.textRenderer, "Freecam: [AKTIV]", x + 8, textY, 0xFF60A5FA);
            textY += lineHeight;
        }

        if (VeloWorldFeatures.blockEspEnabled) {
            context.drawTextWithShadow(client.textRenderer, "ESP: " + VeloWorldFeatures.getSelectedEspBlockName(), x + 8, textY, accent);
            textY += lineHeight;
        }

        if (VeloWorldFeatures.baseFinderEnabled) {
            context.drawTextWithShadow(client.textRenderer, "Base Finder: [PÅ]", x + 8, textY, 0xFFFFA500);
            textY += lineHeight;
        }

        if (VeloWorldFeatures.spawnerFinderEnabled) {
            context.drawTextWithShadow(client.textRenderer, "Spawner: " + VeloWorldFeatures.getSelectedSpawnerLabel(), x + 8, textY, 0xFFD946EF);
        }

        // Visa senaste Base/Spawner-varning i 8 sekunder som en clean notis längst upp i mitten
        if (!VeloWorldFeatures.lastAlertMessage.isEmpty()
                && System.currentTimeMillis() - VeloWorldFeatures.lastAlertTime < 8000) {
            int msgW = client.textRenderer.getWidth(VeloWorldFeatures.lastAlertMessage);
            int boxW = msgW + 20;
            int boxH = 20;
            int boxX = (client.getWindow().getScaledWidth() - boxW) / 2;
            int boxY = 10;
            VeloUiRenderer.drawRoundedBorder(context, boxX, boxY, boxW, boxH, 6, accent, 0xE00A1224);
            context.drawTextWithShadow(client.textRenderer, VeloWorldFeatures.lastAlertMessage, boxX + 10, boxY + 6, 0xFFFFFFFF);
        }
    }
}
