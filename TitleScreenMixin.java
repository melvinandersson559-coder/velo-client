package com.velo.client.mixin;

import com.velo.client.VeloTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public class TitleScreenMixin {

    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen velo$replaceTitleScreen(Screen screen) {
        if (screen instanceof TitleScreen) {
            return new VeloTitleScreen();
        }
        return screen;
    }
}
