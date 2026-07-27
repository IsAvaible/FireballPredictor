package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.TranslucentSubmit;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class PredictionFeatureRenderer extends RenderTypeFeatureRenderer<PredictionSubmit> {
    public static final FeatureRendererType<PredictionSubmit> TYPE = FeatureRendererType.create("fireballpredictor:prediction_submit");

    @Override
    protected void buildGroup(FeatureFrameContext context, List<PredictionSubmit> submits) {
        for (PredictionSubmit submit : submits) {
            if (submit.trailState() != null) {
                VertexConsumer consumer = this.getVertexBuilder(PredictionRenderer.FIREBALL_TRAIL);
                renderTrail(consumer, submit.trailState());
            }
            if (submit.domeState() != null) {
                VertexConsumer consumer = this.getVertexBuilder(PredictionRenderer.SHOCKWAVE_DOME);
                renderDome(consumer, submit.domeState());
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
        int elapsedTicks = state.elapsedTicks();

        int totalPathSteps = path.size() - 1;
        float startBlendSteps = 1.0f;

        TrajectoryStyle style = state.style() == null ? TrajectoryStyle.SOLID : state.style();
        boolean isDashed = style == TrajectoryStyle.DASHED;
        boolean isCoreOnly = style == TrajectoryStyle.CORE_ONLY;
        boolean drawCore = state.renderCoreGlow() || isCoreOnly;
        boolean drawShroud = !isCoreOnly;

        double animTime = state.animTime();
        double pulseSpeed = 0.45;

        for (int i = elapsedTicks; i < path.size() - 1; i++) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);

            Vec3 dir = p2.subtract(p1).normalize();

            float blend1 = Math.min(1.0f, (float)(i - elapsedTicks) / startBlendSteps);
            float blend2 = Math.min(1.0f, (float)(i + 1 - elapsedTicks) / startBlendSteps);

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

            int centerAlpha1 = (int) net.minecraft.util.Mth.clamp(baseCenterAlpha1 * alphaBlend1 * pulse1 * dash1, 0, 255);
            int centerAlpha2 = (int) net.minecraft.util.Mth.clamp(baseCenterAlpha2 * alphaBlend2 * pulse2 * dash2, 0, 255);
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

                consumer.addVertex(positionMatrix, (float)p1L.x, (float)p1L.y, (float)p1L.z).setColor(r, g, b, edgeAlpha);
                consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(r, g, b, centerAlpha1);
                consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(r, g, b, centerAlpha2);
                consumer.addVertex(positionMatrix, (float)p2L.x, (float)p2L.y, (float)p2L.z).setColor(r, g, b, edgeAlpha);

                consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(r, g, b, centerAlpha1);
                consumer.addVertex(positionMatrix, (float)p1R.x, (float)p1R.y, (float)p1R.z).setColor(r, g, b, edgeAlpha);
                consumer.addVertex(positionMatrix, (float)p2R.x, (float)p2R.y, (float)p2R.z).setColor(r, g, b, edgeAlpha);
                consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(r, g, b, centerAlpha2);
            }

            // Pass 2: Inner Core Layer
            if (drawCore) {
                float coreWidthRatio = isCoreOnly ? 0.6f : 0.35f;
                float coreWidth1 = width1 * coreWidthRatio;
                float coreWidth2 = width2 * coreWidthRatio;

                int pass2R = isCoreOnly ? r : Math.min(255, r + (int)((255 - r) * 0.35f));
                int pass2G = isCoreOnly ? g : Math.min(255, g + (int)((255 - g) * 0.35f));
                int pass2B = isCoreOnly ? b : Math.min(255, b + (int)((255 - b) * 0.35f));

                Vec3 coreRight1 = rightDir.scale(coreWidth1);
                Vec3 coreRight2 = rightDir.scale(coreWidth2);

                Vec3 cp1L = p1.add(coreRight1);
                Vec3 cp1R = p1.subtract(coreRight1);
                Vec3 cp2L = p2.add(coreRight2);
                Vec3 cp2R = p2.subtract(coreRight2);

                int coreAlphaCenter1 = isCoreOnly ? centerAlpha1 : net.minecraft.util.Mth.clamp((int)(centerAlpha1 * 1.25f), 0, 255);
                int coreAlphaCenter2 = isCoreOnly ? centerAlpha2 : net.minecraft.util.Mth.clamp((int)(centerAlpha2 * 1.25f), 0, 255);
                int coreAlphaEdge1 = isCoreOnly ? 0 : (int)(centerAlpha1 * 0.4f);
                int coreAlphaEdge2 = isCoreOnly ? 0 : (int)(centerAlpha2 * 0.4f);

                consumer.addVertex(positionMatrix, (float)cp1L.x, (float)cp1L.y, (float)cp1L.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge1);
                consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter1);
                consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter2);
                consumer.addVertex(positionMatrix, (float)cp2L.x, (float)cp2L.y, (float)cp2L.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge2);

                consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter1);
                consumer.addVertex(positionMatrix, (float)cp1R.x, (float)cp1R.y, (float)cp1R.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge1);
                consumer.addVertex(positionMatrix, (float)cp2R.x, (float)cp2R.y, (float)cp2R.z).setColor(pass2R, pass2G, pass2B, coreAlphaEdge2);
                consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(pass2R, pass2G, pass2B, coreAlphaCenter2);
            }
        }
    }

    private void renderDome(VertexConsumer consumer, DomeRenderState state) {
        Matrix4f positionMatrix = state.pose();
        int r = state.r();
        int g = state.g();
        int b = state.b();
        float pulseFactor = state.pulseFactor();

        for (PredictionRenderData.DomeQuad quad : state.domeQuads()) {
            int alpha1 = Math.min(255, Math.max(0, (int) (quad.alpha1() * pulseFactor)));
            int alpha2 = Math.min(255, Math.max(0, (int) (quad.alpha2() * pulseFactor)));
            
            consumer.addVertex(positionMatrix, (float) quad.p1().x, (float) quad.p1().y, (float) quad.p1().z).setColor(r, g, b, alpha1);
            consumer.addVertex(positionMatrix, (float) quad.p2().x, (float) quad.p2().y, (float) quad.p2().z).setColor(r, g, b, alpha1);
            consumer.addVertex(positionMatrix, (float) quad.p3().x, (float) quad.p3().y, (float) quad.p3().z).setColor(r, g, b, alpha2);
            consumer.addVertex(positionMatrix, (float) quad.p4().x, (float) quad.p4().y, (float) quad.p4().z).setColor(r, g, b, alpha2);
        }
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
    double animTime
) {}

record DomeRenderState(
    Vec3 hitPos,
    List<PredictionRenderData.DomeQuad> domeQuads,
    int r,
    int g,
    int b,
    float pulseFactor,
    Matrix4f pose
) {}
