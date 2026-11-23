package com.velo.client.mixin;

import com.velo.client.VeloClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    /**
     * Gör själva tomma handen mindre och smalare (inte tjock/fet).
     * Använder @ModifyVariable på MatrixStack så den är 100% kompatibel oavsett övriga parametrar i 1.21.11.
     */
    @ModifyVariable(method = "renderArmHoldingItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private MatrixStack velo$shrinkEmptyHand(MatrixStack matrices) {
        if (VeloClient.slimHand) {
            float s = VeloClient.handScale;
            matrices.translate(VeloClient.handOffsetX, VeloClient.handOffsetY, VeloClient.handOffsetZ);
            matrices.scale(s, s, s);
        }
        return matrices;
    }

    /**
     * Gör även vapen/block i handen mindre och renare.
     */
    @Inject(method = "applyEquipOffset", at = @At("TAIL"))
    private void velo$slimHandTransform(MatrixStack matrices, Arm arm, float equipProgress, CallbackInfo ci) {
        if (VeloClient.slimHand) {
            int side = (arm == Arm.RIGHT) ? 1 : -1;
            matrices.translate(side * VeloClient.handOffsetX, VeloClient.handOffsetY, VeloClient.handOffsetZ);
            float s = VeloClient.handScale;
            matrices.scale(s, s, s);
        }
    }
}
