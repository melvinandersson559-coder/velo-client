package com.velo.client;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * VELO 4.0 Startskärm — Skarpa 90°-kanter, marinblå & skiffergrå arkitektur:
 * - Djup skiffergrå/marinblå bakgrund med subtilt arkitektoniskt rutnät
 * - Skarp geometrisk VELO-logga + VELO CLIENT-rubrik
 * - Skarpa menyknappar med glidande marinblå vänsterindikator vid hover
 * - 3D-spelarkaraktär på skarp skifferplatta med hörnhakar + hopfällbar servermeny
 */
public class VeloTitleScreen extends Screen {

    private static class SharpNavButton {
        int x, y, w, h;
        String label;
        Runnable action;
        float hoverAnim = 0f;

        SharpNavButton(int x, int y, int w, int h, String label, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.action = action;
        }

        boolean isHovered(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static class BodyBox {
        float cx, cy, cz;
        float w, h, d;
        int frontU, frontV, backU, backV, rightU, rightV, leftU, leftV;
        int ovFrontU, ovFrontV, ovBackU, ovBackV, ovRightU, ovRightV, ovLeftU, ovLeftV;
        boolean hasOverlay;
        float depthScore;

        BodyBox(float cx, float cy, float cz, float w, float h, float d,
                int frontU, int frontV, int backU, int backV, int rightU, int rightV, int leftU, int leftV) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.w = w;
            this.h = h;
            this.d = d;
            this.frontU = frontU;
            this.frontV = frontV;
            this.backU = backU;
            this.backV = backV;
            this.rightU = rightU;
            this.rightV = rightV;
            this.leftU = leftU;
            this.leftV = leftV;
        }

        BodyBox withOverlay(int fU, int fV, int bU, int bV, int rU, int rV, int lU, int lV) {
            this.hasOverlay = true;
            this.ovFrontU = fU;
            this.ovFrontV = fV;
            this.ovBackU = bU;
            this.ovBackV = bV;
            this.ovRightU = rU;
            this.ovRightV = rV;
            this.ovLeftU = lU;
            this.ovLeftV = lV;
            return this;
        }
    }

    private final List<SharpNavButton> navButtons = new ArrayList<>();
    private final List<SharpNavButton> serverButtons = new ArrayList<>();

    private boolean serversOpen = false;
    private boolean addingServer = false;
    private TextFieldWidget nameField;
    private TextFieldWidget ipField;

    private float smoothRotation = -24.0f;
    private long lastFrameNanos = System.nanoTime();

    public VeloTitleScreen() {
        super(Text.literal("VELO Client"));
    }

