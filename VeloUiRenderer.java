package com.velo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * VELO 4.0 Architectural Sharp-Edge UI Renderer:
 * - 100% skarpa 90°-kanter med rakbladstunna 1px kantlinjer och valfria hörnhakar
 * - Mjuk ambient drop-shadow utan runda kanter
 * - Subpixel anti-aliased linjer (drawSmoothLine) för 3D glowing outlines utan trappsteg
 */
public class VeloUiRenderer {
    private static final Identifier DOT_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_dot_128");
    private static boolean initialized = false;
    private static final int DOT_SIZE = 128;

    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        NativeImage dotImg = new NativeImage(NativeImage.Format.RGBA, DOT_SIZE, DOT_SIZE, false);
        float dotHalf = DOT_SIZE / 2.0f;
        float dotRadius = dotHalf - 10.0f;
        for (int y = 0; y < DOT_SIZE; y++) {
            for (int x = 0; x < DOT_SIZE; x++) {
                float ddx = (x + 0.5f) - dotHalf;
                float ddy = (y + 0.5f) - dotHalf;
                float rDist = (float) Math.sqrt(ddx * ddx + ddy * ddy) - dotRadius;
                float a = smoothstep(8.0f, -6.0f, rDist);
                int ia = Math.clamp(Math.round(a * 255.0f), 0, 255);
                dotImg.setColorArgb(x, y, (ia << 24) | 0x00FFFFFF);
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getTextureManager().registerTexture(DOT_TEX, new NativeImageBackedTexture(() -> "velo_dot_128", dotImg));
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static void drawSharpRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        ctx.fill(x, y, x + w, y + h, color);
    }

    public static void drawSharpPanel(DrawContext ctx, int x, int y, int w, int h, int fillColor, int borderColor, boolean withShadow) {
        if (w <= 0 || h <= 0) return;
        if (withShadow && w >= 16 && h >= 12) {
            ctx.fill(x + 2, y + 2, x + w + 3, y + h + 3, 0x55000000);
            ctx.fill(x + 1, y + 1, x + w + 2, y + h + 2, 0x44000000);
        }
        if ((fillColor >>> 24) > 0) {
            ctx.fill(x, y, x + w, y + h, fillColor);
        }
        if ((borderColor >>> 24) > 0) {
            drawSharpBorder(ctx, x, y, w, h, borderColor);
        }
    }

    public static void drawSharpBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static void drawCornerTicks(DrawContext ctx, int x, int y, int w, int h, int len, int color) {
        if (w < len * 2 || h < len * 2 || (color >>> 24) == 0) return;
        // Top-left
        ctx.fill(x, y, x + len, y + 1, color);
        ctx.fill(x, y, x + 1, y + len, color);
        // Top-right
        ctx.fill(x + w - len, y, x + w, y + 1, color);
        ctx.fill(x + w - 1, y, x + w, y + len, color);
        // Bottom-left
        ctx.fill(x, y + h - 1, x + len, y + h, color);
        ctx.fill(x, y + h - len, x + 1, y + h, color);
        // Bottom-right
        ctx.fill(x + w - len, y + h - 1, x + w, y + h, color);
        ctx.fill(x + w - 1, y + h - len, x + w, y + h, color);
    }

    // Legacy method signatures routed directly to sharp 90° panels (zero rounded blobs)
    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        drawSharpRect(ctx, x, y, w, h, color);
    }

    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, int cornerRadius, int color) {
        drawSharpRect(ctx, x, y, w, h, color);
    }

    public static void drawSmoothPanel(DrawContext ctx, int x, int y, int w, int h, int fillColor, int borderColor) {
        drawSharpPanel(ctx, x, y, w, h, fillColor, borderColor, true);
    }

    public static void drawSmoothPanel(DrawContext ctx, int x, int y, int w, int h, int cornerRadius, int fillColor, int borderColor, boolean withShadow) {
        drawSharpPanel(ctx, x, y, w, h, fillColor, borderColor, withShadow);
    }

    /**
     * Subpixel anti-aliased linje för 3D glowing outlines och breadcrumbs utan pixliga trappsteg.
     */
    public static void drawSmoothLine(DrawContext ctx, float x0, float y0, float x1, float y1, float thickness, int color) {
        ensureInitialized();
        if ((color >>> 24) == 0) return;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > 1800.0f) return;

        int size = Math.max(2, Math.round(thickness * 1.45f));
        float halfSize = size * 0.5f;
        float stepPx = Math.max(0.75f, thickness * 0.42f);
        int steps = Math.max(1, Math.min(420, (int) Math.ceil(len / stepPx)));

        for (int i = 0; i <= steps; i++) {
            float t = (float) i / (float) steps;
            int px = Math.round(x0 + dx * t - halfSize);
            int py = Math.round(y0 + dy * t - halfSize);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, DOT_TEX, px, py, 0, 0, size, size, DOT_SIZE, DOT_SIZE, DOT_SIZE, DOT_SIZE, color);
        }
    }

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
