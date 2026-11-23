package com.velo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Högupplöst anti-aliased SDF 9-slice UI-renderare (helt utan pixliga kanter).
 */
public class VeloUiRenderer {
    private static final Identifier ROUNDED_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_panel");
    private static boolean initialized = false;
    private static final int TEX_SIZE = 64;
    private static final int CORNER = 16;

    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        float radius = 14.0f;
        float half = TEX_SIZE / 2.0f;
        float boxHalf = half - radius - 1.0f;

        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                float px = Math.abs((x + 0.5f) - half) - boxHalf;
                float py = Math.abs((y + 0.5f) - half) - boxHalf;
                float dx = Math.max(px, 0.0f);
                float dy = Math.max(py, 0.0f);
                float dist = (float) Math.sqrt(dx * dx + dy * dy) + Math.min(Math.max(px, py), 0.0f) - radius;
                float alpha = Math.clamp(0.5f - dist, 0.0f, 1.0f);
                int a = (int) (alpha * 255.0f);
                int argb = (a << 24) | 0x00FFFFFF;
                img.setColorArgb(x, y, argb);
            }
        }
        MinecraftClient.getInstance().getTextureManager().registerTexture(
                ROUNDED_TEX, new NativeImageBackedTexture(() -> "velo_smooth_panel", img)
        );
    }

    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        ensureInitialized();
        if (w <= 0 || h <= 0) return;
        drawNineSlice(ctx, x, y, w, h, color);
    }

    public static void drawSmoothPanel(DrawContext ctx, int x, int y, int w, int h, int fillColor, int borderColor) {
        ensureInitialized();
        if (w <= 0 || h <= 0) return;
        drawNineSlice(ctx, x, y, w, h, borderColor);
        if (w > 2 && h > 2) {
            drawNineSlice(ctx, x + 1, y + 1, w - 2, h - 2, fillColor);
        }
    }

    private static void drawNineSlice(DrawContext ctx, int x, int y, int w, int h, int color) {
        int c = Math.min(CORNER, Math.min(w / 2, h / 2));
        if (c <= 0) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        int mw = w - c * 2;
        int mh = h - c * 2;

        // 4 anti-aliased hörn
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x, y, 0, 0, c, c, CORNER, CORNER, TEX_SIZE, TEX_SIZE, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + w - c, y, TEX_SIZE - CORNER, 0, c, c, CORNER, CORNER, TEX_SIZE, TEX_SIZE, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x, y + h - c, 0, TEX_SIZE - CORNER, c, c, CORNER, CORNER, TEX_SIZE, TEX_SIZE, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + w - c, y + h - c, TEX_SIZE - CORNER, TEX_SIZE - CORNER, c, c, CORNER, CORNER, TEX_SIZE, TEX_SIZE, color);

        // Kanter & mittparti
        if (mw > 0) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + c, y, CORNER, 0, mw, c, TEX_SIZE - CORNER * 2, CORNER, TEX_SIZE, TEX_SIZE, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + c, y + h - c, CORNER, TEX_SIZE - CORNER, mw, c, TEX_SIZE - CORNER * 2, CORNER, TEX_SIZE, TEX_SIZE, color);
        }
        if (mh > 0) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x, y + c, 0, CORNER, c, mh, CORNER, TEX_SIZE - CORNER * 2, TEX_SIZE, TEX_SIZE, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, ROUNDED_TEX, x + w - c, y + c, TEX_SIZE - CORNER, CORNER, c, mh, CORNER, TEX_SIZE - CORNER * 2, TEX_SIZE, TEX_SIZE, color);
        }
        if (mw > 0 && mh > 0) {
            ctx.fill(x + c, y + c, x + w - c, y + h - c, color);
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
