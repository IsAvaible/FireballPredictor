package com.simonconrad.fireballpredictor.client.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.simonconrad.fireballpredictor.client.render.PredictionPipelines;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Registers our custom {@link RenderPipeline} with Iris so shader packs render it correctly.
 *
 * <p>Assigns {@link PredictionPipelines#PREDICTION} to Iris {@code ShaderKey.LIGHTNING} via internal reflection.
 * This routes rendering to {@code gbuffers_lightning}, which shader packs treat as fullbright/emissive
 * geometry without dark G-buffer re-lighting or alpha-testing pixel discards (which occur under public
 * {@code IrisProgram.BASIC}).
 *
 * <p>If internal APIs are unavailable, falls back to the public API {@code IrisProgram.BASIC}.
 * Shadow pass remains unassigned so prediction overlays do not cast shadows.
 */
public final class IrisCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("fireballpredictor/iris-compat");

    private static final String IRIS_ID = "iris";

    /** Emissive, alpha-test-free, POSITION_COLOR. See the class doc. */
    private static final String PREFERRED_SHADER_KEY = "LIGHTNING";

    private static boolean registered;

    private IrisCompat() {
    }

    public static boolean isIrisLoaded() {
        return FabricLoader.getInstance().isModLoaded(IRIS_ID);
    }

    /** Call once from {@code ClientModInitializer#onInitializeClient}. */
    public static void init() {
        if (registered || !isIrisLoaded()) {
            return;
        }
        registered = true;

        if (assignInternal(PredictionPipelines.PREDICTION, PREFERRED_SHADER_KEY)) {
            return;
        }
        assignViaPublicApi(PredictionPipelines.PREDICTION);
    }

    /**
     * Preferred path: {@code IrisPipelines.assignPipeline(pipeline, ShaderKey.LIGHTNING)}.
     *
     * @return true when the assignment succeeded (or was already present).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean assignInternal(RenderPipeline pipeline, String shaderKeyName) {
        try {
            Class<?> shaderKeyClass = Class.forName("net.irisshaders.iris.pipeline.programs.ShaderKey");
            Object shaderKey = Enum.valueOf((Class<Enum>) shaderKeyClass, shaderKeyName);

            Class<?> pipelinesClass = Class.forName("net.irisshaders.iris.pipeline.IrisPipelines");
            Method assign = pipelinesClass.getMethod("assignPipeline", RenderPipeline.class, shaderKeyClass);
            assign.invoke(null, pipeline, shaderKey);

            LOGGER.info("Assigned {} to Iris ShaderKey.{} (gbuffers_lightning: emissive, no alpha test).",
                PredictionPipelines.PREDICTION_PIPELINE_ID, shaderKeyName);
            return true;
        } catch (Throwable t) {
            // Unwrap the IllegalStateException Iris throws when a pipeline is assigned twice.
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if (cause instanceof IllegalStateException) {
                LOGGER.debug("Prediction pipeline was already assigned to an Iris program.", cause);
                return true;
            }
            LOGGER.info("Iris internal pipeline assignment unavailable ({}), falling back to the public API. "
                + "The overlay may look darker or harder-edged under shaders.", cause.toString());
            return false;
        }
    }

    /**
     * Fallback: the public, stable API. Produces the darker / alpha-tested look described in the class
     * doc, but is guaranteed to keep the geometry visible.
     */
    private static void assignViaPublicApi(RenderPipeline pipeline) {
        try {
            Holder.assignBasic(pipeline);
            LOGGER.info("Assigned {} to Iris program BASIC via the public API.",
                PredictionPipelines.PREDICTION_PIPELINE_ID);
        } catch (IllegalStateException alreadyAssigned) {
            LOGGER.debug("Prediction pipeline was already assigned to an Iris program.", alreadyAssigned);
        } catch (Throwable t) {
            LOGGER.warn("Could not register the prediction pipeline with Iris. The shockwave dome and "
                + "trajectory trail may be invisible while a shader pack is enabled.", t);
        }
    }

    /** Keeps {@code net.irisshaders.*} off the classpath unless Iris is actually present. */
    private static final class Holder {
        private static void assignBasic(RenderPipeline pipeline) {
            net.irisshaders.iris.api.v0.IrisApi.getInstance().assignPipeline(
                pipeline,
                net.irisshaders.iris.api.v0.IrisProgram.BASIC
            );
        }
    }
}