    @Override
    protected void init() {
        this.clearChildren();
        navButtons.clear();
        serverButtons.clear();
        FavoriteServerManager.load();

        int leftCenterX = (int) (this.width * 0.36f);
        int centerY = this.height / 2;

        int btnW = 196;
        int btnH = 26;
        int gap = 6;
        int startX = leftCenterX - btnW / 2;
        int startY = centerY - 22;

        navButtons.add(new SharpNavButton(startX, startY, btnW, btnH, "SINGLEPLAYER", () -> {
            if (this.client != null) this.client.setScreen(new SelectWorldScreen(this));
        }));
        navButtons.add(new SharpNavButton(startX, startY + (btnH + gap), btnW, btnH, "MULTIPLAYER", () -> {
            if (this.client != null) this.client.setScreen(new MultiplayerScreen(this));
        }));
        navButtons.add(new SharpNavButton(startX, startY + (btnH + gap) * 2, btnW, btnH, "CLIENT MENU", () -> {
            if (this.client != null) this.client.setScreen(new VeloMenuScreen(this));
        }));
        navButtons.add(new SharpNavButton(startX, startY + (btnH + gap) * 3, btnW, btnH, "MINECRAFT SETTINGS", () -> {
            if (this.client != null) this.client.setScreen(new OptionsScreen(this, this.client.options));
        }));
        navButtons.add(new SharpNavButton(startX, startY + (btnH + gap) * 4, btnW, btnH, "QUIT GAME", () -> {
            if (this.client != null) this.client.scheduleStop();
        }));

        if (serversOpen) {
            int panelW = 186;
            int panelX = this.width - panelW - 18;
            int panelY = 44;

            serverButtons.add(new SharpNavButton(panelX + 8, panelY + 8, panelW - 16, 20,
                    addingServer ? "CANCEL" : "+ ADD SERVER", () -> {
                addingServer = !addingServer;
                this.init();
            }));

            int listY = panelY + 34;
            if (addingServer) {
                nameField = new TextFieldWidget(this.textRenderer, panelX + 8, listY, panelW - 16, 16, Text.literal("Name"));
                nameField.setPlaceholder(Text.literal("Server Name..."));
                this.addDrawableChild(nameField);

                ipField = new TextFieldWidget(this.textRenderer, panelX + 8, listY + 22, panelW - 16, 16, Text.literal("IP"));
                ipField.setPlaceholder(Text.literal("Server Address..."));
                this.addDrawableChild(ipField);

                serverButtons.add(new SharpNavButton(panelX + 8, listY + 44, panelW - 16, 20, "SAVE SERVER", () -> {
                    if (!ipField.getText().isBlank()) {
                        FavoriteServerManager.addServer(nameField.getText(), ipField.getText());
                        addingServer = false;
                        this.init();
                    }
                }));
            } else {
                List<FavoriteServerManager.ServerEntry> servers = FavoriteServerManager.getServers();
                int maxShown = Math.min(servers.size(), 4);
                for (int i = 0; i < maxShown; i++) {
                    final int idx = i;
                    FavoriteServerManager.ServerEntry entry = servers.get(i);
                    int ry = listY + i * 24;
                    serverButtons.add(new SharpNavButton(panelX + 8, ry, panelW - 38, 20, entry.name, () -> connectToServer(entry)));
                    serverButtons.add(new SharpNavButton(panelX + panelW - 26, ry, 18, 20, "✕", () -> {
                        FavoriteServerManager.removeServer(idx);
                        this.init();
                    }));
                }
            }
        }

        this.lastFrameNanos = System.nanoTime();
    }

