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

/**
 * Luftig blå VELO CLIENT-startskärm med anti-aliased paneler, spelarbadge,
 * favoritservrar och en mjukt snurrande 3D-gubbe i full FPS.
 */
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
    private boolean serversExpanded = true;
    private boolean addingServer = false;
    private TextFieldWidget nameField;
    private TextFieldWidget ipField;

    private float smoothRotation = 0.0f;
    private long lastFrameNanos = System.nanoTime();

    public VeloTitleScreen() {
        super(Text.literal("VELO Client"));
    }

    @Override
    protected void init() {
        this.clearChildren();
        cleanButtons.clear();
        FavoriteServerManager.load();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int btnW = 216;
        int btnH = 28;
        int bx = cx - btnW / 2;

        // Luftiga mittknappar (Feather-inspirerad layout i VELO-blått)
        cleanButtons.add(new CleanButton(bx, cy - 38, btnW, btnH, "SINGLEPLAYER", true, () -> {
            if (this.client != null) this.client.setScreen(new SelectWorldScreen(this));
        }));
        cleanButtons.add(new CleanButton(bx, cy - 2, btnW, btnH, "MULTIPLAYER", true, () -> {
            if (this.client != null) this.client.setScreen(new MultiplayerScreen(this));
        }));

        int subW = (btnW - 12) / 3;
        cleanButtons.add(new CleanButton(bx, cy + 34, subW, 24, "VELO", false, () -> {
            if (this.client != null) this.client.setScreen(new VeloMenuScreen(this));
        }));
        cleanButtons.add(new CleanButton(bx + subW + 6, cy + 34, subW, 24, "OPTIONS", false, () -> {
            if (this.client != null) this.client.setScreen(new OptionsScreen(this, this.client.options));
        }));
        cleanButtons.add(new CleanButton(bx + (subW + 6) * 2, cy + 34, subW, 24, "MODS", false, this::openModMenu));

        cleanButtons.add(new CleanButton(bx, cy + 66, btnW, 22, "QUIT GAME", false, () -> {
            if (this.client != null) this.client.scheduleStop();
        }));

        // Hopfällbar favoritserver-panel uppe till höger
        int panelW = 196;
        int panelX = this.width - panelW - 18;
        int panelY = 18;

        if (serversExpanded) {
            cleanButtons.add(new CleanButton(panelX + 10, panelY + 30, panelW - 20, 20,
                    addingServer ? "Avbryt" : "+ Lägg till server", true, () -> {
                addingServer = !addingServer;
                this.init();
            }));

            int listStartY = panelY + 56;
            if (addingServer) {
                nameField = new TextFieldWidget(this.textRenderer, panelX + 10, listStartY, panelW - 20, 18, Text.literal("Servernamn"));
                nameField.setPlaceholder(Text.literal("Servernamn..."));
                this.addDrawableChild(nameField);

                ipField = new TextFieldWidget(this.textRenderer, panelX + 10, listStartY + 24, panelW - 20, 18, Text.literal("Server-IP"));
                ipField.setPlaceholder(Text.literal("IP (t.ex. mc.hypixel.net)"));
                this.addDrawableChild(ipField);

                cleanButtons.add(new CleanButton(panelX + 10, listStartY + 48, panelW - 20, 22, "Spara server", true, () -> {
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
                int maxShown = Math.min(servers.size(), 4);
                for (int i = 0; i < maxShown; i++) {
                    final int index = i;
                    FavoriteServerManager.ServerEntry entry = servers.get(i);
                    int rowY = listStartY + (i * 24);
                    cleanButtons.add(new CleanButton(panelX + 10, rowY, panelW - 44, 20, "▶ " + entry.name, false,
                            () -> connectToServer(entry)));
                    cleanButtons.add(new CleanButton(panelX + panelW - 30, rowY, 20, 20, "✕", false, () -> {
                        FavoriteServerManager.removeServer(index);
                        this.init();
                    }));
                }
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
        context.fillGradient(0, 0, this.width, this.height, 0xFF040A18, 0xFF0D2147);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;
        smoothRotation = (smoothRotation + dt * 45.0f) % 360.0f;

        int cx = this.width / 2;
        int cy = this.height / 2;
        int accent = VeloClient.getAccentColor();
        int primary = VeloClient.getPrimaryBlue();

        // Spelarbadge uppe till vänster
        MinecraftClient mc = MinecraftClient.getInstance();
        String playerName = mc.getSession().getUsername();
        VeloUiRenderer.drawSmoothPanel(context, 18, 18, 142, 30, 0xCC0A152C, 0xFF1E3A6E);
        context.drawTextWithShadow(this.textRenderer, "§b● §f§l" + playerName, 28, 29, 0xFFFFFFFF);

        // Centrerad VELO CLIENT-rubrik
        context.drawCenteredTextWithShadow(this.textRenderer, "§b§lVELO CLIENT", cx, cy - 82, accent);
        context.drawCenteredTextWithShadow(this.textRenderer, "§7v1.8.0  •  Clean Edition", cx, cy - 68, 0xFF94A3B8);

        // Favoritserver-panel uppe till höger
        int panelW = 196;
        int panelX = this.width - panelW - 18;
        int panelY = 18;
        int panelH = serversExpanded ? 160 : 28;
        VeloUiRenderer.drawSmoothPanel(context, panelX, panelY, panelW, panelH, 0xCC0A152C, 0xFF1E3A6E);
        context.drawTextWithShadow(this.textRenderer, "§b§lFAVORITSERVRAR " + (serversExpanded ? "▾" : "▸"),
                panelX + 12, panelY + 10, accent);

        // Mjukt snurrande 3D-gubbe på vänster sida med spelarens riktiga skin
        int avatarX = Math.max(72, cx - 180);
        int avatarY = cy + 6;
        renderSmoothRotatingPlayer(context, avatarX, avatarY, playerName, smoothRotation);

        // Rita alla anti-aliased knappar med smooth hover
        for (CleanButton btn : cleanButtons) {
            boolean hovered = btn.isHovered(mouseX, mouseY);
            btn.hoverAnim += ((hovered ? 1.0f : 0.0f) - btn.hoverAnim) * Math.min(1.0f, dt * 14.0f);

            int baseFill = btn.accentStyle ? 0xDD112447 : 0xCC0B162E;
            int hoverFill = btn.accentStyle ? primary : 0xFF1A325C;
            int fill = VeloUiRenderer.lerpColor(baseFill, hoverFill, btn.hoverAnim);
            int border = VeloUiRenderer.lerpColor(0xFF1E3A6E, accent, btn.hoverAnim);

            VeloUiRenderer.drawSmoothPanel(context, btn.x, btn.y, btn.w, btn.h, fill, border);
            int textColor = VeloUiRenderer.lerpColor(0xFFE2E8F0, 0xFFFFFFFF, btn.hoverAnim);
            context.drawCenteredTextWithShadow(this.textRenderer, btn.label, btn.x + btn.w / 2, btn.y + (btn.h - 8) / 2, textColor);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderSmoothRotatingPlayer(DrawContext context, int centerX, int centerY, String playerName, float angleDeg) {
        int[][] skin = PlayerSkinCache.getSkinPixels(playerName);
        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        boolean showFront = cos >= 0.0f;
        float facingWidth = Math.max(0.18f, Math.abs(cos));
        float sideDepth = Math.abs(sin) * 0.45f;
        float shade = 0.72f + 0.28f * Math.abs(cos);

        float bob = (float) Math.sin(System.nanoTime() / 420_000_000.0) * 2.5f;
        int scale = 4;

        // Mjuk glödande platta under gubben
        VeloUiRenderer.drawSmoothPanel(context, centerX - 34, centerY + 62, 68, 12, 0x552563EB, 0x9938BDF8);

        int headY = (int) (centerY - 58 + bob);
        int headW = Math.max(6, Math.round(8 * scale * facingWidth));
        int headSrcX = showFront ? 8 : 24;
        int headOverlayX = showFront ? 40 : 56;
        drawProjectedSkin(context, skin, headSrcX, 8, 8, 8, centerX - headW / 2, headY, headW, 8 * scale, shade, false);
        drawProjectedSkin(context, skin, headOverlayX, 8, 8, 8, centerX - headW / 2, headY, headW, 8 * scale, shade, true);

        int bodyY = headY + 8 * scale + 1;
        int bodyW = Math.max(6, Math.round(8 * scale * facingWidth));
        int bodySrcX = showFront ? 20 : 32;
        drawProjectedSkin(context, skin, bodySrcX, 20, 8, 12, centerX - bodyW / 2, bodyY, bodyW, 12 * scale, shade, false);

        // Armar med mjuk 3D-skiftning beroende på rotation
        int armW = Math.max(4, Math.round(4 * scale * Math.max(0.35f, facingWidth + sideDepth * 0.3f)));
        int armOffset = bodyW / 2 + 1;
        drawProjectedSkin(context, skin, 44, 20, 4, 12, centerX - armOffset - armW, bodyY, armW, 12 * scale, shade * 0.92f, false);
        boolean is64 = skin[0].length >= 64;
        drawProjectedSkin(context, skin, is64 ? 36 : 44, is64 ? 52 : 20, 4, 12, centerX + armOffset, bodyY, armW, 12 * scale, shade * 0.92f, false);

        // Ben
        int legsY = bodyY + 12 * scale + 1;
        int legW = Math.max(3, bodyW / 2);
        drawProjectedSkin(context, skin, 4, 20, 4, 12, centerX - legW, legsY, legW, 12 * scale, shade * 0.95f, false);
        drawProjectedSkin(context, skin, is64 ? 20 : 4, is64 ? 52 : 20, 4, 12, centerX, legsY, legW, 12 * scale, shade * 0.95f, false);
    }

    private void drawProjectedSkin(DrawContext context, int[][] skin, int srcX, int srcY, int sw, int sh,
                                   int dstX, int dstY, int dstW, int dstH, float shade, boolean overlay) {
        if (skin == null || skin.length <= srcX + sw || skin[0].length <= srcY + sh) return;
        for (int x = 0; x < sw; x++) {
            int x0 = dstX + (x * dstW) / sw;
            int x1 = dstX + ((x + 1) * dstW) / sw;
            if (x1 <= x0) continue;
            for (int y = 0; y < sh; y++) {
                int argb = skin[srcX + x][srcY + y];
                int alpha = (argb >>> 24) & 0xFF;
                if (overlay && alpha < 128) continue;
                if (!overlay && alpha == 0) continue;

                int r = Math.min(255, (int) (((argb >>> 16) & 0xFF) * shade));
                int g = Math.min(255, (int) (((argb >>> 8) & 0xFF) * shade));
                int b = Math.min(255, (int) ((argb & 0xFF) * shade));
                int shaded = (0xFF << 24) | (r << 16) | (g << 8) | b;

                int y0 = dstY + (y * dstH) / sh;
                int y1 = dstY + ((y + 1) * dstH) / sh;
                if (y1 > y0) {
                    context.fill(x0, y0, x1, y1, shaded);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            int panelW = 196;
            int panelX = this.width - panelW - 18;
            if (mx >= panelX && mx <= panelX + panelW && my >= 18 && my <= 46) {
                serversExpanded = !serversExpanded;
                this.init();
                return true;
            }

            for (CleanButton btn : cleanButtons) {
                if (btn.isHovered(mx, my)) {
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
