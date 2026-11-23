package com.velo.client.mixin;

import com.velo.client.FreecamManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void velo$updateCamera(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (FreecamManager.isEnabled()) {
            double x = MathHelper.lerp(tickProgress, FreecamManager.prevCamX, FreecamManager.camX);
            double y = MathHelper.lerp(tickProgress, FreecamManager.prevCamY, FreecamManager.camY);
            double z = MathHelper.lerp(tickProgress, FreecamManager.prevCamZ, FreecamManager.camZ);
            this.setPos(x, y, z);
            this.setRotation(FreecamManager.camYaw, FreecamManager.camPitch);
        } else if (FreecamManager.isFreeLookActive() && focusedEntity != null) {
            this.setRotation(FreecamManager.freeLookYaw, FreecamManager.freeLookPitch);
            double radYaw = Math.toRadians(FreecamManager.freeLookYaw);
            double radPitch = Math.toRadians(FreecamManager.freeLookPitch);
            double dist = 4.0;
            double ox = Math.sin(radYaw) * Math.cos(radPitch) * dist;
            double oy = Math.sin(radPitch) * dist;
            double oz = -Math.cos(radYaw) * Math.cos(radPitch) * dist;
            double ex = MathHelper.lerp(tickProgress, focusedEntity.lastX, focusedEntity.getX());
            double ey = MathHelper.lerp(tickProgress, focusedEntity.lastY, focusedEntity.getY()) + focusedEntity.getStandingEyeHeight();
            double ez = MathHelper.lerp(tickProgress, focusedEntity.lastZ, focusedEntity.getZ());
            this.setPos(ex + ox, ey + oy, ez + oz);
        }
    }

    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void velo$showSelf(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamManager.isEnabled() || FreecamManager.isFreeLookActive()) {
            cir.setReturnValue(true);
        }
    }
}
