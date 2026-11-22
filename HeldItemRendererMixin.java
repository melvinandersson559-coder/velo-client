package com.velo.client.mixin;

import com.velo.client.VeloClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @Inject(method = "applyEquipOffset", at = @At("TAIL"), require = 0)
    private void velo$slimHandTransform(MatrixStack matrices, Arm arm, float equipProgress, CallbackInfo ci) {
        if (VeloClient.slimHand) {
            int side = (arm == Arm.RIGHT) ? 1 : -1;
            matrices.translate(side * VeloClient.handOffsetX, VeloClient.handOffsetY, 0.0f);
            float scale = VeloClient.handScale;
            matrices.scale(scale, scale, scale);
        }
    }
}
