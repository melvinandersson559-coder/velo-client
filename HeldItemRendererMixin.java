package com.velo.client.mixin;

import com.velo.client.VeloClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    @ModifyVariable(method = "renderArmHoldingItem", at = @At("HEAD"), argsOnly = true, require = 0)
    private MatrixStack velo$shrinkEmptyHand(MatrixStack matrices) {
        if (VeloClient.slimHand) {
            float s = VeloClient.handScale;
            matrices.translate(VeloClient.handOffsetX, VeloClient.handOffsetY, VeloClient.handOffsetZ);
            if (VeloClient.handRotX != 0.0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(VeloClient.handRotX));
            if (VeloClient.handRotY != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(VeloClient.handRotY));
            if (VeloClient.handRotZ != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(VeloClient.handRotZ));
            matrices.scale(s, s, s);
        }
        return matrices;
    }

    @Inject(method = "applyEquipOffset", at = @At("TAIL"))
    private void velo$slimHandTransform(MatrixStack matrices, Arm arm, float equipProgress, CallbackInfo ci) {
        if (VeloClient.slimHand) {
            int side = (arm == Arm.RIGHT) ? 1 : -1;
            matrices.translate(side * VeloClient.handOffsetX, VeloClient.handOffsetY, VeloClient.handOffsetZ);
            if (VeloClient.handRotX != 0.0f) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(VeloClient.handRotX));
            if (VeloClient.handRotY != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * VeloClient.handRotY));
            if (VeloClient.handRotZ != 0.0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * VeloClient.handRotZ));
            float s = VeloClient.handScale;
            matrices.scale(s, s, s);
        }
    }
}
