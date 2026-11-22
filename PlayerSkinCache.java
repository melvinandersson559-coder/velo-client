package com.velo.client;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class PlayerSkinCache {
    private static volatile int[][] cachedSkinPixels = null;
    private static volatile boolean loading = false;
    private static String loadedForUser = "";

    public static int[][] getSkinPixels(String username) {
        if (username == null || username.isBlank()) {
            return getDefaultSkinPixels();
        }

        if (!username.equals(loadedForUser) && !loading) {
            loading = true;
            loadedForUser = username;
            CompletableFuture.runAsync(() -> {
                try {
                    BufferedImage img = downloadSkin("https://minotar.net/skin/" + username);
                    if (img == null) {
                        img = downloadSkin("https://mc-heads.net/skin/" + username);
                    }
                    if (img != null && img.getWidth() >= 64 && img.getHeight() >= 32) {
                        int w = img.getWidth();
                        int h = img.getHeight();
                        int[][] pixels = new int[w][h];
                        for (int x = 0; x < w; x++) {
                            for (int y = 0; y < h; y++) {
                                pixels[x][y] = img.getRGB(x, y);
                            }
                        }
                        cachedSkinPixels = pixels;
                    }
                } catch (Throwable ignored) {
                } finally {
                    loading = false;
                }
            });
        }

        return cachedSkinPixels != null ? cachedSkinPixels : getDefaultSkinPixels();
    }

    private static BufferedImage downloadSkin(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "VELO-Client/1.5.0");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    return ImageIO.read(in);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int[][] getDefaultSkinPixels() {
        int[][] p = new int[64][64];
        int hair = 0xFF2B1B17;
        int skin = 0xFFC68642;
        int eyeWhite = 0xFFFFFFFF;
        int eyeBlue = 0xFF38BDF8;
        int mouth = 0xFF8D5524;
        int shirt = 0xFF2563EB;
        int pants = 0xFF1E3A8A;

        for (int x = 8; x < 16; x++) {
            for (int y = 8; y < 16; y++) {
                p[x][y] = (y < 10) ? hair : skin;
            }
        }
        p[8][10] = hair;
        p[15][10] = hair;
        p[9][12] = eyeWhite;
        p[10][12] = eyeBlue;
        p[13][12] = eyeBlue;
        p[14][12] = eyeWhite;
        p[11][14] = mouth;
        p[12][14] = mouth;

        for (int x = 20; x < 28; x++) {
            for (int y = 20; y < 32; y++) {
                p[x][y] = shirt;
            }
        }
        for (int x = 44; x < 48; x++) {
            for (int y = 20; y < 32; y++) {
                p[x][y] = (y < 24) ? shirt : skin;
            }
        }
        for (int x = 36; x < 40; x++) {
            for (int y = 52; y < 64; y++) {
                p[x][y] = (y < 56) ? shirt : skin;
            }
        }
        for (int x = 4; x < 8; x++) {
            for (int y = 20; y < 32; y++) {
                p[x][y] = pants;
            }
        }
        for (int x = 20; x < 24; x++) {
            for (int y = 52; y < 64; y++) {
                p[x][y] = pants;
            }
        }
        return p;
    }
}
