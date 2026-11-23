package com.velo.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

public class VeloTitleScreen extends Screen {

    private static class CleanButton {
        int x, y, w, h;
        String label;
        Runnable action;
        float hoverAnim = 0f;
        boolean accentStyle;

        CleanButton(int x, int y, int w, int h, String label, boolean accentStyle, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.accentStyle = accentStyle;
            this.action = action;
        }

        boolean isHovered(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private final List<CleanButton> cleanButtons = new ArrayList<>();
    private boolean addingServer = false;
    private TextFieldWidget nameField;
    private TextFieldWidget ipField;

    public VeloTitleScreen() {
        super(Text.literal("VELO Client"));
    }

    @Override
    protected void init() {
        this.clearChildren();
        cleanButtons.clear();
        FavoriteServerManager.load();

        int centerY = this.height / 2;
        int leftX = 36;
        int btnW = 165;
        int btnH = 26;
        int startY = centerY - 78;
        int gap = 32;

        cleanButtons.add(new CleanButton(leftX, startY, btnW, btnH, "Singleplayer", true, () -> {
            if (this.client != null) this.client.setScreen(new SelectWorldScreen(this));
        }));
        cleanButtons.add(new CleanButton(leftX, startY + gap, btnW, btnH, "Multiplayer", true, () -> {
            if (this.client != null) this.client.setScreen(new MultiplayerScreen(this));
        }));
        cleanButtons.add(new CleanButton(leftX, startY + gap * 2, btnW, btnH, "VELO Inställningar", false, () -> {
            if (this.client != null) this.client.setScreen(new VeloMenuScreen(this));
        }));
        cleanButtons.add(new CleanButton(leftX, startY + gap * 3, btnW, btnH, "Mods", false, this::openModMenu));
        cleanButtons.add(new CleanButton(leftX, startY + gap * 4, btnW, btnH, "Minecraft Options", false, () -> {
            if (this.client != null) this.client.setScreen(new OptionsScreen(this, this.client.options));
        }));
        cleanButtons.add(new CleanButton(leftX, startY + gap * 5, btnW, btnH, "Avsluta spel", false, () -> {
            if (this.client != null) this.client.scheduleStop();
        }));

        // Höger panel: Favoritservrar
        int panelW = 210;
        int panelX = this.width - panelW - 32;
        int panelY = centerY - 82;

        cleanButtons.add(new CleanButton(panelX + 12, panelY + 28, panelW - 24, 22,
                addingServer ? "Avbryt" : "+ Lägg till server", true, () -> {
            addingServer = !addingServer;
            this.init();
        }));

        int listStartY = panelY + 58;
        if (addingServer) {
            nameField = new TextFieldWidget(this.textRenderer, panelX + 12, listStartY, panelW - 24, 20, Text.literal("Servernamn"));
            nameField.setPlaceholder(Text.literal("Servernamn..."));
            this.addDrawableChild(nameField);

            ipField = new TextFieldWidget(this.textRenderer, panelX + 12, listStartY + 26, panelW - 24, 20, Text.literal("Server-IP"));
            ipField.setPlaceholder(Text.literal("IP (t.ex. mc.hypixel.net)"));
            this.addDrawableChild(ipField);

            cleanButtons.add(new CleanButton(panelX + 12, listStartY + 54, panelW - 24, 24, "Spara server", true, () -> {
                String name = nameField.getText();
                String ip = ipField.getText();
                if (!ip.isBlank()) {
                    FavoriteServerManager.addServer(name, ip);
                    addingServer = false;
                    this.init();
                }
            }));
        } else {
            List<FavoriteServerManager.ServerEntry> servers = FavoriteServerManager.getServers();
            int maxShown = Math.min(servers.size(), 5);
            for (int i = 0; i < maxShown; i++) {
                final int index = i;
                FavoriteServerManager.ServerEntry entry = servers.get(i);
                int rowY = listStartY + (i * 28);

                cleanButtons.add(new CleanButton(panelX + 12, rowY, panelW - 50, 22, "▶  " + entry.name, false,
                        () -> connectToServer(entry)));
                cleanButtons.add(new CleanButton(panelX + panelW - 34, rowY, 22, 22, "✕", false, () -> {
                    FavoriteServerManager.removeServer(index);
                    this.init();
                }));
            }
        }
    }

    private void openModMenu() {
        if (this.client == null) return;
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            try {
                Class<?> modsScreenClass = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                Constructor<?> ctor = modsScreenClass.getConstructor(Screen.class);
                this.client.setScreen((Screen) ctor.newInstance(this));
                return;
            } catch (Throwable ignored) {
            }
        }
        this.client.setScreen(new VeloMenuScreen(this));
    }

    private void connectToServer(FavoriteServerManager.ServerEntry entry) {
        if (this.client == null) return;
        ServerInfo info = new ServerInfo(entry.name, entry.address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, this.client, ServerAddress.parse(entry.address), info, false, null);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xFF040914, 0xFF0C1E3C);
        context.fill(0, 0, this.width, 2, VeloClient.getAccentColor());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int accent = VeloClient.getAccentColor();
        int primary = VeloClient.getPrimaryBlue();

