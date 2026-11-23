package com.velo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public class VeloWaypointScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget nameField;
    private int winX, winY, winW, winH;

    public VeloWaypointScreen(Screen parent) {
        super(Text.literal("VELO Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.winW = Math.min(400, this.width - 30);
        this.winH = Math.min(260, this.height - 30);
        this.winX = (this.width - this.winW) / 2;
        this.winY = (this.height - this.winH) / 2;

        nameField = new TextFieldWidget(this.textRenderer, winX + 16, winY + 38, winW - 124, 18, Text.literal("Waypoint-namn"));
        nameField.setDrawsBackground(false);
        nameField.setPlaceholder(Text.literal("Namn (t.ex. Min Bas)..."));
        this.addDrawableChild(nameField);
        this.setInitialFocus(nameField);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88030712);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int accent = VeloClient.getAccentColor();
        VeloUiRenderer.drawSmoothPanel(ctx, winX, winY, winW, winH, 10, 0xF0070E1D, 0xAA2563EB, true);

        ctx.drawTextWithShadow(this.textRenderer, "§lWAYPOINTS §r§7(" + VeloConfigManager.data.waypoints.size() + ")", winX + 16, winY + 14, accent);

        // Klar-knapp
        int backX = winX + winW - 68, backY = winY + 10;
        boolean backHov = mouseX >= backX && mouseX <= backX + 56 && mouseY >= backY && mouseY <= backY + 18;
        VeloUiRenderer.drawSmoothPanel(ctx, backX, backY, 56, 18, 6, backHov ? VeloClient.getPrimaryBlue() : 0xFF13223D, accent, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Klar", backX + 28, backY + 5, 0xFFFFFFFF);

        // Input-ruta + Lägg till-knapp
        VeloUiRenderer.drawSmoothPanel(ctx, winX + 12, winY + 34, winW - 116, 22, 6, 0xFF0E1A30, 0xFF233B66, false);
        int addX = winX + winW - 96, addY = winY + 34;
        boolean addHov = mouseX >= addX && mouseX <= addX + 84 && mouseY >= addY && mouseY <= addY + 22;
        VeloUiRenderer.drawSmoothPanel(ctx, addX, addY, 84, 22, 6, addHov ? VeloClient.getPrimaryBlue() : 0xFF162C54, accent, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "+ Spara här", addX + 42, addY + 7, 0xFFFFFFFF);

        List<VeloConfigManager.Waypoint> wps = VeloConfigManager.data.waypoints;
        int y = winY + 66;
        for (int i = 0; i < wps.size() && y + 26 < winY + winH - 8; i++) {
            VeloConfigManager.Waypoint wp = wps.get(i);
            VeloUiRenderer.drawSmoothPanel(ctx, winX + 12, y, winW - 24, 24, 6, 0xCC0D172B, wp.enabled ? accent : 0xFF1D2F52, false);
            ctx.drawTextWithShadow(this.textRenderer, (wp.enabled ? "§b● §f" : "§7○ ") + wp.name, winX + 20, y + 8, 0xFFFFFFFF);
            String c = "§7" + wp.x + ", " + wp.y + ", " + wp.z;
            ctx.drawTextWithShadow(this.textRenderer, c, winX + winW - 44 - this.textRenderer.getWidth(c), y + 8, 0xFF94A3B8);
            ctx.drawTextWithShadow(this.textRenderer, "§c✕", winX + winW - 28, y + 8, 0xFFFF4D6D);
            y += 28;
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            int backX = winX + winW - 68, backY = winY + 10;
            if (mx >= backX && mx <= backX + 56 && my >= backY && my <= backY + 18) {
                if (this.client != null) this.client.setScreen(this.parent);
                return true;
            }

            int addX = winX + winW - 96, addY = winY + 34;
            if (mx >= addX && mx <= addX + 84 && my >= addY && my <= addY + 22) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    String n = nameField.getText().isBlank() ? ("Waypoint #" + (VeloConfigManager.data.waypoints.size() + 1)) : nameField.getText().trim();
                    VeloConfigManager.data.waypoints.add(new VeloConfigManager.Waypoint(
                            n, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), VeloClient.getAccentColor()));
                    VeloConfigManager.save();
                    nameField.setText("");
                }
                return true;
            }

            List<VeloConfigManager.Waypoint> wps = VeloConfigManager.data.waypoints;
            int y = winY + 66;
            for (int i = 0; i < wps.size() && y + 26 < winY + winH - 8; i++) {
                if (my >= y && my <= y + 24) {
                    if (mx >= winX + winW - 34 && mx <= winX + winW - 12) {
                        wps.remove(i);
                        VeloConfigManager.save();
                        return true;
                    } else if (mx >= winX + 12 && mx <= winX + winW - 36) {
                        wps.get(i).enabled = !wps.get(i).enabled;
                        VeloConfigManager.save();
                        return true;
                    }
                }
                y += 28;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
