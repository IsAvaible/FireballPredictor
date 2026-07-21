package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
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

    private void renderTrail(VertexConsumer consumer, TrailRenderState state) {
        Matrix4f positionMatrix = state.pose();
        float width = state.width();
        int r = state.r();
        int g = state.g();
        int b = state.b();
        Vec3 camLook = state.camLook();
        List<Vec3> path = state.path();
        int elapsedTicks = state.elapsedTicks();

        for (int i = elapsedTicks; i < path.size() - 1; i++) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);
            
            Vec3 dir = p2.subtract(p1).normalize();
            Vec3 right = dir.cross(camLook).normalize().scale(width);
            
            if (right.lengthSqr() < 0.001) {
                right = new Vec3(0, 1, 0).cross(dir).normalize().scale(width);
            }

            Vec3 p1L = p1.add(right);
            Vec3 p1R = p1.subtract(right);
            Vec3 p2L = p2.add(right);
            Vec3 p2R = p2.subtract(right);
            
            float progress1 = (float) i / (path.size() - 1);
            float progress2 = (float) (i + 1) / (path.size() - 1);
            
            int centerAlpha1 = (int) (200 - (140 * Math.pow(progress1, 2)));
            int centerAlpha2 = (int) (200 - (140 * Math.pow(progress2, 2)));
            int edgeAlpha = 0;

            // Quad 1: Left to Center
            consumer.addVertex(positionMatrix, (float)p1L.x, (float)p1L.y, (float)p1L.z).setColor(r, g, b, edgeAlpha);
            consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(r, g, b, centerAlpha1);
            consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(r, g, b, centerAlpha2);
            consumer.addVertex(positionMatrix, (float)p2L.x, (float)p2L.y, (float)p2L.z).setColor(r, g, b, edgeAlpha);

            // Quad 2: Center to Right
            consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(r, g, b, centerAlpha1);
            consumer.addVertex(positionMatrix, (float)p1R.x, (float)p1R.y, (float)p1R.z).setColor(r, g, b, edgeAlpha);
            consumer.addVertex(positionMatrix, (float)p2R.x, (float)p2R.y, (float)p2R.z).setColor(r, g, b, edgeAlpha);
            consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(r, g, b, centerAlpha2);
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
    Matrix4f pose
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
