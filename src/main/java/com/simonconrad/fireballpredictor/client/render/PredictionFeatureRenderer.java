package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.config.VisualTheme;
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

    static final Vec3 UP = new Vec3(0, 1, 0);
    static final Vec3 DOWN = new Vec3(0, -1, 0);
    static final Vec3 RIGHT = new Vec3(1, 0, 0);
    static final Vec3 FORWARD = new Vec3(0, 0, 1);

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

    static Vec3 safeNormalize(Vec3 v, Vec3 fallback) {
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
        // animTime is in seconds now (it used to be in ticks before the theme patch); the legacy
        // ribbon pulse was tuned at 0.45 rad/tick, so scale by 20 tps to keep the DEFAULT theme
        // visually identical to the pre-theme renderer.
        double pulseSpeed = 0.45 * 20.0;

        // Cumulative arc length (in blocks) up to first rendered segment for distance-anchored decorations
        double segStartDist = 0.0;
        for (int s = 1; s <= elapsedTicks && s < path.size(); s++) {
            segStartDist += path.get(s - 1).distanceTo(path.get(s));
        }

        float fade = state.fade();
        int maxAlpha = Math.round(PredictionRenderer.MAX_TRAIL_ALPHA * fade);

        VisualTheme theme = state.theme() == null ? VisualTheme.DEFAULT : state.theme();
        int fallbackRgb = VisualTheme.packRgb(r, g, b);

        for (int i = elapsedTicks; i < path.size() - 1; i++) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);
            Vec3 dir = safeNormalize(p2.subtract(p1), UP);

            float blend1 = Math.min(1.0f, (float) (i - elapsedTicks) / startBlendSteps);
            float blend2 = Math.min(1.0f, (float) (i + 1 - elapsedTicks) / startBlendSteps);

            float widthBlend1 = 0.4f + 0.6f * blend1;
            float widthBlend2 = 0.4f + 0.6f * blend2;
            float alphaBlend1 = 0.3f + 0.7f * blend1;
            float alphaBlend2 = 0.3f + 0.7f * blend2;

            float progress1 = (float) i / totalPathSteps;
            float progress2 = (float) (i + 1) / totalPathSteps;
            float segProgress = (progress1 + progress2) * 0.5f;

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

            float themeAlphaMod = theme.getRibbonAlphaModulation(segProgress, animTime, i);

            // Clamped against MAX_TRAIL_ALPHA: with the translucent
            // (non-additive) pipeline a high alpha would paint over the cracking overlay of blocks the
            // ribbon crosses.
            int centerAlpha1 = Mth.clamp((int) (baseCenterAlpha1 * alphaBlend1 * pulse1 * dash1 * fade * themeAlphaMod), 0, maxAlpha);
            int centerAlpha2 = Mth.clamp((int) (baseCenterAlpha2 * alphaBlend2 * pulse2 * dash2 * fade * themeAlphaMod), 0, maxAlpha);
            int edgeAlpha = 0;

            Vec3 perp = dir.cross(camLook);
            if (perp.lengthSqr() < 0.001) {
                perp = dir.cross(UP);
            }
            if (perp.lengthSqr() < 0.001) {
                perp = dir.cross(RIGHT);
            }
            Vec3 rightDir = safeNormalize(perp, RIGHT);

            // Camera up-basis for trail billboard overlays. Only themes that actually emit
            // billboards need it — computing it unconditionally would allocate two Vec3s per
            // segment for every tracked projectile even on the DEFAULT theme.
            boolean needsBillboard = PredictionThemeRenderer.requiresTrailUpBasis(theme);
            Vec3 trailUp = needsBillboard ? safeNormalize(camLook.cross(rightDir), UP) : null;

            int segShroudRgb = theme.getRibbonColorPacked(segProgress, animTime, i, false, fallbackRgb);
            int segCoreRgb = theme.getRibbonColorPacked(segProgress, animTime, i, true, fallbackRgb);

            int sr = VisualTheme.extractR(segShroudRgb);
            int sg = VisualTheme.extractG(segShroudRgb);
            int sb = VisualTheme.extractB(segShroudRgb);

            // Pass 1: Outer Shroud
            if (drawShroud) {
                emitRibbonQuad(consumer, positionMatrix, p1, p2,
                        rightDir.scale(width1), rightDir.scale(width2),
                        sr, sg, sb, edgeAlpha, edgeAlpha, centerAlpha1, centerAlpha2);
            }

            // Pass 2: Inner Core Layer
            if (drawCore) {
                float coreWidthRatio = isCoreOnly ? 0.6f : 0.35f;
                int pass2R = isCoreOnly ? sr : VisualTheme.extractR(segCoreRgb);
                int pass2G = isCoreOnly ? sg : VisualTheme.extractG(segCoreRgb);
                int pass2B = isCoreOnly ? sb : VisualTheme.extractB(segCoreRgb);

                int coreAlphaCenter1 = isCoreOnly ? centerAlpha1 : Mth.clamp((int) (centerAlpha1 * 1.25f), 0, maxAlpha);
                int coreAlphaCenter2 = isCoreOnly ? centerAlpha2 : Mth.clamp((int) (centerAlpha2 * 1.25f), 0, maxAlpha);
                int coreAlphaEdge1 = isCoreOnly ? 0 : (int) (centerAlpha1 * 0.4f);
                int coreAlphaEdge2 = isCoreOnly ? 0 : (int) (centerAlpha2 * 0.4f);

                emitRibbonQuad(consumer, positionMatrix, p1, p2,
                        rightDir.scale(width1 * coreWidthRatio), rightDir.scale(width2 * coreWidthRatio),
                        pass2R, pass2G, pass2B, coreAlphaEdge1, coreAlphaEdge2, coreAlphaCenter1, coreAlphaCenter2);
            }

            // Thematic per-segment passes (Electric arcs, Sculk tendrils, Inferno flames, Ghost wisps, Matrix glyphs, etc.)
            if (theme.isCustomTheme()) {
                PredictionThemeRenderer.renderTrailThemeSegment(
                        consumer, positionMatrix, theme, path, i, p1, p2, rightDir, trailUp,
                        width1, width2, centerAlpha1, baseCenterAlpha1, alphaBlend1, pulse1,
                        dash1, fade, maxAlpha, animTime, segStartDist);
            }

            segStartDist += p1.distanceTo(p2);
        }

        // Thematic whole-path passes (Tactical HUD fighter jets, etc.)
        if (theme.isCustomTheme()) {
            PredictionThemeRenderer.renderTrailThemeGlobal(
                    consumer, positionMatrix, theme, path, totalPathSteps, baseWidth, maxAlpha, animTime);
        }
    }

    static void emitRibbonQuad(
            VertexConsumer consumer, Matrix4f pose,
            Vec3 p1, Vec3 p2, Vec3 right1, Vec3 right2,
            int r, int g, int b, int edgeAlpha1, int edgeAlpha2, int centerAlpha1, int centerAlpha2
    ) {
        emitRibbonQuad(consumer, pose,
                (float) p1.x, (float) p1.y, (float) p1.z,
                (float) p2.x, (float) p2.y, (float) p2.z,
                (float) right1.x, (float) right1.y, (float) right1.z,
                (float) right2.x, (float) right2.y, (float) right2.z,
                r, g, b, edgeAlpha1, edgeAlpha2, centerAlpha1, centerAlpha2);
    }

    static void emitRibbonQuad(
            VertexConsumer consumer, Matrix4f pose,
            float p1x, float p1y, float p1z, float p2x, float p2y, float p2z,
            float r1x, float r1y, float r1z, float r2x, float r2y, float r2z,
            int r, int g, int b, int edgeAlpha1, int edgeAlpha2, int centerAlpha1, int centerAlpha2
    ) {
        float p1Lx = p1x + r1x, p1Ly = p1y + r1y, p1Lz = p1z + r1z;
        float p1Rx = p1x - r1x, p1Ry = p1y - r1y, p1Rz = p1z - r1z;
        float p2Lx = p2x + r2x, p2Ly = p2y + r2y, p2Lz = p2z + r2z;
        float p2Rx = p2x - r2x, p2Ry = p2y - r2y, p2Rz = p2z - r2z;

        consumer.addVertex(pose, p1Lx, p1Ly, p1Lz).setColor(r, g, b, edgeAlpha1);
        consumer.addVertex(pose, p1x, p1y, p1z).setColor(r, g, b, centerAlpha1);
        consumer.addVertex(pose, p2x, p2y, p2z).setColor(r, g, b, centerAlpha2);
        consumer.addVertex(pose, p2Lx, p2Ly, p2Lz).setColor(r, g, b, edgeAlpha2);

        consumer.addVertex(pose, p1x, p1y, p1z).setColor(r, g, b, centerAlpha1);
        consumer.addVertex(pose, p1Rx, p1Ry, p1Rz).setColor(r, g, b, edgeAlpha1);
        consumer.addVertex(pose, p2Rx, p2Ry, p2Rz).setColor(r, g, b, edgeAlpha2);
        consumer.addVertex(pose, p2x, p2y, p2z).setColor(r, g, b, centerAlpha2);
    }

    static void emitDoubleSidedQuad(
            VertexConsumer consumer, Matrix4f pose,
            float x1, float y1, float z1, int r1, int g1, int b1, int a1,
            float x2, float y2, float z2, int r2, int g2, int b2, int a2,
            float x3, float y3, float z3, int r3, int g3, int b3, int a3,
            float x4, float y4, float z4, int r4, int g4, int b4, int a4
    ) {
        consumer.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1);
        consumer.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2);
        consumer.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3);
        consumer.addVertex(pose, x4, y4, z4).setColor(r4, g4, b4, a4);

        consumer.addVertex(pose, x4, y4, z4).setColor(r4, g4, b4, a4);
        consumer.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3);
        consumer.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2);
        consumer.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1);
    }

    static void emitDoubleSidedQuad(
            VertexConsumer consumer, Matrix4f pose,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            int r, int g, int b, int a
    ) {
        emitDoubleSidedQuad(consumer, pose,
                x1, y1, z1, r, g, b, a,
                x2, y2, z2, r, g, b, a,
                x3, y3, z3, r, g, b, a,
                x4, y4, z4, r, g, b, a);
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
        VisualTheme theme = state.theme() == null ? VisualTheme.DEFAULT : state.theme();
        int fallbackRgb = VisualTheme.packRgb(r, g, b);
        double animTime = state.animTime();
        int totalQuads = state.domeQuads().size();

        int longitudeBands = 24;
        int latitudeBands = Math.max(1, totalQuads / longitudeBands);

        // Decoration density scales with camera distance so far-away domes — or many simultaneously
        // tracked projectiles (e.g. a ghast barrage) — don't emit thousands of billboard quads per
        // frame. The base dome shell geometry is untouched; only particle/glyph/sprite passes LOD,
        // fading to zero beyond ~60 blocks.
        float camDist = (float) cameraLocal.length();
        float density = Mth.clamp(60.0f / Math.max(1.0f, camDist), 0.0f, 1.0f);

        // Camera basis vectors for dome particle/glyph billboarding
        Vec3 camLook = safeNormalize(cameraLocal, FORWARD);
        Vec3 camUpRef = Math.abs(camLook.y) > 0.99 ? FORWARD : UP;
        Vec3 camRight = safeNormalize(camUpRef.cross(camLook), RIGHT);
        Vec3 camUp = safeNormalize(camLook.cross(camRight), UP);

        // Every dome quad uses the same RGB or theme-evaluated RGB, with a normal alpha blend.
        for (int qIdx = 0; qIdx < totalQuads; qIdx++) {
            PredictionRenderData.DomeQuad quad = state.domeQuads().get(qIdx);
            int base1 = Mth.clamp((int) (quad.alpha1() * pulseFactor * fade), 0, maxAlpha);
            int base2 = Mth.clamp((int) (quad.alpha2() * pulseFactor * fade), 0, maxAlpha);

            int lat = qIdx / longitudeBands;
            int lon = qIdx % longitudeBands;
            float latProgress = (float) lat / (float) latitudeBands;
            float lonProgress = (float) lon / (float) longitudeBands;

            int qr = r, qg = g, qb = b;
            float alphaMult = 1.0f;
            if (theme.isCustomTheme()) {
                int quadRgb = theme.getDomeColorPacked(quad.p1(), cameraLocal, latProgress, lonProgress, animTime, fallbackRgb);
                qr = VisualTheme.extractR(quadRgb);
                qg = VisualTheme.extractG(quadRgb);
                qb = VisualTheme.extractB(quadRgb);
                alphaMult = theme.getDomeAlphaModulation(latProgress, lonProgress, animTime);
            }

            int a1 = Mth.clamp((int) (fresnelAlpha(quad.p1(), base1, cameraLocal, fresnelStrength, maxAlpha, fade) * alphaMult), 0, maxAlpha);
            int a2 = Mth.clamp((int) (fresnelAlpha(quad.p2(), base1, cameraLocal, fresnelStrength, maxAlpha, fade) * alphaMult), 0, maxAlpha);
            int a3 = Mth.clamp((int) (fresnelAlpha(quad.p3(), base2, cameraLocal, fresnelStrength, maxAlpha, fade) * alphaMult), 0, maxAlpha);
            int a4 = Mth.clamp((int) (fresnelAlpha(quad.p4(), base2, cameraLocal, fresnelStrength, maxAlpha, fade) * alphaMult), 0, maxAlpha);

            consumer.addVertex(positionMatrix, (float) quad.p1().x, (float) quad.p1().y, (float) quad.p1().z).setColor(qr, qg, qb, a1);
            consumer.addVertex(positionMatrix, (float) quad.p2().x, (float) quad.p2().y, (float) quad.p2().z).setColor(qr, qg, qb, a2);
            consumer.addVertex(positionMatrix, (float) quad.p3().x, (float) quad.p3().y, (float) quad.p3().z).setColor(qr, qg, qb, a3);
            consumer.addVertex(positionMatrix, (float) quad.p4().x, (float) quad.p4().y, (float) quad.p4().z).setColor(qr, qg, qb, a4);
        }

        // Theme-specific decorative overlays across the shockwave dome (Celestial stars, Matrix code rain, etc.)
        if (theme.isCustomTheme()) {
            PredictionThemeRenderer.renderDomeThemeDecorations(
                    consumer, state, theme, positionMatrix, totalQuads, maxAlpha, density,
                    animTime, cameraLocal, camLook, camRight, camUp);
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

        double vx = vertex.x, vy = vertex.y, vz = vertex.z;
        double nLenSq = vx * vx + vy * vy + vz * vz;
        double invNLen = nLenSq > 1e-7 ? 1.0 / Math.sqrt(nLenSq) : 0.0;
        double nx = invNLen != 0.0 ? vx * invNLen : 0.0;
        double ny = invNLen != 0.0 ? vy * invNLen : 1.0;
        double nz = invNLen != 0.0 ? vz * invNLen : 0.0;

        double dx = cameraLocal.x - vx;
        double dy = cameraLocal.y - vy;
        double dz = cameraLocal.z - vz;
        double vLenSq = dx * dx + dy * dy + dz * dz;
        double invVLen = vLenSq > 1e-7 ? 1.0 / Math.sqrt(vLenSq) : 0.0;
        double viewX = invVLen != 0.0 ? dx * invVLen : 0.0;
        double viewY = invVLen != 0.0 ? dy * invVLen : 1.0;
        double viewZ = invVLen != 0.0 ? dz * invVLen : 0.0;

        double dot = nx * viewX + ny * viewY + nz * viewZ;
        float ndv = (float) Math.max(0.0, Math.abs(dot));
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
    float fade,
    VisualTheme theme
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
    float fresnelStrength,
    VisualTheme theme,
    double animTime,
    Vec3 trajectoryIntercept
) {}
