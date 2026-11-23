package com.velo.client.mixin;

import com.velo.client.VeloClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @ModifyArg(
            method = "renderFireOverlay",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"),
            index = 1,
            require = 0
    )
    private static float velo$lowFireOffset(float y) {
        if (VeloClient.lowFire) {
            return y - VeloClient.lowFireOffset;
        }
        return y;
    }
}
