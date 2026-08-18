package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void fireballpredictor$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (ThemePreviewGallery.isActive() && ThemePreviewGallery.handleLeftClick()) {
            cir.setReturnValue(true);
        }
    }
}
