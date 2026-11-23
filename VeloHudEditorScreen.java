package com.velo.client;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Meteor-inspirerad Drag & Drop HUD Editor.
 * Dra runt alla HUD-moduler fritt på skärmen, högerklicka för att visa/dölja dem.
 */
public class VeloHudEditorScreen extends Screen {
    private final Screen parent;

    private static class HudBox {
        final String id;
        final String title;
        final VeloConfigManager.HudPos pos;
        final int w, h;
        int drawX, drawY;

        HudBox(String id, String title, VeloConfigManager.HudPos pos, int w, int h) {
            this.id = id;
            this.title = title;
            this.pos = pos;
            this.w = w;
            this.h = h;
        }
    }

    private final List<HudBox> boxes = new ArrayList<>();
    private HudBox dragging = null;
    private int dragOffsetX, dragOffsetY;

    public VeloHudEditorScreen(Screen parent) {
        super(Text.literal("VELO HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        boxes.clear();
        VeloConfigManager.ConfigData d = VeloConfigManager.data;
        boxes.add(new HudBox("watermark", "VELO Watermark & Stats", d.watermarkHud, 152, 46));
        boxes.add(new HudBox("coords", "Coords + Nether", d.coordsHud, 166, 36));
        boxes.add(new HudBox("arraylist", "Active Modules (ArrayList)", d.arrayListHud, 134, 92));
        boxes.add(new HudBox("armor", "Armor & Totem HUD", d.armorHud, 128, 34));
        boxes.add(new HudBox("inventory", "Inventory Viewer", d.inventoryHud, 168, 62));
        boxes.add(new HudBox("target", "Target HUD", d.targetHud, 152, 42));
        boxes.add(new HudBox("compass", "Compass Bar", d.compassHud, 180, 22));
    }

    private void resolvePos(HudBox b) {
        if (b.pos.x == -1) {
            b.drawX = this.width - b.w - 10;
        } else if (b.pos.x == -2) {
            b.drawX = this.width / 2 + 24;
        } else if (b.pos.x == -3) {
            b.drawX = (this.width - b.w) / 2;
        } else {
            b.drawX = MathHelper.clamp(b.pos.x, 4, Math.max(4, this.width - b.w - 4));
        }
        b.drawY = MathHelper.clamp(b.pos.y, 4, Math.max(4, this.height - b.h - 4));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x77030712);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int accent = VeloClient.getAccentColor();

        // Subtilt stöd-rutnät (grid) för clean placering
        for (int gx = 0; gx < this.width; gx += 32) {
            ctx.fill(gx, 0, gx + 1, this.height, 0x1638BDF8);
        }
        for (int gy = 0; gy < this.height; gy += 32) {
            ctx.fill(0, gy, this.width, gy + 1, 0x1638BDF8);
        }
        // Centerlinjer
        ctx.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x3338BDF8);
        ctx.fill(0, this.height / 2, this.width, this.height / 2 + 1, 0x3338BDF8);

        // Info-bar längst ner
        int barW = 360;
        int barH = 30;
        int barX = (this.width - barW) / 2;
        int barY = this.height - 42;
        VeloUiRenderer.drawSmoothPanel(ctx, barX, barY, barW, barH, 9, 0xEB070E1D, accent, true);
        ctx.drawTextWithShadow(this.textRenderer, "§bVänsterklick: §fDra   §bHögerklick: §fVisa/Dölj", barX + 14, barY + 11, 0xFFFFFFFF);

        // Reset-knapp
        int rx = barX + barW - 122;
        int ry = barY + 5;
        boolean rHover = mouseX >= rx && mouseX <= rx + 56 && mouseY >= ry && mouseY <= ry + 20;
        VeloUiRenderer.drawSmoothPanel(ctx, rx, ry, 56, 20, 6, rHover ? 0xFF1E3A8A : 0xFF111C33, 0xFF38BDF8, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Reset", rx + 28, ry + 6, 0xFFFFFFFF);

        // Klar-knapp
        int dx = barX + barW - 60;
        boolean dHover = mouseX >= dx && mouseX <= dx + 52 && mouseY >= ry && mouseY <= ry + 20;
        VeloUiRenderer.drawSmoothPanel(ctx, dx, ry, 52, 20, 6, dHover ? VeloClient.getPrimaryBlue() : 0xFF132445, accent, false);
        ctx.drawCenteredTextWithShadow(this.textRenderer, "Klar", dx + 26, ry + 6, 0xFFFFFFFF);

        // Rita alla HUD-boxar
        for (HudBox b : boxes) {
            resolvePos(b);
            boolean hovered = mouseX >= b.drawX && mouseX <= b.drawX + b.w && mouseY >= b.drawY && mouseY <= b.drawY + b.h;
            int fill = b.pos.enabled
                    ? (hovered || dragging == b ? 0xCC12284D : 0xBB091428)
                    : (hovered ? 0x8824141D : 0x66160D14);
            int border = b.pos.enabled ? (hovered || dragging == b ? accent : 0x992563EB) : 0x88EF4444;

            VeloUiRenderer.drawSmoothPanel(ctx, b.drawX, b.drawY, b.w, b.h, 8, fill, border, b.pos.enabled);
            ctx.drawTextWithShadow(this.textRenderer, b.title, b.drawX + 8, b.drawY + 7, b.pos.enabled ? 0xFFFFFFFF : 0xFF94A3B8);
            String state = b.pos.enabled ? "§aPÅ" : "§cAV";
            ctx.drawTextWithShadow(this.textRenderer, state, b.drawX + 8, b.drawY + 19, 0xFFFFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        int barW = 360, barX = (this.width - barW) / 2, barY = this.height - 42;
        int rx = barX + barW - 122, ry = barY + 5, dx = barX + barW - 60;

        if (click.button() == 0) {
            if (mx >= rx && mx <= rx + 56 && my >= ry && my <= ry + 20) {
                VeloConfigManager.resetHudPositions();
                this.init();
                return true;
            }
            if (mx >= dx && mx <= dx + 52 && my >= ry && my <= ry + 20) {
                VeloConfigManager.save();
                if (this.client != null) this.client.setScreen(this.parent);
                return true;
            }
        }

        for (int i = boxes.size() - 1; i >= 0; i--) {
            HudBox b = boxes.get(i);
            resolvePos(b);
            if (mx >= b.drawX && mx <= b.drawX + b.w && my >= b.drawY && my <= b.drawY + b.h) {
                if (click.button() == 0) {
                    dragging = b;
                    b.pos.x = b.drawX;
                    b.pos.y = b.drawY;
                    dragOffsetX = (int) mx - b.drawX;
                    dragOffsetY = (int) my - b.drawY;
                    return true;
                } else if (click.button() == 1) {
                    b.pos.enabled = !b.pos.enabled;
                    VeloConfigManager.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (click.button() == 0 && dragging != null) {
            int nx = (int) click.x() - dragOffsetX;
            int ny = (int) click.y() - dragOffsetY;
            dragging.pos.x = MathHelper.clamp(nx, 4, Math.max(4, this.width - dragging.w - 4));
            dragging.pos.y = MathHelper.clamp(ny, 4, Math.max(4, this.height - dragging.h - 4));
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0 && dragging != null) {
            dragging = null;
            VeloConfigManager.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        VeloConfigManager.save();
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
