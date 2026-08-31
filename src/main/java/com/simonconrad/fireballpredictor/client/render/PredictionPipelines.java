package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.simonconrad.fireballpredictor.mixin.RenderLayerAccessor;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Custom {@link RenderPipeline} + {@link RenderLayer} used by the prediction overlay (dome + trail).
 *
 * <p>On 26.2 the pipeline is built from {@code RenderPipelines.DEBUG_FILLED_SNIPPET} and registered via
 * {@code RenderPipelines.register(...)}; 1.21.11 has neither the snippet nor a public registration API,
 * so the identical state is built explicitly with the pipeline builder:
 * <ul>
 *   <li><b>Format &amp; Program</b>: {@code POSITION_COLOR} quad format, using standard vanilla {@code core/position_color}.</li>
 *   <li><b>Uniforms</b>: {@code DynamicTransforms} and {@code Projection} uniform buffers required by {@code core/position_color}.</li>
 *   <li><b>Blending</b>: {@code BlendFunction.TRANSLUCENT} for standard alpha blending.</li>
 *   <li><b>Depth Testing &amp; Writing</b>: {@code LEQUAL} depth testing with {@code depthWrite = false}.
 *       Leaving {@code depthWrite = false} ensures depth buffer writes do not hide block breaking crack
 *       overlays ({@code CRUMBLING}).</li>
 *   <li><b>Culling</b>: Disables culling ({@code withCull(false)}) so both sides of the hemisphere dome and
 *       billboarded ribbon render.</li>
 * </ul>
 *
 * <p>The layer is created through {@link RenderLayerAccessor} (the package-private
 * {@code RenderLayer.of(String, RenderSetup)} factory) and registered with Iris via {@code IrisCompat}.
 */
public final class PredictionPipelines {

    public static final Identifier PREDICTION_PIPELINE_ID =
        Identifier.of("fireballpredictor", "pipeline/prediction");

    /**
     * The mod-owned pipeline. State mirrors the 26.2 {@code PredictionPipelines.PREDICTION} pipeline
     * built from {@code DEBUG_FILLED_SNIPPET}.
     */
    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withLocation(PREDICTION_PIPELINE_ID)
        .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
        .withUniform("Projection", UniformType.UNIFORM_BUFFER)
        .withVertexShader("core/position_color")
        .withFragmentShader("core/position_color")
        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
        .withBlend(BlendFunction.TRANSLUCENT)
        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
        .withDepthWrite(false)
        // Draw both faces: the hemisphere is open at the bottom and the ribbon is a flat billboard.
        .withCull(false)
        .build();

    /**
     * The single shared layer: one layer -&gt; one buffer -&gt; emission order equals blend order.
     */
    public static final RenderLayer PREDICTION = RenderLayerAccessor.fireballpredictor$create(
        "fireballpredictor:pipeline/prediction",
        RenderSetup.builder(PIPELINE)
            .translucent()
            .expectedBufferSize(1536)
            .build()
    );

    private PredictionPipelines() {
    }
}
