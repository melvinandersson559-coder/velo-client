package com.velo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Ultra-smooth (512x512) supersamplad SDF UI-renderare utan pixlar:
 * - 512x512 anti-aliased 9-slice rundade paneler & kantlinjer (noll trappsteg)
 * - Subpixel anti-aliased mjuka linjer (drawSmoothLine) & glödande punkter (drawSmoothDot)
 * - Mjuk ambient drop-shadow
 */
public class VeloUiRenderer {
    private static final Identifier FILL_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_fill_512");
    private static final Identifier BORDER_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_border_512");
    private static final Identifier SHADOW_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_shadow_512");
    private static final Identifier DOT_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_dot_128");

    private static boolean initialized = false;
    private static final int TEX_SIZE = 512;
    private static final int CORNER_UV = 128;
    private static final int DOT_SIZE = 128;

    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        float half = TEX_SIZE / 2.0f;
        float radius = 104.0f;
        float boxHalf = half - radius - 16.0f;

        NativeImage fillImg = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        NativeImage borderImg = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);
        NativeImage shadowImg = new NativeImage(NativeImage.Format.RGBA, TEX_SIZE, TEX_SIZE, false);

        for (int y = 0; y < TEX_SIZE; y++) {
            for (int x = 0; x < TEX_SIZE; x++) {
                float px = Math.abs((x + 0.5f) - half) - boxHalf;
                float py = Math.abs((y + 0.5f) - half) - boxHalf;
                float dx = Math.max(px, 0.0f);
                float dy = Math.max(py, 0.0f);
                float dist = (float) Math.sqrt(dx * dx + dy * dy) + Math.min(Math.max(px, py), 0.0f) - radius;

                // 1. Silkeslen anti-aliased fyllning (bred SDF-övergång så även små knappar blir helt släta)
                float fillAlpha = smoothstep(5.5f, -5.5f, dist);
                int fa = Math.clamp(Math.round(fillAlpha * 255.0f), 0, 255);
                fillImg.setColorArgb(x, y, (fa << 24) | 0x00FFFFFF);

                // 2. Mjuk anti-aliased hairline-kantlinje
                float ringDist = Math.abs(dist + 4.5f);
                float borderAlpha = smoothstep(7.5f, 1.5f, ringDist);
                int ba = Math.clamp(Math.round(borderAlpha * 255.0f), 0, 255);
                borderImg.setColorArgb(x, y, (ba << 24) | 0x00FFFFFF);

                // 3. Mjuk drop-shadow
                float shadowAlpha = (dist <= 0.0f) ? 0.58f : (float) Math.exp(-dist * 0.072f) * 0.58f;
                int sa = Math.clamp(Math.round(shadowAlpha * 255.0f), 0, 255);
                shadowImg.setColorArgb(x, y, (sa << 24) | 0x00000000);
            }
        }

        // 4. Anti-aliased mjuk cirkel/borste för subpixel-släta linjer utan pixlar
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
        mc.getTextureManager().registerTexture(FILL_TEX, new NativeImageBackedTexture(() -> "velo_fill_512", fillImg));
        mc.getTextureManager().registerTexture(BORDER_TEX, new NativeImageBackedTexture(() -> "velo_border_512", borderImg));
        mc.getTextureManager().registerTexture(SHADOW_TEX, new NativeImageBackedTexture(() -> "velo_shadow_512", shadowImg));
        mc.getTextureManager().registerTexture(DOT_TEX, new NativeImageBackedTexture(() -> "velo_dot_128", dotImg));
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        drawSmoothRect(ctx, x, y, w, h, 6, color);
    }

    public static void drawSmoothRect(DrawContext ctx, int x, int y, int w, int h, int cornerRadius, int color) {
        ensureInitialized();
        if (w <= 0 || h <= 0) return;
        drawNineSlice(ctx, FILL_TEX, x, y, w, h, cornerRadius, color);
    }

    public static void drawSmoothPanel(DrawContext ctx, int x, int y, int w, int h, int fillColor, int borderColor) {
        drawSmoothPanel(ctx, x, y, w, h, 8, fillColor, borderColor, true);
    }

    public static void drawSmoothPanel(DrawContext ctx, int x, int y, int w, int h, int cornerRadius, int fillColor, int borderColor, boolean withShadow) {
        ensureInitialized();
        if (w <= 0 || h <= 0) return;
        if (withShadow && w >= 20 && h >= 14) {
            drawNineSlice(ctx, SHADOW_TEX, x - 4, y - 3, w + 8, h + 9, cornerRadius + 4, 0xAA000000);
        }
        drawNineSlice(ctx, FILL_TEX, x, y, w, h, cornerRadius, fillColor);
        if ((borderColor >>> 24) > 0) {
            drawNineSlice(ctx, BORDER_TEX, x, y, w, h, cornerRadius, borderColor);
        }
    }

    /**
     * Ritar en helt slät anti-aliased linje utan pixliga trappsteg.
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

    private static void drawNineSlice(DrawContext ctx, Identifier tex, int x, int y, int w, int h, int targetCorner, int color) {
        int c = Math.max(1, Math.min(targetCorner, Math.min(w / 2, h / 2)));
        int mw = w - c * 2;
        int mh = h - c * 2;
        int midUv = TEX_SIZE - CORNER_UV * 2;

        // 4 högupplösta anti-aliased hörn (128x128 texels supersamplade)
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, c, c, CORNER_UV, CORNER_UV, TEX_SIZE, TEX_SIZE, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + w - c, y, TEX_SIZE - CORNER_UV, 0, c, c, CORNER_UV, CORNER_UV, TEX_SIZE, TEX_SIZE, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y + h - c, 0, TEX_SIZE - CORNER_UV, c, c, CORNER_UV, CORNER_UV, TEX_SIZE, TEX_SIZE, color);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + w - c, y + h - c, TEX_SIZE - CORNER_UV, TEX_SIZE - CORNER_UV, c, c, CORNER_UV, CORNER_UV, TEX_SIZE, TEX_SIZE, color);

        // Kanter
        if (mw > 0) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + c, y, CORNER_UV, 0, mw, c, midUv, CORNER_UV, TEX_SIZE, TEX_SIZE, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + c, y + h - c, CORNER_UV, TEX_SIZE - CORNER_UV, mw, c, midUv, CORNER_UV, TEX_SIZE, TEX_SIZE, color);
        }
        if (mh > 0) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x, y + c, 0, CORNER_UV, c, mh, CORNER_UV, midUv, TEX_SIZE, TEX_SIZE, color);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + w - c, y + c, TEX_SIZE - CORNER_UV, CORNER_UV, c, mh, CORNER_UV, midUv, TEX_SIZE, TEX_SIZE, color);
        }
        // Mittparti
        if (mw > 0 && mh > 0) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, x + c, y + c, CORNER_UV, CORNER_UV, mw, mh, midUv, midUv, TEX_SIZE, TEX_SIZE, color);
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
