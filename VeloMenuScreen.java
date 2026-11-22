package com.velo.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class VeloMenuScreen extends Screen {
    private final Screen parent;

    public VeloMenuScreen() {
        this(null);
    }

    public VeloMenuScreen(Screen parent) {
        super(Text.literal("VELO Client Meny"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 55;
        int btnWidth = 170;
        int btnHeight = 20;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("HUD: " + (VeloClient.hudEnabled ? "PÅ" : "AV")),
                button -> {
                    VeloClient.hudEnabled = !VeloClient.hudEnabled;
                    button.setMessage(Text.literal("HUD: " + (VeloClient.hudEnabled ? "PÅ" : "AV")));
                }).dimensions(centerX - btnWidth / 2, startY, btnWidth, btnHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("FPS-räknare: " + (VeloClient.showFps ? "PÅ" : "AV")),
                button -> {
                    VeloClient.showFps = !VeloClient.showFps;
                    button.setMessage(Text.literal("FPS-räknare: " + (VeloClient.showFps ? "PÅ" : "AV")));
                }).dimensions(centerX - btnWidth / 2, startY + 25, btnWidth, btnHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Koordinater: " + (VeloClient.showCoords ? "PÅ" : "AV")),
                button -> {
                    VeloClient.showCoords = !VeloClient.showCoords;
                    button.setMessage(Text.literal("Koordinater: " + (VeloClient.showCoords ? "PÅ" : "AV")));
                }).dimensions(centerX - btnWidth / 2, startY + 50, btnWidth, btnHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Toggle Sprint: " + (VeloClient.toggleSprint ? "PÅ" : "AV")),
                button -> {
                    VeloClient.toggleSprint = !VeloClient.toggleSprint;
                    button.setMessage(Text.literal("Toggle Sprint: " + (VeloClient.toggleSprint ? "PÅ" : "AV")));
                }).dimensions(centerX - btnWidth / 2, startY + 75, btnWidth, btnHeight).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Stäng meny"),
                button -> {
                    if (this.client != null) {
                        this.client.setScreen(this.parent);
                    }
                }).dimensions(centerX - btnWidth / 2, startY + 105, btnWidth, btnHeight).build());
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (this.client != null && this.client.world == null) {
            context.fillGradient(0, 0, this.width, this.height, 0xFF060E1F, 0xFF0F2547);
        } else {
            context.fill(0, 0, this.width, this.height, 0x88040A17);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int panelWidth = 220;
        int panelHeight = 190;
        int x = (this.width - panelWidth) / 2;
        int y = (this.height - panelHeight) / 2 - 12;

        context.fill(x, y, x + panelWidth, y + panelHeight, VeloClient.COLOR_BG);
        context.fill(x, y, x + panelWidth, y + 3, VeloClient.COLOR_ACCENT);
        context.fill(x, y + panelHeight - 2, x + panelWidth, y + panelHeight, VeloClient.COLOR_BORDER);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                "§lVELO CLIENT §r§b1.21.11",
                this.width / 2,
                y + 12,
                VeloClient.COLOR_ACCENT
        );
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
