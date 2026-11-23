package com.velo.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Högupplöst (256x256) supersamplad SDF 9-slice UI-renderare:
 * - Helt jämna anti-aliased rundade hörn utan pixlar
 * - Rakbladstunn mjuk border-ring utan tjocka stegade kanter
 * - Mjuk drop-shadow bakom kort, knappar och HUD
 */
public class VeloUiRenderer {
    private static final Identifier FILL_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_fill_256");
    private static final Identifier BORDER_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_border_256");
    private static final Identifier SHADOW_TEX = Identifier.of(VeloClient.MOD_ID, "dynamic/smooth_shadow_256");

    private static boolean initialized = false;
    private static final int TEX_SIZE = 256;
    private static final int CORNER_UV = 64;

    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        float half = TEX_SIZE / 2.0f;
        float radius = 52.0f;
        float boxHalf = half - radius - 8.0f;

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

                // 1. Smooth anti-aliased fill (mjuk 2.5px övergång i 256px-texturen -> helt skarpt utan pixlar)
                float fillAlpha = smoothstep(1.8f, -1.8f, dist);
                int fa = Math.clamp(Math.round(fillAlpha * 255.0f), 0, 255);
                fillImg.setColorArgb(x, y, (fa << 24) | 0x00FFFFFF);

                // 2. Smooth hairline border ring (tunn, mjuk anti-aliased kantlinje)
                float ringDist = Math.abs(dist + 2.5f);
                float borderAlpha = smoothstep(3.6f, 0.8f, ringDist);
                int ba = Math.clamp(Math.round(borderAlpha * 255.0f), 0, 255);
                borderImg.setColorArgb(x, y, (ba << 24) | 0x00FFFFFF);

                // 3. Soft ambient drop-shadow
                float shadowAlpha = (dist <= 0.0f) ? 0.55f : (float) Math.exp(-dist * 0.14f) * 0.55f;
                int sa = Math.clamp(Math.round(shadowAlpha * 255.0f), 0, 255);
                shadowImg.setColorArgb(x, y, (sa << 24) | 0x00000000);
            }
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getTextureManager().registerTexture(FILL_TEX, new NativeImageBackedTexture(() -> "velo_fill_256", fillImg));
        mc.getTextureManager().registerTexture(BORDER_TEX, new NativeImageBackedTexture(() -> "velo_border_256", borderImg));
        mc.getTextureManager().registerTexture(SHADOW_TEX, new NativeImageBackedTexture(() -> "velo_shadow_256", shadowImg));
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
        drawSmoothPanel(ctx, x, y, w, h, 7, fillColor, borderColor, true);
    }

    public static void drawSmoothPanel(DrawContext ctx, int x, int y, int w, int h, int cornerRadius, int fillColor, int borderColor, boolean withShadow) {
        ensureInitialized();
        if (w <= 0 || h <= 0) return;
        if (withShadow && w >= 24 && h >= 16) {
            drawNineSlice(ctx, SHADOW_TEX, x - 3, y - 2, w + 6, h + 7, cornerRadius + 3, 0x99000000);
        }
        drawNineSlice(ctx, FILL_TEX, x, y, w, h, cornerRadius, fillColor);
        if ((borderColor >>> 24) > 0) {
            drawNineSlice(ctx, BORDER_TEX, x, y, w, h, cornerRadius, borderColor);
        }
    }

    private static void drawNineSlice(DrawContext ctx, Identifier tex, int x, int y, int w, int h, int targetCorner, int color) {
        int c = Math.min(targetCorner, Math.min(w / 2, h / 2));
        if (c <= 1) {
            ctx.fill(x, y, x + w, y + h, color);
            return;
        }
        int mw = w - c * 2;
        int mh = h - c * 2;
        int midUv = TEX_SIZE - CORNER_UV * 2;

        // 4 högupplösta anti-aliased hörn (64x64 texels skalade ner till c x c -> supersamplade!)
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
        // Mittparti (använd texturen så border-ringens mitt förblir transparent!)
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
