package com.velo.client;

import net.minecraft.client.MinecraftClient;

public class FreecamManager {
    private static boolean enabled = false;
    public static float speed = 1.0f;

    public static double camX, camY, camZ;
    public static double prevCamX, prevCamY, prevCamZ;
    public static float camYaw, camPitch;

    private static float savedPlayerYaw, savedPlayerPitch;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        if (enabled == value) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (value) {
            if (mc.player == null) return;
            enabled = true;
            camX = prevCamX = mc.player.getX();
            camY = prevCamY = mc.player.getEyeY();
            camZ = prevCamZ = mc.player.getZ();
            camYaw = savedPlayerYaw = mc.player.getYaw();
            camPitch = savedPlayerPitch = mc.player.getPitch();
            mc.chunkCullingEnabled = false;
        } else {
            enabled = false;
            if (mc.player != null) {
                mc.player.setYaw(savedPlayerYaw);
                mc.player.setPitch(savedPlayerPitch);
            }
            mc.chunkCullingEnabled = true;
        }
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static void tick(MinecraftClient mc) {
        if (!enabled) return;
        if (mc.player == null || mc.world == null) {
            enabled = false;
            mc.chunkCullingEnabled = true;
            return;
        }

        prevCamX = camX;
        prevCamY = camY;
        prevCamZ = camZ;

        // Flytta inte kameran om en meny eller chatt är öppen
        if (mc.currentScreen != null) return;

        float forward = 0.0f;
        float strafe = 0.0f;
        float vertical = 0.0f;

        if (mc.options.forwardKey.isPressed()) forward += 1.0f;
        if (mc.options.backKey.isPressed()) forward -= 1.0f;
        if (mc.options.leftKey.isPressed()) strafe += 1.0f;
        if (mc.options.rightKey.isPressed()) strafe -= 1.0f;
        if (mc.options.jumpKey.isPressed()) vertical += 1.0f;
        if (mc.options.sneakKey.isPressed()) vertical -= 1.0f;

        double moveSpeed = speed * 0.55;
        if (mc.options.sprintKey.isPressed()) {
            moveSpeed *= 1.8;
        }

        double rad = Math.toRadians(camYaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        double dx = (strafe * cos - forward * sin) * moveSpeed;
        double dz = (forward * cos + strafe * sin) * moveSpeed;
        double dy = vertical * moveSpeed;

        camX += dx;
        camY += dy;
        camZ += dz;
    }
}
