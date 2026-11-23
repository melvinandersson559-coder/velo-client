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
import java.util.Comparator;
import java.util.List;

/**
 * Ultra-minimalistisk VELO Client startskärm (v3.0):
 * - Helt ren mörk bakgrund utan Minecraft-utseende, med mjuka konturvågor
 * - Geometrisk sexuddig stjärnlogga + "VELO Client"
 * - Rena ramlösa textknappar med mjuk hover-animation
 * - Spelar-skin till höger med användarnamn ovanför
 */
public class VeloTitleScreen extends Screen {

    private static class MinimalMenuItem {
        int x, y, w, h;
        String label;
        Runnable action;
        float hoverAnim = 0f;

        MinimalMenuItem(int x, int y, int w, int h, String label, Runnable action) {
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

    private static class ServerActionBtn {
        int x, y, w, h;
        String label;
        Runnable action;
        float hoverAnim = 0f;

        ServerActionBtn(int x, int y, int w, int h, String label, Runnable action) {
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

    private final List<MinimalMenuItem> menuItems = new ArrayList<>();
    private final List<ServerActionBtn> serverButtons = new ArrayList<>();

    private boolean serversOpen = false;
    private boolean addingServer = false;
    private TextFieldWidget nameField;
    private TextFieldWidget ipField;

    private float smoothRotation = -22.0f;
    private float openAnim = 0.0f;
    private long lastFrameNanos = System.nanoTime();

    public VeloTitleScreen() {
        super(Text.literal("VELO Client"));
    }

    @Override
    protected void init() {
        this.clearChildren();
        menuItems.clear();
        serverButtons.clear();
        FavoriteServerManager.load();

        int leftCenterX = (int) (this.width * 0.38f);
        int centerY = this.height / 2;

        int itemW = 148;
        int itemH = 22;
        int startX = leftCenterX - itemW / 2;
        int startY = centerY - 6;
        int gap = 23;

        menuItems.add(new MinimalMenuItem(startX, startY, itemW, itemH, "Singleplayer", () -> {
            if (this.client != null) this.client.setScreen(new SelectWorldScreen(this));
        }));
        menuItems.add(new MinimalMenuItem(startX, startY + gap, itemW, itemH, "Multiplayer", () -> {
            if (this.client != null) this.client.setScreen(new MultiplayerScreen(this));
        }));
        menuItems.add(new MinimalMenuItem(startX, startY + gap * 2, itemW, itemH, "Minecraft Settings", () -> {
            if (this.client != null) this.client.setScreen(new OptionsScreen(this, this.client.options));
        }));
        menuItems.add(new MinimalMenuItem(startX, startY + gap * 3, itemW, itemH, "Client", () -> {
            if (this.client != null) this.client.setScreen(new VeloMenuScreen(this));
        }));
        menuItems.add(new MinimalMenuItem(startX, startY + gap * 4, itemW, itemH, "Quit Game", () -> {
            if (this.client != null) this.client.scheduleStop();
        }));

        // Diskret hopfällbar servermeny uppe till höger
        if (serversOpen) {
            int panelW = 176;
            int panelX = this.width - panelW - 18;
            int panelY = 42;

            serverButtons.add(new ServerActionBtn(panelX + 8, panelY + 8, panelW - 16, 18,
                    addingServer ? "Avbryt" : "+ Lägg till server", () -> {
                addingServer = !addingServer;
                this.init();
            }));

            int listY = panelY + 32;
            if (addingServer) {
                nameField = new TextFieldWidget(this.textRenderer, panelX + 8, listY, panelW - 16, 16, Text.literal("Namn"));
                nameField.setPlaceholder(Text.literal("Namn..."));
                this.addDrawableChild(nameField);

                ipField = new TextFieldWidget(this.textRenderer, panelX + 8, listY + 22, panelW - 16, 16, Text.literal("IP"));
                ipField.setPlaceholder(Text.literal("Server-IP..."));
                this.addDrawableChild(ipField);

                serverButtons.add(new ServerActionBtn(panelX + 8, listY + 44, panelW - 16, 18, "Spara", () -> {
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
                    int ry = listY + i * 21;
                    serverButtons.add(new ServerActionBtn(panelX + 8, ry, panelW - 36, 18, entry.name, () -> connectToServer(entry)));
                    serverButtons.add(new ServerActionBtn(panelX + panelW - 24, ry, 16, 18, "✕", () -> {
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
        // Minimalistisk matt mörkgrå/obsidian bakgrund (helt utan Minecraft-känsla)
        context.fillGradient(0, 0, this.width, this.height, 0xFF14161B, 0xFF1C1F26);

        // Mjuka kontur-vågor uppe till vänster och nere till höger (som i inspirationsbilden)
        for (int i = 0; i < 6; i++) {
            int r = 135 - i * 18;
            int alpha = 10 + i * 3;
            int col = (alpha << 24) | 0x9CA3AF;
            drawCornerWaveArc(context, 0, 0, r, col, true);
            drawCornerWaveArc(context, this.width, this.height, r + 25, col, false);
        }
    }

    private void drawCornerWaveArc(DrawContext ctx, int originX, int originY, int radius, int color, boolean topLeft) {
        int steps = 28;
        for (int s = 0; s < steps; s++) {
            double a0 = (Math.PI * 0.5) * s / steps;
            double a1 = (Math.PI * 0.5) * (s + 1) / steps;
            int x0 = topLeft ? (int) (originX + Math.cos(a0) * radius) : (int) (originX - Math.cos(a0) * radius);
            int y0 = topLeft ? (int) (originY + Math.sin(a0) * radius) : (int) (originY - Math.sin(a0) * radius);
            int x1 = topLeft ? (int) (originX + Math.cos(a1) * radius) : (int) (originX - Math.cos(a1) * radius);
            int y1 = topLeft ? (int) (originY + Math.sin(a1) * radius) : (int) (originY - Math.sin(a1) * radius);
            drawThinLine(ctx, x0, y0, x1, y1, color);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;

        float fastSmooth = 1.0f - (float) Math.exp(-15.0f * dt);
        openAnim += (1.0f - openAnim) * fastSmooth;
        smoothRotation = (smoothRotation + dt * 28.0f) % 360.0f;

        MinecraftClient mc = MinecraftClient.getInstance();
        String playerName = mc.getSession().getUsername();

        int leftCenterX = (int) (this.width * 0.38f);
        int centerY = this.height / 2;

        // 1. Geometrisk sexuddig stjärn-logga ovanför titeln
        int logoY = centerY - 62;
        drawMinimalHexagramLogo(context, leftCenterX, logoY, 17, 0xFFF3F4F6);

        // 2. Clean bold "VELO Client" titel
        context.drawCenteredTextWithShadow(this.textRenderer, "§lVELO Client", leftCenterX, centerY - 34, 0xFFF9FAFB);

        // 3. Ramlösa minimalistiska meny-alternativ med mjuk hover-animation
        for (MinimalMenuItem item : menuItems) {
            boolean hovered = item.isHovered(mouseX, mouseY);
            item.hoverAnim += ((hovered ? 1.0f : 0.0f) - item.hoverAnim) * fastSmooth;

            int textColor = VeloUiRenderer.lerpColor(0xFF9CA3AF, 0xFFFFFFFF, item.hoverAnim);
            int textY = item.y + (item.h - 8) / 2;
            context.drawCenteredTextWithShadow(this.textRenderer, item.label, item.x + item.w / 2, textY, textColor);

            // Tunn vit indikatorlinje under texten vid hover
            if (item.hoverAnim > 0.03f) {
                int lineW = Math.round(36 * item.hoverAnim);
                int lineAlpha = Math.round(210 * item.hoverAnim);
                int lineCol = (lineAlpha << 24) | 0xF3F4F6;
                context.fill((item.x + item.w / 2) - lineW / 2, textY + 11, (item.x + item.w / 2) + lineW / 2, textY + 12, lineCol);
            }
        }

        // 4. Spelarkaraktär till höger med användarnamn ovanför (precis som i inspirationsbilden)
        int avatarX = (int) (this.width * 0.72f);
        int avatarY = centerY + 26;
        context.drawCenteredTextWithShadow(this.textRenderer, "§f" + playerName + " §7▾", avatarX, avatarY - 76, 0xFFE5E7EB);
        renderTrue3DPlayer(context, avatarX, avatarY, playerName, smoothRotation);

        // 5. Minimalistisk server-knapp uppe till höger
        int srvBtnW = 84;
        int srvBtnX = this.width - srvBtnW - 18;
        int srvBtnY = 16;
        boolean srvHover = mouseX >= srvBtnX && mouseX <= srvBtnX + srvBtnW && mouseY >= srvBtnY && mouseY <= srvBtnY + 20;
        VeloUiRenderer.drawSmoothPanel(context, srvBtnX, srvBtnY, srvBtnW, 20, 6,
                srvHover ? 0xCC262A34 : 0x991A1D24, 0x449CA3AF, false);
        context.drawCenteredTextWithShadow(this.textRenderer, "Servers " + (serversOpen ? "▴" : "▾"),
                srvBtnX + srvBtnW / 2, srvBtnY + 6, 0xFFD1D5DB);

        if (serversOpen) {
            int panelW = 176;
            int panelX = this.width - panelW - 18;
            int panelY = 42;
            int panelH = addingServer ? 104 : (40 + Math.min(FavoriteServerManager.getServers().size(), 4) * 21);
            VeloUiRenderer.drawSmoothPanel(context, panelX, panelY, panelW, panelH, 8, 0xE6181B22, 0x449CA3AF, true);

            for (ServerActionBtn sb : serverButtons) {
                boolean hov = sb.isHovered(mouseX, mouseY);
                sb.hoverAnim += ((hov ? 1.0f : 0.0f) - sb.hoverAnim) * fastSmooth;
                int fill = VeloUiRenderer.lerpColor(0x88222630, 0xDD323846, sb.hoverAnim);
                VeloUiRenderer.drawSmoothPanel(context, sb.x, sb.y, sb.w, sb.h, 5, fill, 0x33FFFFFF, false);
                context.drawCenteredTextWithShadow(this.textRenderer, sb.label, sb.x + sb.w / 2, sb.y + 5,
                        hov ? 0xFFFFFFFF : 0xFFD1D5DB);
            }
        }

        // Diskret versionstext längst ner till vänster
        context.drawText(this.textRenderer, "VELO 3.0", 14, this.height - 18, 0xFF4B5563, false);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawMinimalHexagramLogo(DrawContext ctx, int cx, int cy, int r, int color) {
        // Två överlappande liksidiga trianglar (geometrisk stjärnsymbol)
        int[] upX = new int[3];
        int[] upY = new int[3];
        int[] dnX = new int[3];
        int[] dnY = new int[3];

        for (int i = 0; i < 3; i++) {
            double aUp = Math.toRadians(-90 + i * 120);
            upX[i] = cx + (int) Math.round(Math.cos(aUp) * r);
            upY[i] = cy + (int) Math.round(Math.sin(aUp) * r);

            double aDn = Math.toRadians(90 + i * 120);
            dnX[i] = cx + (int) Math.round(Math.cos(aDn) * r);
            dnY[i] = cy + (int) Math.round(Math.sin(aDn) * r);
        }

        for (int i = 0; i < 3; i++) {
            int next = (i + 1) % 3;
            drawThickLine(ctx, upX[i], upY[i], upX[next], upY[next], color);
            drawThickLine(ctx, dnX[i], dnY[i], dnX[next], dnY[next], color);
        }
    }

    private void drawThickLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            ctx.fill(x - 1, y - 1, x + 1, y + 1, color);
        }
    }

    private void drawThinLine(DrawContext ctx, int x0, int y0, int x1, int y1, int color) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps <= 0) return;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / steps;
            int y = y0 + (y1 - y0) * i / steps;
            ctx.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void renderTrue3DPlayer(DrawContext ctx, int centerX, int centerY, String playerName, float angleDeg) {
        int[][] skin = PlayerSkinCache.getSkinPixels(playerName);
        boolean is64 = skin != null && skin[0].length >= 64;

        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float scale = 3.8f;

        // Mjuk oval skugga under fötterna
        VeloUiRenderer.drawSmoothPanel(ctx, centerX - 34, centerY + 58, 68, 8, 4, 0x55000000, 0x22FFFFFF, false);

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

            int srvBtnW = 84;
            int srvBtnX = this.width - srvBtnW - 18;
            int srvBtnY = 16;
            if (mx >= srvBtnX && mx <= srvBtnX + srvBtnW && my >= srvBtnY && my <= srvBtnY + 20) {
                serversOpen = !serversOpen;
                this.init();
                return true;
            }

            for (MinimalMenuItem item : menuItems) {
                if (item.isHovered(mx, my)) {
                    item.action.run();
                    return true;
                }
            }

            if (serversOpen) {
                for (ServerActionBtn sb : serverButtons) {
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
