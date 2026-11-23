package com.velo.client.mixin;

import com.velo.client.VeloClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true, require = 0)
    private void velo$noHurtCam(MatrixStack matrices, float tickProgress, CallbackInfo ci) {
        if (VeloClient.noHurtCam) {
            ci.cancel();
        }
    }
}