    private void connectToServer(FavoriteServerManager.ServerEntry entry) {
        if (this.client == null) return;
        ServerInfo info = new ServerInfo(entry.name, entry.address, ServerInfo.ServerType.OTHER);
        ConnectScreen.connect(this, this.client, ServerAddress.parse(entry.address), info, false, null);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Djup skiffergrå & marinblå bakgrund
        context.fillGradient(0, 0, this.width, this.height, 0xFF0A0D13, 0xFF101624);

        // Diskret arkitektoniskt rutnät
        int gridCol = 0x1094A3B8;
        for (int x = 0; x < this.width; x += 48) {
            context.fill(x, 0, x + 1, this.height, gridCol);
        }
        for (int y = 0; y < this.height; y += 48) {
            context.fill(0, y, this.width, y + 1, gridCol);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;

        float fastSmooth = 1.0f - (float) Math.exp(-15.0f * dt);
        smoothRotation = (smoothRotation + dt * 26.0f) % 360.0f;

        int accent = VeloClient.getAccentColor();
        int primary = VeloClient.getPrimaryBlue();

        MinecraftClient mc = MinecraftClient.getInstance();
        String playerName = mc.getSession().getUsername();

        int leftCenterX = (int) (this.width * 0.36f);
        int centerY = this.height / 2;

        // Skarp geometrisk VELO-emblem + rubrik
        int logoSize = 26;
        int logoX = leftCenterX - logoSize / 2;
        int logoY = centerY - 82;
        VeloUiRenderer.drawSharpPanel(context, logoX, logoY, logoSize, logoSize, primary, accent, true);
        VeloUiRenderer.drawCornerTicks(context, logoX - 3, logoY - 3, logoSize + 6, logoSize + 6, 4, accent);
        context.drawCenteredTextWithShadow(this.textRenderer, "§lV", leftCenterX, logoY + 9, 0xFFF1F5F9);

        context.drawCenteredTextWithShadow(this.textRenderer, "§lVELO CLIENT", leftCenterX, centerY - 46, 0xFFF1F5F9);
        VeloUiRenderer.drawSharpRect(context, leftCenterX - 32, centerY - 34, 64, 1, accent);

        // Skarpa menyknappar med marinblå hover-indikator
        for (SharpNavButton btn : navButtons) {
            boolean hovered = btn.isHovered(mouseX, mouseY);
            btn.hoverAnim += ((hovered ? 1.0f : 0.0f) - btn.hoverAnim) * fastSmooth;

            int fill = VeloUiRenderer.lerpColor(0xDD111622, 0xEE162136, btn.hoverAnim);
            int border = VeloUiRenderer.lerpColor(0xFF222A3A, accent, btn.hoverAnim);
            VeloUiRenderer.drawSharpPanel(context, btn.x, btn.y, btn.w, btn.h, fill, border, false);

            if (btn.hoverAnim > 0.02f) {
                int barH = Math.max(2, Math.round(btn.h * btn.hoverAnim));
                int barY = btn.y + (btn.h - barH) / 2;
                VeloUiRenderer.drawSharpRect(context, btn.x, barY, 2, barH, accent);
            }

            int textCol = VeloUiRenderer.lerpColor(0xFF94A3B8, 0xFFF1F5F9, btn.hoverAnim);
            context.drawCenteredTextWithShadow(this.textRenderer, btn.label, btn.x + btn.w / 2, btn.y + (btn.h - 8) / 2, textCol);
        }

        // Höger sida: 3D-spelarkaraktär på skarp skifferplatta
        int avatarX = (int) (this.width * 0.70f);
        int avatarY = centerY + 18;
        int tagW = Math.max(96, this.textRenderer.getWidth(playerName) + 24);
        VeloUiRenderer.drawSharpPanel(context, avatarX - tagW / 2, avatarY - 82, tagW, 18, 0xDD111622, 0xFF222A3A, false);
        VeloUiRenderer.drawSharpRect(context, avatarX - tagW / 2, avatarY - 82, 2, 18, accent);
        context.drawCenteredTextWithShadow(this.textRenderer, playerName, avatarX, avatarY - 77, 0xFFF1F5F9);

        renderTrue3DPlayer(context, avatarX, avatarY, playerName, smoothRotation, accent);

        // Top-right Servers Drawer Button
        int srvBtnW = 92;
        int srvBtnX = this.width - srvBtnW - 18;
        int srvBtnY = 16;
        boolean srvHover = mouseX >= srvBtnX && mouseX <= srvBtnX + srvBtnW && mouseY >= srvBtnY && mouseY <= srvBtnY + 22;
        VeloUiRenderer.drawSharpPanel(context, srvBtnX, srvBtnY, srvBtnW, 22,
                srvHover ? primary : 0xDD111622, srvHover ? accent : 0xFF222A3A, false);
        context.drawCenteredTextWithShadow(this.textRenderer, "SERVERS " + (serversOpen ? "▴" : "▾"),
                srvBtnX + srvBtnW / 2, srvBtnY + 7, 0xFFF1F5F9);

        if (serversOpen) {
            int panelW = 186;
            int panelX = this.width - panelW - 18;
            int panelY = 44;
            int panelH = addingServer ? 108 : (42 + Math.min(FavoriteServerManager.getServers().size(), 4) * 24);
            VeloUiRenderer.drawSharpPanel(context, panelX, panelY, panelW, panelH, 0xF00E121A, 0xFF303B52, true);

            for (SharpNavButton sb : serverButtons) {
                boolean hov = sb.isHovered(mouseX, mouseY);
                sb.hoverAnim += ((hov ? 1.0f : 0.0f) - sb.hoverAnim) * fastSmooth;
                int fill = VeloUiRenderer.lerpColor(0xFF131823, primary, sb.hoverAnim);
                int border = VeloUiRenderer.lerpColor(0xFF222A3A, accent, sb.hoverAnim);
                VeloUiRenderer.drawSharpPanel(context, sb.x, sb.y, sb.w, sb.h, fill, border, false);
                context.drawCenteredTextWithShadow(this.textRenderer, sb.label, sb.x + sb.w / 2, sb.y + 6,
                        hov ? 0xFFF1F5F9 : 0xFF94A3B8);
            }
        }

        context.drawText(this.textRenderer, "VELO CLIENT  //  4.0", 16, this.height - 18, 0xFF475569, false);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderTrue3DPlayer(DrawContext ctx, int centerX, int centerY, String playerName, float angleDeg, int accent) {
        int[][] skin = PlayerSkinCache.getSkinPixels(playerName);
        boolean is64 = skin != null && skin[0].length >= 64;

        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float scale = 3.9f;

        // Skarp skifferplatta under fötterna med hörnhakar
        VeloUiRenderer.drawSharpPanel(ctx, centerX - 38, centerY + 60, 76, 8, 0xCC111622, 0xFF222A3A, false);
        VeloUiRenderer.drawCornerTicks(ctx, centerX - 40, centerY + 58, 80, 12, 4, accent);

        List<BodyBox> parts = new ArrayList<>();
        parts.add(new BodyBox(0, -12, 0, 8, 8, 8,
                8, 8, 24, 8, 0, 8, 16, 8)
                .withOverlay(40, 8, 56, 8, 32, 8, 48, 8));

        BodyBox torso = new BodyBox(0, -2, 0, 8, 12, 4,
                20, 20, 32, 20, 16, 20, 28, 20);
        if (is64) torso.withOverlay(20, 36, 32, 36, 16, 36, 28, 36);
        parts.add(torso);

        BodyBox rightArm = new BodyBox(-6, -2, 0, 4, 12, 4,
                44, 20, 52, 20, 40, 20, 48, 20);
        if (is64) rightArm.withOverlay(44, 36, 52, 36, 40, 36, 48, 36);
        parts.add(rightArm);

        BodyBox leftArm = is64
                ? new BodyBox(6, -2, 0, 4, 12, 4, 36, 52, 44, 52, 32, 52, 40, 52)
                        .withOverlay(52, 52, 60, 52, 48, 52, 56, 52)
                : new BodyBox(6, -2, 0, 4, 12, 4, 44, 20, 52, 20, 40, 20, 48, 20);
        parts.add(leftArm);

        BodyBox rightLeg = new BodyBox(-2, 10, 0, 4, 12, 4,
                4, 20, 12, 20, 0, 20, 8, 20);
        if (is64) rightLeg.withOverlay(4, 36, 12, 36, 0, 36, 8, 36);
        parts.add(rightLeg);

        BodyBox leftLeg = is64
                ? new BodyBox(2, 10, 0, 4, 12, 4, 20, 52, 28, 52, 16, 52, 24, 52)
                        .withOverlay(4, 52, 12, 52, 0, 52, 8, 52)
                : new BodyBox(2, 10, 0, 4, 12, 4, 4, 20, 12, 20, 0, 20, 8, 20);
        parts.add(leftLeg);

        for (BodyBox b : parts) {
            b.depthScore = -b.cx * sin + b.cz * cos;
        }
        parts.sort(Comparator.comparingDouble(b -> b.depthScore));

        for (BodyBox b : parts) {
            draw3DCuboid(ctx, skin, b, centerX, centerY, scale, cos, sin);
        }
    }

    private void draw3DCuboid(DrawContext ctx, int[][] skin, BodyBox b, int originX, int originY,
                              float scale, float cos, float sin) {
        float hw = b.w * 0.5f;
        float hd = b.d * 0.5f;

        float xFL = (b.cx - hw) * cos + (b.cz + hd) * sin;
        float xFR = (b.cx + hw) * cos + (b.cz + hd) * sin;
        float xBL = (b.cx - hw) * cos + (b.cz - hd) * sin;
        float xBR = (b.cx + hw) * cos + (b.cz - hd) * sin;

        int dstY = Math.round(originY + (b.cy - b.h * 0.5f) * scale);
        int dstH = Math.round(b.h * scale);

        if (sin > 0.01f) {
            int sx0 = Math.round(originX + xBR * scale);
            int sx1 = Math.round(originX + xFR * scale);
            float sideShade = 0.62f + 0.25f * Math.abs(sin);
            drawTexturedQuadX(ctx, skin, b.leftU, b.leftV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, false);
            if (b.hasOverlay) {
                drawTexturedQuadX(ctx, skin, b.ovLeftU, b.ovLeftV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, true);
            }
        } else if (sin < -0.01f) {
            int sx0 = Math.round(originX + xFL * scale);
            int sx1 = Math.round(originX + xBL * scale);
            float sideShade = 0.62f + 0.25f * Math.abs(sin);
            drawTexturedQuadX(ctx, skin, b.rightU, b.rightV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, false);
            if (b.hasOverlay) {
                drawTexturedQuadX(ctx, skin, b.ovRightU, b.ovRightV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, true);
            }
        }

        if (cos >= 0.0f) {
            int fx0 = Math.round(originX + xFL * scale);
            int fx1 = Math.round(originX + xFR * scale);
            float frontShade = 0.78f + 0.22f * cos;
            drawTexturedQuadX(ctx, skin, b.frontU, b.frontV, (int) b.w, (int) b.h, fx0, dstY, fx1, dstH, frontShade, false);
            if (b.hasOverlay) {
                drawTexturedQuadX(ctx, skin, b.ovFrontU, b.ovFrontV, (int) b.w, (int) b.h, fx0, dstY, fx1, dstH, frontShade, true);
            }
        } else {
            int bx0 = Math.round(originX + xBR * scale);
            int bx1 = Math.round(originX + xBL * scale);
            float backShade = 0.72f + 0.22f * (-cos);
            drawTexturedQuadX(ctx, skin, b.backU, b.backV, (int) b.w, (int) b.h, bx0, dstY, bx1, dstH, backShade, false);
            if (b.hasOverlay) {
                drawTexturedQuadX(ctx, skin, b.ovBackU, b.ovBackV, (int) b.w, (int) b.h, bx0, dstY, bx1, dstH, backShade, true);
            }
        }
    }

    private void drawTexturedQuadX(DrawContext ctx, int[][] skin, int srcU, int srcV, int sw, int sh,
                                   int xStart, int dstY, int xEnd, int dstH, float shade, boolean overlay) {
        if (skin == null || sw <= 0 || sh <= 0) return;
        if (srcU + sw > skin.length || srcV + sh > skin[0].length) return;
        int spanX = xEnd - xStart;
        if (Math.abs(spanX) < 1) return;

        for (int u = 0; u < sw; u++) {
            int px0 = xStart + (u * spanX) / sw;
            int px1 = xStart + ((u + 1) * spanX) / sw;
            int left = Math.min(px0, px1);
            int right = Math.max(px0, px1);
            if (right <= left) continue;

            for (int v = 0; v < sh; v++) {
                int argb = skin[srcU + u][srcV + v];
                int alpha = (argb >>> 24) & 0xFF;
                if (overlay && alpha < 128) continue;
                if (!overlay && alpha == 0) continue;

                int r = Math.min(255, (int) (((argb >>> 16) & 0xFF) * shade));
                int g = Math.min(255, (int) (((argb >>> 8) & 0xFF) * shade));
                int b = Math.min(255, (int) ((argb & 0xFF) * shade));
                int shaded = (0xFF << 24) | (r << 16) | (g << 8) | b;

                int y0 = dstY + (v * dstH) / sh;
                int y1 = dstY + ((v + 1) * dstH) / sh;
                if (y1 > y0) {
                    ctx.fill(left, y0, right, y1, shaded);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();

            int srvBtnW = 92;
            int srvBtnX = this.width - srvBtnW - 18;
            int srvBtnY = 16;
            if (mx >= srvBtnX && mx <= srvBtnX + srvBtnW && my >= srvBtnY && my <= srvBtnY + 22) {
                serversOpen = !serversOpen;
                this.init();
                return true;
            }

            for (SharpNavButton btn : navButtons) {
                if (btn.isHovered(mx, my)) {
                    btn.action.run();
                    return true;
                }
            }

            if (serversOpen) {
                for (SharpNavButton sb : serverButtons) {
                    if (sb.isHovered(mx, my)) {
                        sb.action.run();
                        return true;
                    }
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
