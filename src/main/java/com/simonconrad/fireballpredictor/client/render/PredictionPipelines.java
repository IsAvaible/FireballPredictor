package com.simonconrad.fireballpredictor.client.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.simonconrad.fireballpredictor.mixin.RenderPipelinesAccessor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Custom {@link RenderPipeline} used by the prediction overlay (dome + trail).
 *
 * <p>Built from {@link RenderPipelinesAccessor#getDebugFilledSnippet()} with the following properties:
 * <ul>
 *   <li><b>Format & Program</b>: {@code POSITION_COLOR} quad format, using standard vanilla {@code core/position_color}.</li>
 *   <li><b>Blending</b>: {@code BlendFunction.TRANSLUCENT} for standard alpha blending.</li>
 *   <li><b>Depth Testing & Writing</b>: Reversed-Z {@code GREATER_THAN_OR_EQUAL} depth testing with {@code depthWrite = false}.
 *       Leaving {@code depthWrite = false} ensures depth buffer writes do not hide block breaking crack overlays ({@code CRUMBLING}).</li>
 *   <li><b>Culling</b>: Disables culling ({@code withCull(false)}) so both sides of the hemisphere dome and billboarded ribbon render.</li>
 * </ul>
 *
 * <p>Registered through {@link RenderPipelinesAccessor#register(RenderPipeline)} and registered with Iris via {@code IrisCompat}.
 */
public final class PredictionPipelines {

    public static final Identifier PREDICTION_PIPELINE_ID =
        Identifier.fromNamespaceAndPath("fireballpredictor", "pipeline/prediction");

    /**
     * Registered through {@link RenderPipelinesAccessor#register(RenderPipeline)} so the pipeline participates
     * in vanilla's pipeline warm-up/precompilation like every other pipeline.
     */
    public static final RenderPipeline PREDICTION = RenderPipelinesAccessor.register(
        RenderPipeline.builder(RenderPipelinesAccessor.getDebugFilledSnippet())
            .withLocation(PREDICTION_PIPELINE_ID)
            // Draw both faces: the hemisphere is open at the bottom and the ribbon is a flat billboard.
            .withCull(false)
            .build()
    );

    private PredictionPipelines() {
    }
}
