package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class PredictionFeatureRenderer extends RenderTypeFeatureRenderer<PredictionSubmit> {

    public static final FeatureRendererType<PredictionSubmit> TYPE = FeatureRendererType.create("fireballpredictor:prediction_submit");

    /**
     * Fresnel rim parameters for the shockwave dome (Schlick approximation).
     *
     * <p>The dome is emitted through the shared {@code POSITION_COLOR} pipeline, so instead of a
     * custom shader the Fresnel term is evaluated per vertex on the CPU and baked into the vertex
     * alpha ({@link #fresnelAlpha}). Surfaces facing the camera stay transparent while the
     * silhouette rim is pushed toward {@link PredictionRenderer#MAX_DOME_ALPHA}.
     */
    private static final float FRESNEL_F0 = 0.04f;  // dielectric base reflectance at normal incidence
    private static final int FRESNEL_POWER = 5;      // Schlick exponent
    private static final int FRESNEL_RIM_GLOW = 55;  // extra alpha added at grazing angles

    @Override
    protected void buildGroup(FeatureFrameContext context, List<PredictionSubmit> submits) {
        // One shared RenderType -> one buffer -> emission order == blend order.
        // Dome first, ribbon on top, so the trail stays readable through the blast sphere.
        VertexConsumer consumer = this.getVertexBuilder(PredictionRenderer.PREDICTION_GEOMETRY);

        for (PredictionSubmit submit : submits) {
            if (submit.domeState() != null) {
                renderDome(consumer, submit.domeState());
            }
        }

        for (PredictionSubmit submit : submits) {
            if (submit.trailState() != null) {
                renderTrail(consumer, submit.trailState());
            }
        }
    }

    private static Vec3 safeNormalize(Vec3 v, Vec3 fallback) {
        double lenSq = v.lengthSqr();
        if (lenSq > 1e-7) {
            return v.scale(1.0 / Math.sqrt(lenSq));
        }
        return fallback;
    }

    private void renderTrail(VertexConsumer consumer, TrailRenderState state) {
        Matrix4f positionMatrix = state.pose();
        float baseWidth = state.width();
        int r = state.r();
        int g = state.g();
        int b = state.b();
        Vec3 camLook = state.camLook();
        List<Vec3> path = state.path();
        int elapsedTicks = Math.max(0, state.elapsedTicks());
        int totalPathSteps = path.size() - 1;
        float startBlendSteps = 1.0f;

        TrajectoryStyle style = state.style() == null ? TrajectoryStyle.SOLID : state.style();
        boolean isDashed = style == TrajectoryStyle.DASHED;
        boolean isCoreOnly = style == TrajectoryStyle.CORE_ONLY;
        boolean drawCore = state.renderCoreGlow() || isCoreOnly;
        boolean drawShroud = !isCoreOnly;

        double animTime = state.animTime();
        double pulseSpeed = 0.45;

        float fade = state.fade();
        int maxAlpha = Math.round(PredictionRenderer.MAX_TRAIL_ALPHA * fade);

        for (int i = elapsedTicks; i < path.size() - 1; i++) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);
            Vec3 dir = safeNormalize(p2.subtract(p1), new Vec3(0, 1, 0));

            float blend1 = Math.min(1.0f, (float) (i - elapsedTicks) / startBlendSteps);
            float blend2 = Math.min(1.0f, (float) (i + 1 - elapsedTicks) / startBlendSteps);

            float widthBlend1 = 0.4f + 0.6f * blend1;
            float widthBlend2 = 0.4f + 0.6f * blend2;
            float alphaBlend1 = 0.3f + 0.7f * blend1;
            float alphaBlend2 = 0.3f + 0.7f * blend2;

            float progress1 = (float) i / totalPathSteps;
            float progress2 = (float) (i + 1) / totalPathSteps;

            float endTaper1 = progress1 > 0.8f ? 1.0f - (progress1 - 0.8f) * 2.0f : 1.0f;
            float endTaper2 = progress2 > 0.8f ? 1.0f - (progress2 - 0.8f) * 2.0f : 1.0f;

            float width1 = baseWidth * widthBlend1 * endTaper1;
            float width2 = baseWidth * widthBlend2 * endTaper2;

            float pulse1 = state.enableRibbonPulse() ? (0.85f + 0.15f * (float) Math.sin(animTime * pulseSpeed - progress1 * 6.0f)) : 1.0f;
            float pulse2 = state.enableRibbonPulse() ? (0.85f + 0.15f * (float) Math.sin(animTime * pulseSpeed - progress2 * 6.0f)) : 1.0f;

            float dash1 = isDashed ? ((i % 3 < 2) ? 1.0f : 0.15f) : 1.0f;
            float dash2 = isDashed ? (((i + 1) % 3 < 2) ? 1.0f : 0.15f) : 1.0f;

            int baseCenterAlpha1 = (int) (200 - (140 * Math.pow(progress1, 2)));
            int baseCenterAlpha2 = (int) (200 - (140 * Math.pow(progress2, 2)));

            // Clamped against MAX_TRAIL_ALPHA: with the translucent
            // (non-additive) pipeline a high alpha would paint over the cracking overlay of blocks the
            // ribbon crosses.
            int centerAlpha1 = Mth.clamp((int) (baseCenterAlpha1 * alphaBlend1 * pulse1 * dash1 * fade), 0, maxAlpha);
            int centerAlpha2 = Mth.clamp((int) (baseCenterAlpha2 * alphaBlend2 * pulse2 * dash2 * fade), 0, maxAlpha);
            int edgeAlpha = 0;

            Vec3 perp = dir.cross(camLook);
            if (perp.lengthSqr() < 0.001) {
                perp = dir.cross(new Vec3(0, 1, 0));
            }
            if (perp.lengthSqr() < 0.001) {
                perp = dir.cross(new Vec3(1, 0, 0));
            }
            Vec3 rightDir = safeNormalize(perp, new Vec3(1, 0, 0));

            // Pass 1: Outer Shroud
            if (drawShroud) {
                Vec3 right1 = rightDir.scale(width1);
                Vec3 right2 = rightDir.scale(width2);

                Vec3 p1L = p1.add(right1);
                Vec3 p1R = p1.subtract(right1);
                Vec3 p2L = p2.add(right2);
                Vec3 p2R = p2.subtract(right2);

                consumer.addVertex(positionMatrix, (float) p1L.x, (float) p1L.y, (float) p1L.z).setColor(r, g, b, edgeAlpha);
                consumer.addVertex(positionMatrix, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, centerAlpha1);
                consumer.addVertex(positionMatrix, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, centerAlpha2);
                consumer.addVertex(positionMatrix, (float) p2L.x, (float) p2L.y, (float) p2L.z).setColor(r, g, b, edgeAlpha);

                consumer.addVertex(positionMatrix, (float) p1.x, (float) p1.y, (float) p1.z).setColor(r, g, b, centerAlpha1);
                consumer.addVertex(positionMatrix, (float) p1R.x, (float) p1R.y, (float) p1R.z).setColor(r, g, b, edgeAlpha);
                consumer.addVertex(positionMatrix, (float) p2R.x, (float) p2R.y, (float) p2R.z).setColor(r, g, b, edgeAlpha);
                consumer.addVertex(positionMatrix, (float) p2.x, (float) p2.y, (float) p2.z).setColor(r, g, b, centerAlpha2);
            }

            // Pass 2: Inner Core Layer
            if (drawCore) {
                float coreWidthRatio = isCoreOnly ? 0.6f : 0.35f;
                float coreWidth1 = width1 * coreWidthRatio;
                float coreWidth2 = width2 * coreWidthRatio;

                int pass2R = isCoreOnly ? r : Math.min(255, r + (int) ((255 - r) * 0.35f));
                int pass2G = isCoreOnly ? g : Math.min(255, g + (int) ((255 - g) * 0.35f));
                int pass2B = isCoreOnly ? b : Math.min(255, b + (int) ((255 - b) * 0.35f));

                Vec3 coreRight1 = rightDir.scale(coreWidth1);
                Vec3 coreRight2 = rightDir.scale(coreWidth2);

                Vec3 cp1L = p1.add(coreRight1);
                Vec3 cp1R = p1.subtract(coreRight1);
                Vec3 cp2L = p2.add(coreRight2);
                Vec3 cp2R = p2.subtract(coreRight2);

                int coreAlphaCenter1 = isCoreOnly ? centerAlpha1 : Mth.clamp((int) (centerAlpha1 * 1.25f), 0, maxAlpha);
                int coreAlphaCenter2 = isCoreOnly ? centerAlpha2 : Mth.clamp((int) (centerAlpha2 * 1.25f), 0, maxAlpha);
                int coreAlphaEdge1 = isCoreOnly ? 0 : (int) (centerAlpha1 * 0.4f);
                int coreAlphaEdge2 = isCoreOnly ? 0 : (int) (centerAlpha2 * 0.4f);

                consumer.addVertex(positionMatrix, (float) cp1L.x, (float) cp1L.y, (float) cp1L.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge1);
                consumer.addVertex(positionMatrix, (float) p1.x, (float) p1.y, (float) p1.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter1);
                consumer.addVertex(positionMatrix, (float) p2.x, (float) p2.y, (float) p2.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter2);
                consumer.addVertex(positionMatrix, (float) cp2L.x, (float) cp2L.y, (float) cp2L.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge2);

                consumer.addVertex(positionMatrix, (float) p1.x, (float) p1.y, (float) p1.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter1);
                consumer.addVertex(positionMatrix, (float) cp1R.x, (float) cp1R.y, (float) cp1R.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge1);
                consumer.addVertex(positionMatrix, (float) cp2R.x, (float) cp2R.y, (float) cp2R.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge2);
                consumer.addVertex(positionMatrix, (float) p2.x, (float) p2.y, (float) p2.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter2);
            }
        }
    }

    private void renderDome(VertexConsumer consumer, DomeRenderState state) {
        Matrix4f positionMatrix = state.pose();
        int r = state.r();
        int g = state.g();
        int b = state.b();
        float pulseFactor = state.pulseFactor();
        float fade = state.fade();
        int maxAlpha = Math.round(PredictionRenderer.MAX_DOME_ALPHA * fade);

        // Camera position relative to the dome centre. The dome quads are generated as offsets from
        // the impact point, so in dome space the centre sits at the origin and the surface normal at
        // a vertex is simply the (normalised) vertex position.
        Vec3 cameraLocal = state.cameraPos().subtract(state.hitPos());
        float fresnelStrength = state.fresnelStrength();

        // Every dome quad uses the same RGB, so with a normal alpha blend the composite result stays
        // order independent. Alpha is capped so the cracking overlay of the blocks inside the dome
        // stays visible (the hemisphere is drawn twice per pixel: no culling). The Fresnel term is
        // evaluated per vertex and baked into the alpha (see {@link #fresnelAlpha}).
        for (PredictionRenderData.DomeQuad quad : state.domeQuads()) {
            int base1 = Mth.clamp((int) (quad.alpha1() * pulseFactor * fade), 0, maxAlpha);
            int base2 = Mth.clamp((int) (quad.alpha2() * pulseFactor * fade), 0, maxAlpha);

            consumer.addVertex(positionMatrix, (float) quad.p1().x, (float) quad.p1().y, (float) quad.p1().z)
                    .setColor(r, g, b, fresnelAlpha(quad.p1(), base1, cameraLocal, fresnelStrength, maxAlpha, fade));
            consumer.addVertex(positionMatrix, (float) quad.p2().x, (float) quad.p2().y, (float) quad.p2().z)
                    .setColor(r, g, b, fresnelAlpha(quad.p2(), base1, cameraLocal, fresnelStrength, maxAlpha, fade));
            consumer.addVertex(positionMatrix, (float) quad.p3().x, (float) quad.p3().y, (float) quad.p3().z)
                    .setColor(r, g, b, fresnelAlpha(quad.p3(), base2, cameraLocal, fresnelStrength, maxAlpha, fade));
            consumer.addVertex(positionMatrix, (float) quad.p4().x, (float) quad.p4().y, (float) quad.p4().z)
                    .setColor(r, g, b, fresnelAlpha(quad.p4(), base2, cameraLocal, fresnelStrength, maxAlpha, fade));
        }
    }

    /**
     * Bakes the Schlick Fresnel term for a single dome vertex into an alpha value.
     *
     * <p>The dome uses the shared {@code POSITION_COLOR} pipeline (no custom shader), so the Fresnel
     * term is evaluated on the CPU and written straight into the vertex alpha:
     * <pre>
     *   F = F0 + (1 - F0) * (1 - dot(N, V))^POWER
     * </pre>
     * Surface patches facing the camera (dot(N,V) close to 1) become transparent while the silhouette
     * rim (grazing angle, dot(N,V) close to 0) is pushed toward the alpha ceiling, giving the dome a
     * glass-bubble look that tracks the camera position. Because culling is disabled, the far side of
     * the hemisphere also receives the full rim term, which reads as the bright shell of the blast.
     *
     * @param vertex        dome-space vertex position (dome centre at origin; normal = vertex direction)
     * @param base          profile alpha (latitude shading, pulse and fade already applied)
     * @param cameraLocal   camera position relative to the dome centre
     * @param strength      config strength: 0 keeps the legacy latitude profile, 1 applies full Fresnel
     * @param maxAlpha      per-submit alpha ceiling (already scaled by {@code fade})
     * @param fade          global fade factor
     */
    private static int fresnelAlpha(Vec3 vertex, int base, Vec3 cameraLocal, float strength,
                                    int maxAlpha, float fade) {
        if (strength <= 0.0f) {
            return base;
        }

        Vec3 normal = safeNormalize(vertex, new Vec3(0.0, 1.0, 0.0));
        Vec3 view = safeNormalize(cameraLocal.subtract(vertex), new Vec3(0.0, 1.0, 0.0));

        float ndv = (float) Math.max(0.0, Math.abs(normal.dot(view)));
        // Schlick: F = F0 + (1 - F0) * (1 - dot)^FRESNEL_POWER (expanded below, FRESNEL_POWER = 5).
        float t = 1.0f - ndv;
        float fresnel = FRESNEL_F0 + (1.0f - FRESNEL_F0) * t * t * t * t * t;

        // Blend between the legacy profile (strength 0) and pure Fresnel shading (strength 1), then
        // add a fixed rim glow so the silhouette reads even where the latitude profile is zero.
        float alpha = base * (1.0f - strength + strength * fresnel)
                + FRESNEL_RIM_GLOW * fade * strength * fresnel;
        return Mth.clamp((int) alpha, 0, maxAlpha);
    }
}

record PredictionSubmit(
    float distanceToCameraSq,
    TrailRenderState trailState,
    DomeRenderState domeState
) implements TranslucentSubmit {
    @Override
    public FeatureRendererType<? extends TranslucentSubmit> featureType() {
        return PredictionFeatureRenderer.TYPE;
    }
}

record TrailRenderState(
    List<Vec3> path,
    int elapsedTicks,
    float width,
    int r,
    int g,
    int b,
    Vec3 camLook,
    Matrix4f pose,
    TrajectoryStyle style,
    boolean renderCoreGlow,
    boolean enableRibbonPulse,
    double animTime,
    float fade
) {}

record DomeRenderState(
    Vec3 hitPos,
    List<PredictionRenderData.DomeQuad> domeQuads,
    int r,
    int g,
    int b,
    float pulseFactor,
    Matrix4f pose,
    float fade,
    Vec3 cameraPos,
    float fresnelStrength
) {}
