package com.simonconrad.fireballpredictor.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Unique
    private static final PoseStack IDENTITY_POSE_STACK = new PoseStack();

    @Inject(method = "submitFeatures", at = @At("TAIL"))
    private void fireballpredictor$onSubmitFeatures(
            LevelRenderState levelRenderState,
            SubmitNodeCollector submitNodeCollector,
            boolean renderBlockOutline,
            CallbackInfo ci
    ) {
        FireballPredictorClient.onSubmitFeatures(IDENTITY_POSE_STACK, submitNodeCollector);
    }
}
