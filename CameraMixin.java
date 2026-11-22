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
    private void velo$updateFreecam(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        if (FreecamManager.isEnabled()) {
            double x = MathHelper.lerp(tickProgress, FreecamManager.prevCamX, FreecamManager.camX);
            double y = MathHelper.lerp(tickProgress, FreecamManager.prevCamY, FreecamManager.camY);
            double z = MathHelper.lerp(tickProgress, FreecamManager.prevCamZ, FreecamManager.camZ);
            this.setPos(x, y, z);
            this.setRotation(FreecamManager.camYaw, FreecamManager.camPitch);
        }
    }

    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void velo$showSelfInFreecam(CallbackInfoReturnable<Boolean> cir) {
        if (FreecamManager.isEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
