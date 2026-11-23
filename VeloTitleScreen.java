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
 * Modern, ultra-clean blå VELO CLIENT-startskärm med äkta roterande 3D-karaktär
 * (snurrar enbart horisontellt utan upp-och-ner-rörelse), spelarbadge och favoritservrar.
 */
public class VeloTitleScreen extends Screen {

    private static class CleanButton {
        int x, y, w, h;
        String label;
        Runnable action;
        float hoverAnim = 0f;
        boolean primaryStyle;

        CleanButton(int x, int y, int w, int h, String label, boolean primaryStyle, Runnable action) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
            this.primaryStyle = primaryStyle;
            this.action = action;
        }

        boolean isHovered(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private static class BodyBox {
        float cx, cy, cz; // center in model pixels
        float w, h, d;    // width, height, depth in model pixels
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

    private final List<CleanButton> cleanButtons = new ArrayList<>();
    private boolean serversExpanded = true;
    private boolean addingServer = false;
    private TextFieldWidget nameField;
    private TextFieldWidget ipField;

    private float smoothRotation = 25.0f;
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
        int btnW = 210;
        int btnH = 28;
        int bx = cx - btnW / 2;

        cleanButtons.add(new CleanButton(bx, cy - 36, btnW, btnH, "SINGLEPLAYER", true, () -> {
            if (this.client != null) this.client.setScreen(new SelectWorldScreen(this));
        }));
        cleanButtons.add(new CleanButton(bx, cy - 1, btnW, btnH, "MULTIPLAYER", true, () -> {
            if (this.client != null) this.client.setScreen(new MultiplayerScreen(this));
        }));

        int subW = (btnW - 12) / 3;
        cleanButtons.add(new CleanButton(bx, cy + 35, subW, 24, "VELO", false, () -> {
            if (this.client != null) this.client.setScreen(new VeloMenuScreen(this));
        }));
        cleanButtons.add(new CleanButton(bx + subW + 6, cy + 35, subW, 24, "OPTIONS", false, () -> {
            if (this.client != null) this.client.setScreen(new OptionsScreen(this, this.client.options));
        }));
        cleanButtons.add(new CleanButton(bx + (subW + 6) * 2, cy + 35, subW, 24, "MODS", false, this::openModMenu));

        cleanButtons.add(new CleanButton(bx, cy + 66, btnW, 22, "QUIT GAME", false, () -> {
            if (this.client != null) this.client.scheduleStop();
        }));

        // Hopfällbar favoritserver-panel uppe till höger
        int panelW = 194;
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
        // Djup, modern midnattsblå bakgrundsgradient
        context.fillGradient(0, 0, this.width, this.height, 0xFF030712, 0xFF0B1C3D);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.05f);
        lastFrameNanos = now;
        // Mjuk, jämn horisontell 3D-rotation (ingen upp-och-ner-rörelse)
        smoothRotation = (smoothRotation + dt * 42.0f) % 360.0f;

        int cx = this.width / 2;
        int cy = this.height / 2;
        int accent = VeloClient.getAccentColor();
        int primary = VeloClient.getPrimaryBlue();

        // Spelarbadge uppe till vänster
        MinecraftClient mc = MinecraftClient.getInstance();
        String playerName = mc.getSession().getUsername();
        VeloUiRenderer.drawSmoothPanel(context, 18, 18, 148, 30, 9, 0xCC070F21, 0x882563EB, true);
        VeloUiRenderer.drawSmoothRect(context, 27, 29, 6, 6, 3, accent);
        context.drawTextWithShadow(this.textRenderer, "§f§l" + playerName, 39, 28, 0xFFFFFFFF);

        // Modern centrerad VELO CLIENT-rubrik + version-pill
        context.drawCenteredTextWithShadow(this.textRenderer, "§lVELO §r§bCLIENT", cx, cy - 86, accent);
        VeloUiRenderer.drawSmoothPanel(context, cx - 44, cy - 70, 88, 16, 8, 0x990A162E, 0x6638BDF8, false);
        context.drawCenteredTextWithShadow(this.textRenderer, "§7v2.1  •  Meteor", cx, cy - 66, 0xFFCBD5E1);

        // Favoritserver-panel uppe till höger
        int panelW = 194;
        int panelX = this.width - panelW - 18;
        int panelY = 18;
        int panelH = serversExpanded ? 160 : 28;
        VeloUiRenderer.drawSmoothPanel(context, panelX, panelY, panelW, panelH, 10, 0xCC070F21, 0x882563EB, true);
        context.drawTextWithShadow(this.textRenderer, "§lFAVORITSERVRAR " + (serversExpanded ? "▾" : "▸"),
                panelX + 12, panelY + 10, accent);

        // Äkta roterande 3D-gubbe (vänster om mittknapparna, helt stilla i höjdled)
        int avatarX = Math.max(78, cx - 178);
        int avatarY = cy + 6;
        renderTrue3DPlayer(context, avatarX, avatarY, playerName, smoothRotation);

        // Rita alla anti-aliased knappar med smooth hover
        for (CleanButton btn : cleanButtons) {
            boolean hovered = btn.isHovered(mouseX, mouseY);
            btn.hoverAnim += ((hovered ? 1.0f : 0.0f) - btn.hoverAnim) * Math.min(1.0f, dt * 15.0f);

            int baseFill = btn.primaryStyle ? 0xDD0F2142 : 0xCC091326;
            int hoverFill = btn.primaryStyle ? primary : 0xFF172C54;
            int fill = VeloUiRenderer.lerpColor(baseFill, hoverFill, btn.hoverAnim);
            int border = VeloUiRenderer.lerpColor(0x772563EB, accent, btn.hoverAnim);

            VeloUiRenderer.drawSmoothPanel(context, btn.x, btn.y, btn.w, btn.h, 8, fill, border, btn.primaryStyle);
            int textColor = VeloUiRenderer.lerpColor(0xFFCBD5E1, 0xFFFFFFFF, btn.hoverAnim);
            context.drawCenteredTextWithShadow(this.textRenderer, btn.label, btn.x + btn.w / 2, btn.y + (btn.h - 8) / 2, textColor);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Renderar spelaren som riktiga 3D-kuber (fram/bak + vänster/höger sidor) med djup-sortering,
     * enbart horisontell rotation och ingen upp-och-ner-rörelse.
     */
    private void renderTrue3DPlayer(DrawContext ctx, int centerX, int centerY, String playerName, float angleDeg) {
        int[][] skin = PlayerSkinCache.getSkinPixels(playerName);
        boolean is64 = skin != null && skin[0].length >= 64;

        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float scale = 4.0f;

        // Mjuk glödande platta under karaktären
        VeloUiRenderer.drawSmoothPanel(ctx, centerX - 36, centerY + 62, 72, 10, 5, 0x442563EB, 0x8838BDF8, false);

        List<BodyBox> parts = new ArrayList<>();

        // Huvud (8x8x8)
        parts.add(new BodyBox(0, -12, 0, 8, 8, 8,
                8, 8, 24, 8, 0, 8, 16, 8)
                .withOverlay(40, 8, 56, 8, 32, 8, 48, 8));

        // Kropp / Torso (8x12x4)
        BodyBox torso = new BodyBox(0, -2, 0, 8, 12, 4,
                20, 20, 32, 20, 16, 20, 28, 20);
        if (is64) torso.withOverlay(20, 36, 32, 36, 16, 36, 28, 36);
        parts.add(torso);

        // Höger arm (x = -6, 4x12x4)
        BodyBox rightArm = new BodyBox(-6, -2, 0, 4, 12, 4,
                44, 20, 52, 20, 40, 20, 48, 20);
        if (is64) rightArm.withOverlay(44, 36, 52, 36, 40, 36, 48, 36);
        parts.add(rightArm);

        // Vänster arm (x = +6, 4x12x4)
        BodyBox leftArm = is64
                ? new BodyBox(6, -2, 0, 4, 12, 4, 36, 52, 44, 52, 32, 52, 40, 52)
                        .withOverlay(52, 52, 60, 52, 48, 52, 56, 52)
                : new BodyBox(6, -2, 0, 4, 12, 4, 44, 20, 52, 20, 40, 20, 48, 20);
        parts.add(leftArm);

        // Höger ben (x = -2, 4x12x4)
        BodyBox rightLeg = new BodyBox(-2, 10, 0, 4, 12, 4,
                4, 20, 12, 20, 0, 20, 8, 20);
        if (is64) rightLeg.withOverlay(4, 36, 12, 36, 0, 36, 8, 36);
        parts.add(rightLeg);

        // Vänster ben (x = +2, 4x12x4)
        BodyBox leftLeg = is64
                ? new BodyBox(2, 10, 0, 4, 12, 4, 20, 52, 28, 52, 16, 52, 24, 52)
                        .withOverlay(4, 52, 12, 52, 0, 52, 8, 52)
                : new BodyBox(2, 10, 0, 4, 12, 4, 4, 20, 12, 20, 0, 20, 8, 20);
        parts.add(leftLeg);

        // Djup-sortera kroppsdelarna så bakre arm/ben alltid ritas bakom kroppen
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

        // 4 horisontella hörn i modellrymden roterade runt Y-axeln
        float xFL = (b.cx - hw) * cos + (b.cz + hd) * sin;
        float xFR = (b.cx + hw) * cos + (b.cz + hd) * sin;
        float xBL = (b.cx - hw) * cos + (b.cz - hd) * sin;
        float xBR = (b.cx + hw) * cos + (b.cz - hd) * sin;

        int dstY = Math.round(originY + (b.cy - b.h * 0.5f) * scale);
        int dstH = Math.round(b.h * scale);

        // Synlig sida (höger eller vänster) + synlig framsida/baksida
        if (sin > 0.01f) {
            // Höger sida (x = +hw, normal +X) syns när sin > 0
            int sx0 = Math.round(originX + xBR * scale);
            int sx1 = Math.round(originX + xFR * scale);
            float sideShade = 0.62f + 0.25f * Math.abs(sin);
            drawTexturedQuadX(ctx, skin, b.leftU, b.leftV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, false);
            if (b.hasOverlay) {
                drawTexturedQuadX(ctx, skin, b.ovLeftU, b.ovLeftV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, true);
            }
        } else if (sin < -0.01f) {
            // Vänster sida (x = -hw, normal -X) syns när sin < 0
            int sx0 = Math.round(originX + xFL * scale);
            int sx1 = Math.round(originX + xBL * scale);
            float sideShade = 0.62f + 0.25f * Math.abs(sin);
            drawTexturedQuadX(ctx, skin, b.rightU, b.rightV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, false);
            if (b.hasOverlay) {
                drawTexturedQuadX(ctx, skin, b.ovRightU, b.ovRightV, (int) b.d, (int) b.h, sx0, dstY, sx1, dstH, sideShade, true);
            }
        }

        if (cos >= 0.0f) {
            // Framsida syns
            int fx0 = Math.round(originX + xFL * scale);
            int fx1 = Math.round(originX + xFR * scale);
            float frontShade = 0.78f + 0.22f * cos;
            drawTexturedQuadX(ctx, skin, b.frontU, b.frontV, (int) b.w, (int) b.h, fx0, dstY, fx1, dstH, frontShade, false);
            if (b.hasOverlay) {
                drawTexturedQuadX(ctx, skin, b.ovFrontU, b.ovFrontV, (int) b.w, (int) b.h, fx0, dstY, fx1, dstH, frontShade, true);
            }
        } else {
            // Baksida syns
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
            int panelW = 194;
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
