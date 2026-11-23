package com.velo.client;

import net.minecraft.client.gui.DrawContext;

public class VeloUiRenderer {

    /**
     * Ritar en mjuk rektangel med rundade hörn (clean UI utan pixliga Minecraft-knappar).
     */
    public static void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w / 2, h / 2));
        if (r <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }

        // Mittparti
        ctx.fill(x + r, y, x + w - r, y + h, color);
        // Vänster och höger kant
        ctx.fill(x, y + r, x + r, y + h - r, color);
        ctx.fill(x + w - r, y + r, x + w, y + h - r, color);

        // Mjuka hörn (cirkel-approximation)
        for (int dy = 0; dy < r; dy++) {
            int dx = (int) Math.ceil(r - Math.sqrt((double) (r * r - (r - dy) * (r - dy))));
            ctx.fill(x + dx, y + dy, x + r, y + dy + 1, color);
            ctx.fill(x + w - r, y + dy, x + w - dx, y + dy + 1, color);
            ctx.fill(x + dx, y + h - 1 - dy, x + r, y + h - dy, color);
            ctx.fill(x + w - r, y + h - 1 - dy, x + w - dx, y + h - dy, color);
        }
    }

    /**
     * Ritar en tunn, ren kant runt ett rundat kort.
     */
    public static void drawRoundedBorder(DrawContext ctx, int x, int y, int w, int h, int radius, int borderColor, int fillColor) {
        drawRoundedRect(ctx, x - 1, y - 1, w + 2, h + 2, radius + 1, borderColor);
        drawRoundedRect(ctx, x, y, w, h, radius, fillColor);
    }

    /**
     * Blandar två ARGB-färger mjukt (t = 0.0 till 1.0) för smooth hover-animationer.
     */
    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
