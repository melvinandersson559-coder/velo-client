package com.velo.client.mixin;

import com.velo.client.FreecamManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void velo$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (FreecamManager.isEnabled() && mc.player != null && (Object) this == mc.player) {
            FreecamManager.camYaw += (float) cursorDeltaX * 0.15f;
            FreecamManager.camPitch = MathHelper.clamp(FreecamManager.camPitch + (float) cursorDeltaY * 0.15f, -90.0f, 90.0f);
            ci.cancel();
        }
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void velo$freezePlayerInFreecam(MovementType type, Vec3d movement, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (FreecamManager.isEnabled() && mc.player != null && (Object) this == mc.player) {
            ci.cancel();
        }
    }
}
