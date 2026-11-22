package com.velo.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class VeloClient implements ClientModInitializer {
    public static final String MOD_ID = "velocli";

    public static boolean hudEnabled = true;
    public static boolean showFps = true;
    public static boolean showCoords = true;
    public static boolean toggleSprint = true;

    public static final int COLOR_BG = 0xCC0A1628;
    public static final int COLOR_BORDER = 0xFF2563EB;
    public static final int COLOR_ACCENT = 0xFF38BDF8;
    public static final int COLOR_TEXT = 0xFFE0F2FE;

    private static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        FavoriteServerManager.load();

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.velocli.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                client.setScreen(new VeloMenuScreen(client.currentScreen));
            }

            if (toggleSprint && client.player != null && client.options.forwardKey.isPressed()) {
                client.player.setSprinting(true);
            }
        });

        HudElementRegistry.addLast(Identifier.of(MOD_ID, "velo_hud"), (context, tickCounter) -> {
            renderVeloHud(context);
        });
    }

    private void renderVeloHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!hudEnabled || client.player == null || client.options.hudHidden) {
            return;
        }

        int x = 10;
        int y = 10;
        int width = 148;
        int lineHeight = 12;

        int lines = 1;
        if (showFps) lines++;
        if (showCoords) lines++;
        if (toggleSprint) lines++;

        int height = 10 + (lines * lineHeight);

        context.fill(x, y, x + width, y + height, COLOR_BG);
        context.fill(x, y, x + width, y + 2, COLOR_ACCENT);
        context.fill(x, y + height - 1, x + width, y + height, COLOR_BORDER);
        context.fill(x, y, x + 2, y + height, COLOR_ACCENT);

        int textY = y + 6;
        context.drawTextWithShadow(client.textRenderer, "§lVELO §r§bCLIENT", x + 8, textY, COLOR_ACCENT);
        textY += lineHeight;

        if (showFps) {
            String fpsText = "FPS: " + client.getCurrentFps();
            context.drawTextWithShadow(client.textRenderer, fpsText, x + 8, textY, COLOR_TEXT);
            textY += lineHeight;
        }

        if (showCoords) {
            String coords = String.format("XYZ: %d, %d, %d",
                    client.player.getBlockX(),
                    client.player.getBlockY(),
                    client.player.getBlockZ());
            context.drawTextWithShadow(client.textRenderer, coords, x + 8, textY, COLOR_TEXT);
            textY += lineHeight;
        }

        if (toggleSprint) {
            context.drawTextWithShadow(client.textRenderer, "Sprint: [AKTIV]", x + 8, textY, COLOR_ACCENT);
        }
    }
}