        // Logotyp uppe till vänster
        context.drawTextWithShadow(this.textRenderer, "§lVELO §r§bCLIENT", 36, 24, accent);
        context.drawTextWithShadow(this.textRenderer, "§7v1.7.0  •  Clean Edition", 36, 36, 0xFF94A3B8);

        // Höger server-panel (ritas innan knapparna så knapparna syns ovanpå)
        int panelW = 210;
        int panelH = 204;
        int panelX = this.width - panelW - 32;
        int panelY = centerY - 82;
        VeloUiRenderer.drawRoundedBorder(context, panelX, panelY, panelW, panelH, 8, 0xFF1E3256, 0xCC0A1326);
        context.drawTextWithShadow(this.textRenderer, "§lFAVORITSERVRAR", panelX + 14, panelY + 12, accent);

        // Mitten: Spelarkort
        renderPlayerCard(context, centerX, centerY);

        // Rita alla mjuka rundade knappar
        for (CleanButton btn : cleanButtons) {
            boolean hovered = btn.isHovered(mouseX, mouseY);
            btn.hoverAnim += ((hovered ? 1.0f : 0.0f) - btn.hoverAnim) * 0.25f;

            int baseFill = btn.accentStyle ? 0xFF112240 : 0xCC0D182E;
            int hoverFill = btn.accentStyle ? primary : 0xFF1A2D4F;
            int fill = VeloUiRenderer.lerpColor(baseFill, hoverFill, btn.hoverAnim);
            int border = VeloUiRenderer.lerpColor(0xFF1E3256, accent, btn.hoverAnim);

            VeloUiRenderer.drawRoundedBorder(context, btn.x, btn.y, btn.w, btn.h, 6, border, fill);
            int textColor = VeloUiRenderer.lerpColor(0xFFE2E8F0, 0xFFFFFFFF, btn.hoverAnim);
            context.drawCenteredTextWithShadow(this.textRenderer, btn.label, btn.x + btn.w / 2, btn.y + (btn.h - 8) / 2, textColor);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPlayerCard(DrawContext context, int centerX, int centerY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String playerName = mc.getSession().getUsername();
        int[][] skin = PlayerSkinCache.getSkinPixels(playerName);

        int cardW = 128;
        int cardH = 190;
        int cardX = centerX - cardW / 2 - 8;
        int cardY = centerY - 76;

        VeloUiRenderer.drawRoundedBorder(context, cardX, cardY, cardW, cardH, 8, 0xFF1E3256, 0xCC0A1326);

        int bob = (int) Math.round(Math.sin(System.currentTimeMillis() / 380.0) * 2.0);
        int scale = 4;
        int charCenterX = cardX + cardW / 2;
        int headX = charCenterX - (4 * scale);
        int headY = cardY + 18 + bob;

        drawSkinRegion(context, skin, 8, 8, 8, 8, headX, headY, scale, false);
        drawSkinRegion(context, skin, 40, 8, 8, 8, headX, headY, scale, true);

        int bodyY = headY + (8 * scale) + 1;
        int torsoX = charCenterX - (4 * scale);
        drawSkinRegion(context, skin, 20, 20, 8, 12, torsoX, bodyY, scale, false);
        drawSkinRegion(context, skin, 20, 36, 8, 12, torsoX, bodyY, scale, true);
        drawSkinRegion(context, skin, 44, 20, 4, 12, torsoX - (4 * scale) - 1, bodyY, scale, false);

        boolean is64x64 = skin[0].length >= 64;
        drawSkinRegion(context, skin, is64x64 ? 36 : 44, is64x64 ? 52 : 20, 4, 12, torsoX + (8 * scale) + 1, bodyY, scale, false);

        int legsY = bodyY + (12 * scale) + 1;
        drawSkinRegion(context, skin, 4, 20, 4, 12, torsoX, legsY, scale, false);
        drawSkinRegion(context, skin, is64x64 ? 20 : 4, is64x64 ? 52 : 20, 4, 12, torsoX + (4 * scale), legsY, scale, false);

        context.drawCenteredTextWithShadow(this.textRenderer, "§f§l" + playerName, charCenterX, cardY + cardH - 28, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "§b● Online", charCenterX, cardY + cardH - 16, VeloClient.getAccentColor());
    }

    private void drawSkinRegion(DrawContext context, int[][] skin, int srcX, int srcY, int w, int h, int dstX, int dstY, int scale, boolean overlay) {
        if (skin == null || skin.length <= srcX + w || skin[0].length <= srcY + h) return;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = skin[srcX + x][srcY + y];
                int alpha = (argb >>> 24) & 0xFF;
                if (overlay && alpha < 128) continue;
                if (!overlay && alpha == 0) continue;
                context.fill(dstX + x * scale, dstY + y * scale, dstX + (x + 1) * scale, dstY + (y + 1) * scale, (0xFF << 24) | (argb & 0x00FFFFFF));
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            for (CleanButton btn : cleanButtons) {
                if (btn.isHovered(click.x(), click.y())) {
                    btn.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
