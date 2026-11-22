package com.velo.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.util.List;

public class VeloTitleScreen extends Screen {
    private boolean addingServer = false;
    private TextFieldWidget nameField;
    private TextFieldWidget ipField;

    public VeloTitleScreen() {
        super(Text.literal("VELO Client"));
    }

    @Override
    protected void init() {
        this.clearChildren();
        FavoriteServerManager.load();

        int centerY = this.height / 2;

        // Vänster kolumn: Huvudknappar
        int leftX = 30;
        int btnW = 150;
        int btnH = 20;
        int startY = centerY - 58;

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Singleplayer"), btn -> {
            if (this.client != null) this.client.setScreen(new SelectWorldScreen(this));
        }).dimensions(leftX, startY, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Multiplayer"), btn -> {
            if (this.client != null) this.client.setScreen(new MultiplayerScreen(this));
        }).dimensions(leftX, startY + 25, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("VELO Inställningar"), btn -> {
            if (this.client != null) this.client.setScreen(new VeloMenuScreen(this));
        }).dimensions(leftX, startY + 50, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Mods (Mod Menu)"), btn -> {
            openModMenu();
        }).dimensions(leftX, startY + 75, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Minecraft Options"), btn -> {
            if (this.client != null) this.client.setScreen(new OptionsScreen(this, this.client.options));
        }).dimensions(leftX, startY + 100, btnW, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Avsluta spel"), btn -> {
            if (this.client != null) this.client.scheduleStop();
        }).dimensions(leftX, startY + 125, btnW, btnH).build());

        // Höger panel: Favoritservrar
        int panelW = 205;
        int panelX = this.width - panelW - 25;
        int panelY = centerY - 75;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(addingServer ? "Avbryt" : "+ Lägg till favoritserver"),
                btn -> {
                    addingServer = !addingServer;
                    this.init();
                }).dimensions(panelX + 10, panelY + 24, panelW - 20, 18).build());

        int listStartY = panelY + 48;

        if (addingServer) {
            nameField = new TextFieldWidget(this.textRenderer, panelX + 10, listStartY, panelW - 20, 18, Text.literal("Servernamn"));
            nameField.setPlaceholder(Text.literal("Namn (t.ex. Min Server)"));
            this.addDrawableChild(nameField);

            ipField = new TextFieldWidget(this.textRenderer, panelX + 10, listStartY + 23, panelW - 20, 18, Text.literal("Server-IP"));
            ipField.setPlaceholder(Text.literal("IP (t.ex. mc.hypixel.net)"));
            this.addDrawableChild(ipField);

            this.addDrawableChild(ButtonWidget.builder(Text.literal("Spara server"), btn -> {
                String name = nameField.getText();
                String ip = ipField.getText();
                if (!ip.isBlank()) {
                    FavoriteServerManager.addServer(name, ip);
                    addingServer = false;
                    this.init();
                }
            }).dimensions(panelX + 10, listStartY + 46, panelW - 20, 20).build());
        } else {
            List<FavoriteServerManager.ServerEntry> servers = FavoriteServerManager.getServers();
            int maxShown = Math.min(servers.size(), 5);
            for (int i = 0; i < maxShown; i++) {
                final int index = i;
                FavoriteServerManager.ServerEntry entry = servers.get(i);
                int rowY = listStartY + (i * 24);

                this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("▶ " + entry.name),
                        btn -> connectToServer(entry)
                ).dimensions(panelX + 10, rowY, panelW - 44, 20).build());

                this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("✕"),
                        btn -> {
                            FavoriteServerManager.removeServer(index);
                            this.init();
                        }
                ).dimensions(panelX + panelW - 30, rowY, 20, 20).build());
            }
        }
    }

    private void openModMenu() {
        if (this.client == null) return;
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            try {
                Class<?> modsScreenClass = Class.forName("com.terraformersmc.modmenu.gui.ModsScreen");
                Constructor<?> ctor = modsScreenClass.getConstructor(Screen.class);
                Screen modsScreen = (Screen) ctor.newInstance(this);
                this.client.setScreen(modsScreen);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xFF060E1F, 0xFF0F2547);

        context.fill(0, 0, this.width, 3, VeloClient.COLOR_ACCENT);
        context.fill(0, this.height - 2, this.width, this.height, VeloClient.COLOR_BORDER);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        context.drawTextWithShadow(this.textRenderer, "§lVELO §r§bCLIENT", 30, 22, VeloClient.COLOR_ACCENT);
        context.drawTextWithShadow(this.textRenderer, "§7Fabric 1.21.11 • v1.5.0", 30, 34, VeloClient.COLOR_TEXT);

        renderPlayerCard(context, centerX, centerY);

        int panelW = 205;
        int panelH = 180;
        int panelX = this.width - panelW - 25;
        int panelY = centerY - 75;

        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, VeloClient.COLOR_BG);
        context.fill(panelX, panelY, panelX + panelW, panelY + 2, VeloClient.COLOR_ACCENT);
        context.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, VeloClient.COLOR_BORDER);
        context.drawTextWithShadow(this.textRenderer, "§lFAVORITSERVRAR", panelX + 10, panelY + 9, VeloClient.COLOR_ACCENT);

        if (!addingServer && FavoriteServerManager.getServers().isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    "§7Inga favoritservrar ännu",
                    panelX + panelW / 2,
                    panelY + 75,
                    VeloClient.COLOR_TEXT
            );
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPlayerCard(DrawContext context, int centerX, int centerY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String playerName = mc.getSession().getUsername();
        int[][] skin = PlayerSkinCache.getSkinPixels(playerName);

        int cardW = 122;
        int cardH = 180;
        int cardX = centerX - cardW / 2 - 12;
        int cardY = centerY - 75;

        context.fill(cardX, cardY, cardX + cardW, cardY + cardH, VeloClient.COLOR_BG);
        context.fill(cardX, cardY, cardX + cardW, cardY + 2, VeloClient.COLOR_ACCENT);
        context.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, VeloClient.COLOR_BORDER);

        int bob = (int) Math.round(Math.sin(System.currentTimeMillis() / 350.0) * 2.0);
        int scale = 4;
        int charCenterX = cardX + cardW / 2;
        int headX = charCenterX - (4 * scale);
        int headY = cardY + 14 + bob;

        drawSkinRegion(context, skin, 8, 8, 8, 8, headX, headY, scale, false);
        drawSkinRegion(context, skin, 40, 8, 8, 8, headX, headY, scale, true);

        int bodyY = headY + (8 * scale) + 1;
        int torsoX = charCenterX - (4 * scale);

        drawSkinRegion(context, skin, 20, 20, 8, 12, torsoX, bodyY, scale, false);
        drawSkinRegion(context, skin, 20, 36, 8, 12, torsoX, bodyY, scale, true);

        drawSkinRegion(context, skin, 44, 20, 4, 12, torsoX - (4 * scale) - 1, bodyY, scale, false);

        boolean is64x64 = skin[0].length >= 64;
        if (is64x64) {
            drawSkinRegion(context, skin, 36, 52, 4, 12, torsoX + (8 * scale) + 1, bodyY, scale, false);
        } else {
            drawSkinRegion(context, skin, 44, 20, 4, 12, torsoX + (8 * scale) + 1, bodyY, scale, false);
        }

        int legsY = bodyY + (12 * scale) + 1;
        drawSkinRegion(context, skin, 4, 20, 4, 12, torsoX, legsY, scale, false);
        if (is64x64) {
            drawSkinRegion(context, skin, 20, 52, 4, 12, torsoX + (4 * scale), legsY, scale, false);
        } else {
            drawSkinRegion(context, skin, 4, 20, 4, 12, torsoX + (4 * scale), legsY, scale, false);
        }

        context.drawCenteredTextWithShadow(this.textRenderer, "§f§l" + playerName, charCenterX, cardY + cardH - 27, VeloClient.COLOR_TEXT);
        context.drawCenteredTextWithShadow(this.textRenderer, "§b● VELO Client", charCenterX, cardY + cardH - 15, VeloClient.COLOR_ACCENT);
    }

    private void drawSkinRegion(DrawContext context, int[][] skin, int srcX, int srcY, int w, int h, int dstX, int dstY, int scale, boolean overlay) {
        if (skin == null || skin.length <= srcX + w || skin[0].length <= srcY + h) return;
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int argb = skin[srcX + x][srcY + y];
                int alpha = (argb >>> 24) & 0xFF;
                if (overlay && alpha < 128) continue;
                if (!overlay && alpha == 0) continue;
                int opaqueColor = (0xFF << 24) | (argb & 0x00FFFFFF);
                int px = dstX + x * scale;
                int py = dstY + y * scale;
                context.fill(px, py, px + scale, py + scale, opaqueColor);
            }
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
