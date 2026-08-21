package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

import static com.simonconrad.fireballpredictor.client.render.PredictionFeatureRenderer.DOWN;
import static com.simonconrad.fireballpredictor.client.render.PredictionFeatureRenderer.FORWARD;
import static com.simonconrad.fireballpredictor.client.render.PredictionFeatureRenderer.RIGHT;
import static com.simonconrad.fireballpredictor.client.render.PredictionFeatureRenderer.UP;
import static com.simonconrad.fireballpredictor.client.render.PredictionFeatureRenderer.emitDoubleSidedQuad;
import static com.simonconrad.fireballpredictor.client.render.PredictionFeatureRenderer.emitRibbonQuad;
import static com.simonconrad.fireballpredictor.client.render.PredictionFeatureRenderer.safeNormalize;

/**
 * Handles 3D world space visual theme passes, animated particle/glyph overlays,
 * and procedural geometry generation for both trajectory ribbons and blast domes.
 */
public final class PredictionThemeRenderer {

    /** Reference step length (~0.616 blocks) matching preview gallery density for trail decorations. */
    private static final double TRAIL_DECOR_STEP = Math.sqrt(
            Math.pow(6.5 / (2.0 * Math.sin(Math.PI / 16.0)), 2.0) + 8.0 * 8.0) / 30.0;

    /** Epsilon for half-open arc-length slot intervals across segment boundaries. */
    private static final double TRAIL_DECOR_EPS = 1.0e-6;

    private PredictionThemeRenderer() {
    }

    /** Converts arc length (blocks) into gallery step units for consistent animation frequencies. */
    private static float galleryUnits(double distFromStart) {
        return (float) (distFromStart / TRAIL_DECOR_STEP);
    }

    /** Index of the first decoration slot at or after {@code segStartDist}. */
    private static int firstDecorSlot(double segStartDist, int stepCount) {
        return (int) Math.floor((segStartDist + TRAIL_DECOR_EPS) / (stepCount * TRAIL_DECOR_STEP));
    }

    /**
     * Determines whether the active theme requires computing a billboard camera up-basis vector
     * for trajectory overlay geometry.
     */
    public static boolean requiresTrailUpBasis(VisualTheme theme) {
        return theme == VisualTheme.INFERNO || theme == VisualTheme.GHOST
                || theme == VisualTheme.MATRIX || theme == VisualTheme.AURORA
                || theme == VisualTheme.SINGULARITY || theme == VisualTheme.SAKURA
                || theme == VisualTheme.CRYSTAL || theme == VisualTheme.ARCADE;
    }

    /**
     * Renders per-segment thematic decorative passes along the 3D trajectory ribbon.
     * Trail decorations anchor to cumulative arc length ({@code segStartDist}) for uniform spacing.
     */
    public static void renderTrailThemeSegment(
            VertexConsumer consumer, Matrix4f positionMatrix, VisualTheme theme,
            List<Vec3> path, int i, Vec3 p1, Vec3 p2, Vec3 rightDir, Vec3 trailUp,
            float width1, float width2, int centerAlpha1, int baseCenterAlpha1,
            float alphaBlend1, float pulse1, float dash1, float fade, int maxAlpha,
            double animTime, double segStartDist
    ) {
        // Pass 3: Branching Electric Arcs (High-voltage plasma with blistering white core & electric cyan corona)
        if (theme == VisualTheme.ELECTRIC_ARC && i < path.size() - 2) {
            int arcStep = (int) (animTime * 14.0);
            int seed = (i * 37 + arcStep * 19) % 11;
            if (seed < 5) {
                float jSign = (seed % 2 == 0) ? 1.0f : -1.0f;
                float jDist = (0.30f + (seed * 0.15f)) * width1 * 2.8f;
                Vec3 jMid = p1.add(p2).scale(0.5)
                        .add(rightDir.scale(jSign * jDist))
                        .add(0, (seed == 0 ? 0.20 : (seed == 1 ? -0.18 : 0.10)) * width1, 0);

                float arcWidth = width1 * 0.16f;
                int arcAlpha = Mth.clamp((int) (centerAlpha1 * 1.15f), 0, maxAlpha);

                // Outer ionized gas corona (electric cyan & cobalt)
                emitRibbonQuad(consumer, positionMatrix, p1, jMid,
                        rightDir.scale(arcWidth), rightDir.scale(arcWidth),
                        0, 229, 255, 0, 0, (int) (arcAlpha * 0.70f), (int) (arcAlpha * 0.70f));
                emitRibbonQuad(consumer, positionMatrix, jMid, p2,
                        rightDir.scale(arcWidth), rightDir.scale(arcWidth),
                        2, 132, 199, 0, 0, (int) (arcAlpha * 0.70f), (int) (arcAlpha * 0.70f));

                // Inner high-voltage plasma core streamer (blistering white)
                float coreArcWidth = arcWidth * 0.50f;
                emitRibbonQuad(consumer, positionMatrix, p1, jMid,
                        rightDir.scale(coreArcWidth), rightDir.scale(coreArcWidth),
                        255, 255, 255, 0, 0, arcAlpha, arcAlpha);
                emitRibbonQuad(consumer, positionMatrix, jMid, p2,
                        rightDir.scale(coreArcWidth), rightDir.scale(coreArcWidth),
                        255, 255, 255, 0, 0, arcAlpha, arcAlpha);
            }
        }

        // Pass 4: Sculk Wavy Organic Soul Tendrils
        if (theme == VisualTheme.SCULK_VOID && i < path.size() - 1) {
            double segDist = p1.distanceTo(p2);
            int subSteps = Math.max(1, (int) Math.ceil(segDist / TRAIL_DECOR_STEP - 1.0e-9));
            for (int s = 0; s < subSteps; s++) {
                float fA = (float) s / subSteps;
                float fB = (float) (s + 1) / subSteps;
                float guA = galleryUnits(segStartDist + segDist * fA);
                float guB = galleryUnits(segStartDist + segDist * fB);
                float wA = Mth.lerp(fA, width1, width2);
                float wB = Mth.lerp(fB, width1, width2);

                float wavePhaseA = (float) (animTime * -4.5 + guA * 0.45);
                float wavePhaseB = (float) (animTime * -4.5 + guB * 0.45);
                float waveLatA = (float) (Math.sin(wavePhaseA) * 0.60 + Math.sin(wavePhaseA * 2.1) * 0.25) * wA;
                float waveLatB = (float) (Math.sin(wavePhaseB) * 0.60 + Math.sin(wavePhaseB * 2.1) * 0.25) * wB;
                float waveVertA = (float) (Math.cos(wavePhaseA * 1.5) * 0.35) * wA;
                float waveVertB = (float) (Math.cos(wavePhaseB * 1.5) * 0.35) * wB;

                Vec3 sA = p1.lerp(p2, fA);
                Vec3 sB = p1.lerp(p2, fB);
                Vec3 w1 = sA.add(rightDir.scale(waveLatA)).add(0, waveVertA, 0);
                Vec3 w2 = sB.add(rightDir.scale(waveLatB)).add(0, waveVertB, 0);

                float tendrilWidthA = wA * 0.22f;
                float tendrilWidthB = wB * 0.22f;
                int soulAlpha = Mth.clamp((int) (baseCenterAlpha1 * alphaBlend1 * pulse1 * dash1 * fade * 1.15f), 0, maxAlpha);

                emitRibbonQuad(consumer, positionMatrix, w1, w2,
                        rightDir.scale(tendrilWidthA), rightDir.scale(tendrilWidthB),
                        0, 245, 212, 0, 0, soulAlpha, soulAlpha);
            }
        }

        // Pass 5: Inferno Dynamic Multi-Tier Licking Flames & Floating Embers (Constant small spacing)
        if (theme == VisualTheme.INFERNO && i < path.size() - 1) {
            double segDist = p1.distanceTo(p2);
            int flameCount = Math.max(1, (int) Math.round(segDist / 0.35));
            for (int f = 0; f < flameCount; f++) {
                double subT = (f + 0.5) / flameCount;
                Vec3 fBase = p1.lerp(p2, subT);

                float flamePhase1 = (float) (animTime * 7.5 - (i * 3 + f) * 0.55);
                float flamePhase2 = (float) (animTime * 6.0 + (i * 3 + f) * 0.70);

                float flameH1 = (0.35f + 0.45f * Math.max(0.0f, (float) Math.sin(flamePhase1))) * width1 * 2.6f;
                float flameH2 = (0.25f + 0.35f * Math.max(0.0f, (float) Math.sin(flamePhase2))) * width1 * 2.0f;

                float flameOff1 = (float) (Math.sin(flamePhase1 * 1.6) * 0.45f) * width1;
                float flameOff2 = (float) (Math.cos(flamePhase2 * 1.4) * -0.40f) * width1;

                Vec3 fTip1 = fBase.add(rightDir.scale(flameOff1)).add(0, flameH1, 0);
                Vec3 fTip2 = fBase.add(rightDir.scale(flameOff2)).add(0, flameH2, 0);

                float flameW = width1 * 0.22f;
                int flameAlpha = Mth.clamp((int) (centerAlpha1 * 1.30f), 0, maxAlpha);

                emitRibbonQuad(consumer, positionMatrix, fBase, fTip1,
                        rightDir.scale(flameW), rightDir.scale(flameW * 0.10f),
                        255, 100, 0, 0, 0, flameAlpha, flameAlpha / 2);
                emitRibbonQuad(consumer, positionMatrix, fBase, fTip2,
                        rightDir.scale(flameW * 0.8f), rightDir.scale(flameW * 0.08f),
                        255, 200, 30, 0, 0, flameAlpha, flameAlpha / 3);

                if (f == 0 && i % 2 == 0) {
                    float emberRise = ((float) ((animTime * 1.8 + i * 0.37) % 1.0)) * width1 * 3.2f;
                    float emberSway = (float) Math.sin(animTime * 3.0 + i) * width1 * 0.5f;
                    Vec3 emberPos = fBase.add(rightDir.scale(emberSway)).add(0, emberRise, 0);
                    float emberSize = width1 * 0.07f;
                    int emberAlpha = Mth.clamp((int) ((1.0f - emberRise / (width1 * 3.2f)) * maxAlpha * 1.2f), 0, maxAlpha);
                    emitBillboardGlint(consumer, positionMatrix, emberPos, rightDir, trailUp, emberSize, 255, 220, 100, emberAlpha);
                }
            }
        }

        // Pass 6: Ghost Spectral Soul Wisps & Tendrils
        if (theme == VisualTheme.GHOST && i < path.size() - 1) {
            double segDist = p1.distanceTo(p2);
            int subSteps = Math.max(1, (int) Math.ceil(segDist / TRAIL_DECOR_STEP - 1.0e-9));
            for (int s = 0; s < subSteps; s++) {
                float fA = (float) s / subSteps;
                float fB = (float) (s + 1) / subSteps;
                float wA = Mth.lerp(fA, width1, width2);

                float wispPhase = (float) (animTime * 2.8 - galleryUnits(segStartDist + segDist * fA) * 0.38);
                float wispLat = (float) (Math.sin(wispPhase) * 0.70 + Math.sin(wispPhase * 2.3) * 0.30) * wA;
                float wispVert = (float) (Math.cos(wispPhase * 1.7) * 0.45) * wA;

                Vec3 sA = p1.lerp(p2, fA);
                Vec3 sB = p1.lerp(p2, fB);
                Vec3 w1 = sA.add(rightDir.scale(wispLat)).add(0, wispVert, 0);
                Vec3 w2 = sB.add(rightDir.scale(wispLat * 0.8)).add(0, wispVert * 0.8, 0);

                float wispW = wA * 0.20f;
                int soulAlpha = Mth.clamp((int) (centerAlpha1 * 1.20f), 0, maxAlpha);

                emitRibbonQuad(consumer, positionMatrix, w1, w2,
                        rightDir.scale(wispW), rightDir.scale(wispW),
                        45, 212, 191, 0, 0, soulAlpha, soulAlpha);
            }

            // Rising spirit orbs
            double segEnd = segStartDist + segDist;
            for (int k = firstDecorSlot(segStartDist, 3); k * 3 * TRAIL_DECOR_STEP < segEnd - TRAIL_DECOR_EPS; k++) {
                double anchor = k * 3 * TRAIL_DECOR_STEP;
                if (anchor < segStartDist - TRAIL_DECOR_EPS) {
                    continue;
                }
                float t = (float) ((anchor - segStartDist) / Math.max(segDist, TRAIL_DECOR_EPS));
                float gu = k * 3.0f;
                float wK = Mth.lerp(t, width1, width2);

                float orbRise = ((float) ((animTime * 1.2 + gu * 0.29) % 1.0)) * wK * 2.6f;
                float orbSway = (float) Math.sin(animTime * 2.0 + gu * 1.5) * wK * 0.45f;
                Vec3 orbPos = p1.lerp(p2, t).add(rightDir.scale(orbSway)).add(0, orbRise, 0);
                float orbSize = wK * 0.10f;
                int orbAlpha = Mth.clamp((int) ((1.0f - orbRise / (wK * 2.6f)) * maxAlpha), 0, maxAlpha);
                emitBillboardGlint(consumer, positionMatrix, orbPos, rightDir, trailUp, orbSize, 153, 246, 228, orbAlpha);
            }
        }

        // Pass 7: Matrix Digital Glyphs on Trajectory
        if (theme == VisualTheme.MATRIX) {
            double segDist = p1.distanceTo(p2);
            double segEnd = segStartDist + segDist;
            for (int k = firstDecorSlot(segStartDist, 3); k * 3 * TRAIL_DECOR_STEP < segEnd - TRAIL_DECOR_EPS; k++) {
                double anchor = k * 3 * TRAIL_DECOR_STEP;
                if (anchor < segStartDist - TRAIL_DECOR_EPS) {
                    continue;
                }
                float t = (float) ((anchor - segStartDist) / Math.max(segDist, TRAIL_DECOR_EPS));
                float wK = Mth.lerp(t, width1, width2);
                int glyphIdx = (int) (animTime * 8.0 + k * 3) & 15;
                Vec3 gCenter = p1.lerp(p2, t).add(0, wK * 0.4f, 0);
                emitMatrixGlyph(consumer, positionMatrix, gCenter, rightDir, trailUp, wK * 0.12f, glyphIdx, 0, 255, 65, maxAlpha);
            }
        }

        // Pass 8: Aurora Borealis Drifting Ice Hexagon Glints
        if (theme == VisualTheme.AURORA && i % 3 == 0) {
            float crystalPhase = (float) ((animTime * 1.5 + i * 0.41) % 1.0);
            float crystalDrift = crystalPhase * width1 * 2.8f;
            float crystalSway = (float) Math.sin(animTime * 2.5 + i * 1.2) * width1 * 0.6f;
            Vec3 cPos = p1.add(rightDir.scale(crystalSway)).add(0, crystalDrift, 0);
            float cSize = width1 * 0.09f;
            int cAlpha = Mth.clamp((int) ((1.0f - crystalPhase) * maxAlpha * 1.2f), 0, maxAlpha);
            emitHexGlint(consumer, positionMatrix, cPos, rightDir, trailUp, cSize, 96, 239, 255, cAlpha);
        }

        // Pass 9: Singularity Orbiting Accretion Glints & Photon Ring Particles
        if (theme == VisualTheme.SINGULARITY) {
            double segDist = p1.distanceTo(p2);
            double segEnd = segStartDist + segDist;
            for (int k = firstDecorSlot(segStartDist, 1); k * TRAIL_DECOR_STEP < segEnd - TRAIL_DECOR_EPS; k++) {
                double anchor = k * TRAIL_DECOR_STEP;
                if (anchor < segStartDist - TRAIL_DECOR_EPS) {
                    continue;
                }
                float t = (float) ((anchor - segStartDist) / Math.max(segDist, TRAIL_DECOR_EPS));
                float gu = (float) k;
                float wK = Mth.lerp(t, width1, width2);

                float angle1 = (float) (animTime * 5.5 + gu * 1.2);
                float spiralR = wK * (0.8f + 0.3f * (float) Math.sin(animTime * 3.0 + gu));
                Vec3 base = p1.lerp(p2, t);
                Vec3 glint1 = base.add(rightDir.scale(Math.cos(angle1) * spiralR)).add(0, Math.sin(angle1) * spiralR, 0);
                Vec3 glint2 = base.subtract(rightDir.scale(Math.cos(angle1) * spiralR)).subtract(0, Math.sin(angle1) * spiralR, 0);
                float gSize = wK * 0.08f;
                int gAlpha = Mth.clamp((int) (centerAlpha1 * 1.25f), 0, maxAlpha);
                emitBillboardGlint(consumer, positionMatrix, glint1, rightDir, trailUp, gSize, 255, 125, 10, gAlpha);
                emitBillboardGlint(consumer, positionMatrix, glint2, rightDir, trailUp, gSize * 0.75f, 217, 70, 239, gAlpha);
            }
        }

        // Pass 10: Sakura Drift: Fluttering 5-Petal Blossoms & Drifting Petals
        if (theme == VisualTheme.SAKURA) {
            double segDist = p1.distanceTo(p2);
            double segEnd = segStartDist + segDist;

            // 5-petal flower every 2 steps
            for (int k = firstDecorSlot(segStartDist, 2); k * 2 * TRAIL_DECOR_STEP < segEnd - TRAIL_DECOR_EPS; k++) {
                double anchor = k * 2 * TRAIL_DECOR_STEP;
                if (anchor < segStartDist - TRAIL_DECOR_EPS) {
                    continue;
                }
                float t = (float) ((anchor - segStartDist) / Math.max(segDist, TRAIL_DECOR_EPS));
                float gu = k * 2.0f;
                float wK = Mth.lerp(t, width1, width2);
                float fAngle = (float) (animTime * 2.2 + gu * 0.75);
                float fOrbit = wK * 0.9f * (float) Math.sin(animTime * 1.8 + gu * 0.5);
                Vec3 flowerPos = p1.lerp(p2, t).add(rightDir.scale(fOrbit)).add(0, wK * 0.35f, 0);
                float fSize = wK * 0.12f;
                int fAlpha = Mth.clamp((int) (centerAlpha1 * 1.25f), 0, maxAlpha);
                emitSakuraFlower(consumer, positionMatrix, flowerPos, rightDir, trailUp, fSize, fAngle, 244, 114, 182, fAlpha);
            }

            // Individual drifting petal
            for (int k = firstDecorSlot(segStartDist, 1); k * TRAIL_DECOR_STEP < segEnd - TRAIL_DECOR_EPS; k++) {
                double anchor = k * TRAIL_DECOR_STEP;
                if (anchor < segStartDist - TRAIL_DECOR_EPS) {
                    continue;
                }
                float t = (float) ((anchor - segStartDist) / Math.max(segDist, TRAIL_DECOR_EPS));
                float gu = (float) k;
                float wK = Mth.lerp(t, width1, width2);
                float petalPhase = (float) ((animTime * 1.4 + gu * 0.29) % 1.0);
                float petalSway = (float) Math.sin(animTime * 2.8 + gu * 1.3) * wK * 1.1f;
                float petalFall = petalPhase * wK * 2.2f;
                Vec3 petalPos = p1.lerp(p2, t).add(rightDir.scale(petalSway)).add(0, wK * 0.6f - petalFall, 0);
                float petalSize = wK * 0.09f;
                int petalAlpha = Mth.clamp((int) ((1.0f - petalPhase * 0.5f) * maxAlpha), 0, maxAlpha);
                emitPetalGlint(consumer, positionMatrix, petalPos, rightDir, trailUp, petalSize, 251, 113, 133, petalAlpha);
            }
        }

        // Pass 11: Prismatic Crystal Faceted Octahedron Gemstones
        if (theme == VisualTheme.CRYSTAL && i % 3 == 0) {
            float cOrbit = (float) (animTime * 2.2 + i * 0.8);
            float cDist = width1 * 1.5f;
            Vec3 cPos = p1.add(rightDir.scale(Math.cos(cOrbit) * cDist)).add(0, Math.sin(cOrbit) * cDist * 0.7f, 0);
            float cSize = width1 * 0.11f;
            int cAlpha = Mth.clamp((int) (centerAlpha1 * 1.25f), 0, maxAlpha);
            boolean isFlash = (int) (animTime * 8.0 + i) % 4 == 0;
            int cR = isFlash ? 255 : 192;
            int cG = isFlash ? 255 : 132;
            int cB = isFlash ? 255 : 252;
            emitBillboardGlint(consumer, positionMatrix, cPos, rightDir, trailUp, cSize, cR, cG, cB, cAlpha);
        }

        // Pass 12: 8-Bit Arcade Matrix Sprites on Trajectory
        if (theme == VisualTheme.ARCADE) {
            double segDist = p1.distanceTo(p2);
            double segEnd = segStartDist + segDist;
            for (int k = firstDecorSlot(segStartDist, 3); k * 3 * TRAIL_DECOR_STEP < segEnd - TRAIL_DECOR_EPS; k++) {
                double anchor = k * 3 * TRAIL_DECOR_STEP;
                if (anchor < segStartDist - TRAIL_DECOR_EPS) {
                    continue;
                }
                float t = (float) ((anchor - segStartDist) / Math.max(segDist, TRAIL_DECOR_EPS));
                float wK = Mth.lerp(t, width1, width2);
                int spriteIdx = (int) (animTime * 5.0 + k * 3) & 7;
                Vec3 aCenter = p1.lerp(p2, t).add(0, wK * 0.45f, 0);
                int[] aCol = ThemeVisualAssets.getArcadeSpriteColor(spriteIdx);
                emitArcadeSprite(consumer, positionMatrix, aCenter, rightDir, trailUp, wK * 0.08f, spriteIdx, aCol[0], aCol[1], aCol[2], maxAlpha);
            }
        }
    }

    /**
     * Renders whole-path trajectory passes (e.g. Tactical HUD fighter jets).
     */
    public static void renderTrailThemeGlobal(
            VertexConsumer consumer, Matrix4f positionMatrix, VisualTheme theme,
            List<Vec3> path, int totalPathSteps, float baseWidth, int maxAlpha,
            double animTime
    ) {
        // Pass 13: Tactical HUD Escort Fighter Jets
        if (theme == VisualTheme.TACTICAL_HUD && totalPathSteps >= 1) {
            int numPlanes = 2;
            for (int pIdx = 0; pIdx < numPlanes; pIdx++) {
                float planePhase = (float) ((animTime * 0.40 + pIdx * 0.50) % 1.0);
                float pathT = planePhase * totalPathSteps;
                int segIdx = Mth.clamp((int) pathT, 0, path.size() - 2);
                float segFrac = pathT - segIdx;

                Vec3 pA = path.get(segIdx);
                Vec3 pB = path.get(segIdx + 1);
                Vec3 basePos = pA.lerp(pB, segFrac);
                Vec3 forward = safeNormalize(pB.subtract(pA), FORWARD);
                Vec3 right = safeNormalize(forward.cross(UP), RIGHT);
                Vec3 up = safeNormalize(right.cross(forward), UP);

                float sideSign = (pIdx == 0) ? 1.0f : -1.0f;
                float flankDist = baseWidth * 2.2f;
                float vertBob = (float) Math.sin(animTime * 4.0 + pIdx * Math.PI) * baseWidth * 0.4f;

                Vec3 planePos = basePos.add(right.scale(sideSign * flankDist)).add(0, vertBob + baseWidth * 0.5f, 0);
                float planeScale = baseWidth * 0.32f;

                emitTacticalAirplane(consumer, positionMatrix, planePos, forward, right, up, planeScale, 245, 158, 11, maxAlpha);

                // Afterburner contrail
                Vec3 jetExhaust = planePos.subtract(forward.scale(planeScale * 0.9f));
                Vec3 jetTrailEnd = planePos.subtract(forward.scale(planeScale * 3.2f));
                emitRibbonQuad(consumer, positionMatrix, jetExhaust, jetTrailEnd,
                        right.scale(planeScale * 0.20f), right.scale(planeScale * 0.02f),
                        254, 240, 138, 0, 0, maxAlpha, 0);
            }
        }
    }

    /**
     * Renders 3D volumetric dome effects and billboard decorations.
     */
    public static void renderDomeThemeDecorations(
            VertexConsumer consumer, DomeRenderState state, VisualTheme theme,
            Matrix4f positionMatrix, int totalQuads, int maxAlpha, float density,
            double animTime, Vec3 cameraLocal, Vec3 camLook, Vec3 camRight, Vec3 camUp
    ) {
        if (totalQuads <= 0) {
            return;
        }

        // Celestial Twinkling Stars (inside the dome volume)
        if (theme == VisualTheme.CELESTIAL) {
            int starCount = (int) (96 * density);
            for (int s = 0; s < starCount; s++) {
                int targetQuadIdx = (s * 23 + 7) % totalQuads;
                PredictionRenderData.DomeQuad q = state.domeQuads().get(targetQuadIdx);
                float innerScale = 0.30f + ((s * 19) % 100) / 100.0f * 0.58f;
                Vec3 pCenter = q.p1().add(q.p2()).add(q.p3()).add(q.p4()).scale(0.25 * innerScale);

                float twinkle = (float) Math.sin(animTime * 3.0 + s * 1.6);
                if (twinkle > -0.2f) {
                    float starSize = 0.04f + 0.02f * Math.max(0.0f, twinkle);
                    int starAlpha = Mth.clamp((int) ((0.7f + 0.3f * twinkle) * maxAlpha * 1.35f), 0, maxAlpha);
                    int sCol = twinkle > 0.4f ? 0xFFFFFF : (twinkle > 0.0f ? 0xE0F2FE : 0xDDD6FE);
                    int sR = VisualTheme.extractR(sCol);
                    int sG = VisualTheme.extractG(sCol);
                    int sB = VisualTheme.extractB(sCol);

                    emitBillboardGlint(consumer, positionMatrix, pCenter, camRight, camUp, starSize, sR, sG, sB, starAlpha);
                }
            }
        }

        // Falling Matrix Digital Code Rain on Dome (Aligned directly to dome radius)
        if (theme == VisualTheme.MATRIX) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();
            int columns = (int) (16 * density);
            for (int col = 0; col < columns; col++) {
                float colAngle = (float) (col * 2.0 * Math.PI / columns);
                float speed = 1.4f + ((col * 7) % 5) * 0.35f;
                float streamY = (float) ((animTime * speed + (col * 13 % 100) / 100.0f) % 1.0);

                for (int charIdx = 0; charIdx < 4; charIdx++) {
                    float charProgress = (float) ((streamY - charIdx * 0.12 + 1.0) % 1.0);
                    float theta = (float) (0.06 * Math.PI + charProgress * 0.42 * Math.PI);
                    float rDist = domeRadius * 0.985f * (float) Math.sin(theta);
                    float yDist = domeRadius * 0.985f * (float) Math.cos(theta);

                    float cx = (float) (Math.cos(colAngle) * rDist);
                    float cy = yDist;
                    float cz = (float) (Math.sin(colAngle) * rDist);

                    int glyph = (int) (animTime * 6.0 + col * 5 + charIdx) & 15;
                    int gR = (charIdx == 0) ? 230 : 0;
                    int gG = 255;
                    int gB = (charIdx == 0) ? 230 : (charIdx == 1 ? 65 : 30);
                    int gAlpha = (charIdx == 0) ? maxAlpha : Mth.clamp(maxAlpha - charIdx * 25, 30, maxAlpha);

                    emitMatrixGlyph(consumer, positionMatrix, cx, cy, cz, camRight, camUp, 0.045f, glyph, gR, gG, gB, gAlpha);
                }
            }
        }

        // Inferno Volcanic Rising Embers around Dome
        if (theme == VisualTheme.INFERNO) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();
            int emberCount = (int) (32 * density);
            for (int e = 0; e < emberCount; e++) {
                float eCol = (float) (e * 2.0 * Math.PI / 32);
                float eSpeed = 0.8f + ((e * 11) % 5) * 0.25f;
                float eProgress = (float) ((animTime * eSpeed + (e * 23 % 100) / 100.0f) % 1.0);
                float theta = (float) (0.08 * Math.PI + eProgress * 0.40 * Math.PI);
                float rDist = (domeRadius + 0.10f + eProgress * 0.4f) * (float) Math.sin(theta);
                float yDist = (domeRadius + 0.10f + eProgress * 0.4f) * (float) Math.cos(theta);

                float cx = (float) (Math.cos(eCol) * rDist);
                float cy = yDist;
                float cz = (float) (Math.sin(eCol) * rDist);

                float flicker = (float) Math.sin(animTime * 8.0 + e * 2.1);
                if (flicker > -0.3f) {
                    float emberSize = 0.035f + 0.015f * Math.max(0.0f, flicker);
                    int emberAlpha = Mth.clamp((int) ((1.0f - eProgress * 0.7f) * maxAlpha * 1.2f), 0, maxAlpha);
                    int eColor = flicker > 0.3f ? 0xFFFF66 : (flicker > 0.0f ? 0xFF8800 : 0xDC2626);
                    emitBillboardGlint(consumer, positionMatrix, cx, cy, cz, camRight, camUp, emberSize,
                            VisualTheme.extractR(eColor), VisualTheme.extractG(eColor), VisualTheme.extractB(eColor), emberAlpha);
                }
            }
        }

        // Ghost Spiritual Vortex Soul Orbs inside Dome
        if (theme == VisualTheme.GHOST) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();
            int orbCount = (int) (24 * density);
            for (int s = 0; s < orbCount; s++) {
                float angle = (float) (animTime * 1.2 + s * 2.0 * Math.PI / 24);
                float sProgress = ((float) ((animTime * 0.4 + s * 0.13) % 1.0));
                float theta = (float) (0.10 * Math.PI + sProgress * 0.38 * Math.PI);
                float rDist = (domeRadius * 0.96f) * (float) Math.sin(theta);
                float yDist = (domeRadius * 0.96f) * (float) Math.cos(theta);

                float cx = (float) (Math.cos(angle) * rDist);
                float cy = yDist;
                float cz = (float) (Math.sin(angle) * rDist);

                float pulse = 0.6f + 0.4f * (float) Math.sin(animTime * 3.0 + s * 1.8);
                float soulSize = 0.055f * pulse;
                int soulAlpha = Mth.clamp((int) (pulse * maxAlpha * 0.95f), 0, maxAlpha);
                emitBillboardGlint(consumer, positionMatrix, cx, cy, cz, camRight, camUp, soulSize, 45, 212, 191, soulAlpha);
            }
        }

        // Electric Arc Crackling High-Voltage Plasma Lightning Discharge across Dome Shell
        if (theme == VisualTheme.ELECTRIC_ARC) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();
            int numLightningArcs = (int) (10 * density);
            int timeStep = (int) (animTime * 14.0);

            int arcAlpha = Mth.clamp((int) (maxAlpha * 1.35f), 0, maxAlpha);
            float arcWidth = domeRadius * 0.035f;

            Vec3 interceptDir = safeNormalize(state.trajectoryIntercept(), UP);
            Vec3 interceptRef = Math.abs(interceptDir.y) > 0.99 ? FORWARD : UP;
            Vec3 interceptRight = safeNormalize(interceptRef.cross(interceptDir), RIGHT);
            Vec3 interceptUp = safeNormalize(interceptDir.cross(interceptRight), UP);

            float shellRadius = domeRadius * 1.015f;

            for (int a = 0; a < numLightningArcs; a++) {
                int arcSeed = (a * 31 + timeStep * 17) % 7;
                if (arcSeed > 3) continue;

                float baseAzimuth = (float) ((a * 2.0 * Math.PI / numLightningArcs) + ((arcSeed * 13) % 100) / 100.0f * 0.5f);

                int segments = 4;
                float prevX = (float) (interceptDir.x * shellRadius);
                float prevY = (float) Math.max(0.0, interceptDir.y * shellRadius);
                float prevZ = (float) (interceptDir.z * shellRadius);

                for (int s = 1; s <= segments; s++) {
                    float sFrac = (float) s / segments;
                    float theta = sFrac * 0.44f * (float) Math.PI;

                    int jitterSeed = (a * 19 + s * 29 + timeStep * 11) % 100;
                    float azJitter = (jitterSeed - 50) / 50.0f * 0.22f;
                    float thJitter = (((jitterSeed * 7) % 100) - 50) / 50.0f * 0.06f;

                    float curTheta = Mth.clamp(theta + thJitter, 0.02f * (float) Math.PI, 0.48f * (float) Math.PI);
                    float curAz = baseAzimuth + azJitter;

                    float cosTheta = (float) Math.cos(curTheta);
                    float sinTheta = (float) Math.sin(curTheta);
                    float cosAz = (float) Math.cos(curAz);
                    float sinAz = (float) Math.sin(curAz);

                    double dirX = interceptDir.x * cosTheta + (interceptRight.x * cosAz + interceptUp.x * sinAz) * sinTheta;
                    double dirY = interceptDir.y * cosTheta + (interceptRight.y * cosAz + interceptUp.y * sinAz) * sinTheta;
                    double dirZ = interceptDir.z * cosTheta + (interceptRight.z * cosAz + interceptUp.z * sinAz) * sinTheta;

                    float curX = (float) (dirX * shellRadius);
                    float curY = (float) Math.max(0.0, dirY * shellRadius);
                    float curZ = (float) (dirZ * shellRadius);

                    double dx = curX - prevX, dy = curY - prevY, dz = curZ - prevZ;
                    double lenSq = dx * dx + dy * dy + dz * dz;
                    double invLen = lenSq > 1e-7 ? 1.0 / Math.sqrt(lenSq) : 0.0;
                    double segDirX = invLen != 0.0 ? dx * invLen : 0.0;
                    double segDirY = invLen != 0.0 ? dy * invLen : -1.0;
                    double segDirZ = invLen != 0.0 ? dz * invLen : 0.0;

                    double px = camLook.y * segDirZ - camLook.z * segDirY;
                    double py = camLook.z * segDirX - camLook.x * segDirZ;
                    double pz = camLook.x * segDirY - camLook.y * segDirX;
                    double pLenSq = px * px + py * py + pz * pz;
                    double invPLen = pLenSq > 1e-7 ? 1.0 / Math.sqrt(pLenSq) : 0.0;
                    float srX = (float) (invPLen != 0.0 ? px * invPLen : camRight.x);
                    float srY = (float) (invPLen != 0.0 ? py * invPLen : camRight.y);
                    float srZ = (float) (invPLen != 0.0 ? pz * invPLen : camRight.z);

                    float rX = srX * arcWidth, rY = srY * arcWidth, rZ = srZ * arcWidth;

                    emitRibbonQuad(consumer, positionMatrix,
                            prevX, prevY, prevZ, curX, curY, curZ,
                            rX, rY, rZ, rX, rY, rZ,
                            255, 255, 255, 0, 0, arcAlpha, arcAlpha);

                    prevX = curX;
                    prevY = curY;
                    prevZ = curZ;
                }
            }
        }

        // Aurora Borealis Polar Crown & Ice Glints
        if (theme == VisualTheme.AURORA) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();
            int auroraCount = (int) (32 * density);
            for (int a = 0; a < auroraCount; a++) {
                float aAngle = (float) (animTime * 1.1 + a * 2.0 * Math.PI / 32);
                float aProg = (float) ((animTime * 0.5 + a * 0.17) % 1.0);
                float theta = (float) (0.05 * Math.PI + aProg * 0.42 * Math.PI);
                float rDist = domeRadius * 0.98f * (float) Math.sin(theta);
                float yDist = domeRadius * 0.98f * (float) Math.cos(theta);
                float cx = (float) (Math.cos(aAngle) * rDist);
                float cy = yDist;
                float cz = (float) (Math.sin(aAngle) * rDist);
                float aPulse = 0.6f + 0.4f * (float) Math.sin(animTime * 3.5 + a * 2.0);
                float aSize = 0.045f * aPulse;
                int aAlpha = Mth.clamp((int) (aPulse * maxAlpha * 1.1f), 0, maxAlpha);
                emitHexGlint(consumer, positionMatrix, cx, cy, cz, camRight, camUp, aSize, 96, 239, 255, aAlpha);
            }
        }

        // Singularity: 3D Black Hole Shader Effect
        if (theme == VisualTheme.SINGULARITY) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();
            int sectors = (int) (64 * density);
            float rEH = domeRadius * 0.44f;
            float rPhoton = domeRadius * 0.58f;
            float rMid = domeRadius * 0.92f;
            float rOuter = domeRadius * 1.40f;

            float diskSpin = (float) (animTime * 2.8);

            if (sectors >= 12) {
                // 1. Central Event Horizon shadow disc
                int voidSectors = 8;
                int voidAlpha = Mth.clamp((int) (maxAlpha * 0.35f), 0, maxAlpha);
                for (int s = 0; s < voidSectors; s++) {
                    double a1 = s * 2.0 * Math.PI / voidSectors;
                    double a2 = (s + 1) * 2.0 * Math.PI / voidSectors;

                    float p1x = (float) (Math.cos(a1) * rEH);
                    float p1z = (float) (Math.sin(a1) * rEH);
                    float p2x = (float) (Math.cos(a2) * rEH);
                    float p2z = (float) (Math.sin(a2) * rEH);

                    emitDoubleSidedQuad(consumer, positionMatrix,
                            0.0f, 0.10f, 0.0f,
                            p1x, 0.10f, p1z,
                            p2x, 0.10f, p2z,
                            0.0f, 0.10f, 0.0f,
                            4, 1, 10, voidAlpha);
                }

                // 2. Photon Sphere Gravitational Lensing Boundary Ring
                for (int s = 0; s < sectors; s++) {
                    double a1 = s * 2.0 * Math.PI / sectors + diskSpin * 0.5;
                    double a2 = (s + 1) * 2.0 * Math.PI / sectors + diskSpin * 0.5;

                    float shimmer1 = 0.85f + 0.15f * (float) Math.sin(animTime * 6.0 + a1 * 4.0);
                    float shimmer2 = 0.85f + 0.15f * (float) Math.sin(animTime * 6.0 + a2 * 4.0);

                    float in1x = (float) (Math.cos(a1) * rEH);
                    float in1z = (float) (Math.sin(a1) * rEH);
                    float in2x = (float) (Math.cos(a2) * rEH);
                    float in2z = (float) (Math.sin(a2) * rEH);
                    float out1x = (float) (Math.cos(a1) * rPhoton);
                    float out1z = (float) (Math.sin(a1) * rPhoton);
                    float out2x = (float) (Math.cos(a2) * rPhoton);
                    float out2z = (float) (Math.sin(a2) * rPhoton);

                    int pAlpha1 = Mth.clamp((int) (maxAlpha * shimmer1), 0, maxAlpha);
                    int pAlpha2 = Mth.clamp((int) (maxAlpha * shimmer2), 0, maxAlpha);

                    emitDoubleSidedQuad(consumer, positionMatrix,
                            in1x, 0.105f, in1z, 4, 1, 10, maxAlpha,
                            out1x, 0.105f, out1z, 255, 255, 255, pAlpha1,
                            out2x, 0.105f, out2z, 255, 255, 255, pAlpha2,
                            in2x, 0.105f, in2z, 4, 1, 10, maxAlpha);
                }

                // 3. Multi-Tier Relativistic Accretion Vortex Disk
                int tiers = 3;
                float[] tierRadii = { rPhoton, rPhoton + (rMid - rPhoton) * 0.6f, rMid, rOuter };
                for (int t = 0; t < tiers; t++) {
                    float rInnerTier = tierRadii[t];
                    float rOuterTier = tierRadii[t + 1];
                    float yTier = (float) (0.11 + t * 0.005);

                    for (int s = 0; s < sectors; s++) {
                        double a1 = s * 2.0 * Math.PI / sectors + diskSpin;
                        double a2 = (s + 1) * 2.0 * Math.PI / sectors + diskSpin;

                        float c1 = (float) Math.cos(a1);
                        float s1 = (float) Math.sin(a1);
                        float c2 = (float) Math.cos(a2);
                        float s2 = (float) Math.sin(a2);

                        float in1x = c1 * rInnerTier, in1z = s1 * rInnerTier;
                        float in2x = c2 * rInnerTier, in2z = s2 * rInnerTier;
                        float out1x = c1 * rOuterTier, out1z = s1 * rOuterTier;
                        float out2x = c2 * rOuterTier, out2z = s2 * rOuterTier;

                        float doppler1 = 0.75f + 0.35f * (float) Math.cos(a1 - diskSpin);
                        float doppler2 = 0.75f + 0.35f * (float) Math.cos(a2 - diskSpin);

                        float spiral1 = (float) (a1 * 3.0 - animTime * 3.8 + (rInnerTier / domeRadius) * 4.5);
                        float spiral2 = (float) (a2 * 3.0 - animTime * 3.8 + (rInnerTier / domeRadius) * 4.5);
                        float density1 = 0.5f + 0.5f * (float) Math.sin(spiral1);
                        float density2 = 0.5f + 0.5f * (float) Math.sin(spiral2);

                        int colInR, colInG, colInB, colOutR, colOutG, colOutB;
                        int alphaIn, alphaOut;

                        if (t == 0) {
                            colInR = 255; colInG = 255; colInB = 255;
                            colOutR = (int) (255 * doppler1); colOutG = (int) (125 * doppler1); colOutB = (int) (10 * doppler1);
                            alphaIn = Mth.clamp((int) (maxAlpha * 1.2f * density1), 0, maxAlpha);
                            alphaOut = Mth.clamp((int) (maxAlpha * 0.95f * density2), 0, maxAlpha);
                        } else if (t == 1) {
                            colInR = (int) (255 * doppler1); colInG = (int) (125 * doppler1); colInB = (int) (10 * doppler1);
                            colOutR = 217; colOutG = 70; colOutB = 239;
                            alphaIn = Mth.clamp((int) (maxAlpha * 0.95f * density1), 0, maxAlpha);
                            alphaOut = Mth.clamp((int) (maxAlpha * 0.60f * density2), 0, maxAlpha);
                        } else {
                            colInR = 217; colInG = 70; colInB = 239;
                            colOutR = 67; colOutG = 56; colOutB = 202;
                            alphaIn = Mth.clamp((int) (maxAlpha * 0.60f * density1), 0, maxAlpha);
                            alphaOut = 0;
                        }

                        emitDoubleSidedQuad(consumer, positionMatrix,
                                in1x, yTier, in1z, colInR, colInG, colInB, alphaIn,
                                out1x, yTier, out1z, colOutR, colOutG, colOutB, alphaOut,
                                out2x, yTier, out2z, colOutR, colOutG, colOutB, alphaOut,
                                in2x, yTier, in2z, colInR, colInG, colInB, alphaIn);
                    }
                }
            }

            // 4. Relativistic Polar Jet
            float baseApexY = domeRadius * 0.90f;
            float topApexY = domeRadius * 1.85f;
            float jetCoreW = domeRadius * 0.045f;
            float jetSheathW = domeRadius * 0.12f;

            float rx = (float) camRight.x, ry = (float) camRight.y, rz = (float) camRight.z;

            // Ionization sheath
            emitRibbonQuad(consumer, positionMatrix,
                    0.0f, baseApexY, 0.0f, 0.0f, topApexY, 0.0f,
                    rx * jetSheathW, ry * jetSheathW, rz * jetSheathW,
                    rx * jetSheathW * 0.1f, ry * jetSheathW * 0.1f, rz * jetSheathW * 0.1f,
                    147, 51, 234, 0, 0, (int) (maxAlpha * 0.7f), 0);
            // Searing Hawking white-gold core
            emitRibbonQuad(consumer, positionMatrix,
                    0.0f, baseApexY, 0.0f, 0.0f, topApexY, 0.0f,
                    rx * jetCoreW, ry * jetCoreW, rz * jetCoreW,
                    rx * jetCoreW * 0.2f, ry * jetCoreW * 0.2f, rz * jetCoreW * 0.2f,
                    255, 251, 235, 0, 0, maxAlpha, 0);
        }

        // Sakura: High-Density 5-Petal Flower Base & Swirling Floral Petals
        if (theme == VisualTheme.SAKURA) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();

            int petalSegments = (int) (160 * density);
            float baseRot = (float) (animTime * 0.25);
            int lobeAlpha = Mth.clamp((int) (maxAlpha * 1.15f), 0, maxAlpha);

            if (petalSegments >= 24) {
                for (int s = 0; s < petalSegments; s++) {
                    double th1 = s * 2.0 * Math.PI / petalSegments;
                    double th2 = (s + 1) * 2.0 * Math.PI / petalSegments;

                    float c1 = (float) Math.cos(th1);
                    float s1 = (float) Math.sin(th1);
                    float c2 = (float) Math.cos(th2);
                    float s2 = (float) Math.sin(th2);

                    float lobeAngle1 = 5.0f * (float) (th1 - baseRot);
                    float lobeAngle2 = 5.0f * (float) (th2 - baseRot);
                    float rOuter1 = domeRadius * (1.15f + 0.42f * (float) Math.cos(lobeAngle1) - 0.08f * Math.abs((float) Math.sin(lobeAngle1)));
                    float rOuter2 = domeRadius * (1.15f + 0.42f * (float) Math.cos(lobeAngle2) - 0.08f * Math.abs((float) Math.sin(lobeAngle2)));

                    float r1 = domeRadius * 0.25f;
                    float r2 = domeRadius * 0.65f;
                    float r3 = domeRadius * 1.05f;

                    float p1_1x = c1 * r1, p1_1z = s1 * r1;
                    float p1_2x = c2 * r1, p1_2z = s2 * r1;
                    float p2_1x = c1 * r2, p2_1z = s1 * r2;
                    float p2_2x = c2 * r2, p2_2z = s2 * r2;
                    float p3_1x = c1 * r3, p3_1z = s1 * r3;
                    float p3_2x = c2 * r3, p3_2z = s2 * r3;
                    float p4_1x = c1 * rOuter1, p4_1z = s1 * rOuter1;
                    float p4_2x = c2 * rOuter2, p4_2z = s2 * rOuter2;

                    // Ring 0: Center Golden Pistil Core
                    emitDoubleSidedQuad(consumer, positionMatrix,
                            0.0f, 0.11f, 0.0f, 254, 240, 138, lobeAlpha,
                            p1_1x, 0.11f, p1_1z, 255, 241, 242, lobeAlpha,
                            p1_2x, 0.11f, p1_2z, 255, 241, 242, lobeAlpha,
                            0.0f, 0.11f, 0.0f, 254, 240, 138, lobeAlpha);

                    // Ring 1: Inner Ivory Petal Base
                    emitDoubleSidedQuad(consumer, positionMatrix,
                            p1_1x, 0.11f, p1_1z, 255, 241, 242, lobeAlpha,
                            p2_1x, 0.11f, p2_1z, 244, 114, 182, lobeAlpha,
                            p2_2x, 0.11f, p2_2z, 244, 114, 182, lobeAlpha,
                            p1_2x, 0.11f, p1_2z, 255, 241, 242, lobeAlpha);

                    // Ring 2: Mid Rose Quartz Petal Body
                    emitDoubleSidedQuad(consumer, positionMatrix,
                            p2_1x, 0.11f, p2_1z, 244, 114, 182, lobeAlpha,
                            p3_1x, 0.11f, p3_1z, 251, 113, 133, lobeAlpha,
                            p3_2x, 0.11f, p3_2z, 251, 113, 133, lobeAlpha,
                            p2_2x, 0.11f, p2_2z, 244, 114, 182, lobeAlpha);

                    // Ring 3: Outer Blossom Pink Tip with Sakura Clefts
                    int tipAlpha = Mth.clamp((int) (maxAlpha * 0.90f), 0, maxAlpha);
                    emitDoubleSidedQuad(consumer, positionMatrix,
                            p3_1x, 0.11f, p3_1z, 251, 113, 133, lobeAlpha,
                            p4_1x, 0.11f, p4_1z, 254, 205, 211, tipAlpha,
                            p4_2x, 0.11f, p4_2z, 254, 205, 211, tipAlpha,
                            p3_2x, 0.11f, p3_2z, 251, 113, 133, lobeAlpha);
                }

                // 2. Swirling 3D 5-petal blossoms & individual petals orbiting on dome surface
                int blossomCount = (int) (24 * density);
                for (int p = 0; p < blossomCount; p++) {
                    float pAngle = (float) (animTime * 1.2 + p * 2.0 * Math.PI / 24);
                    float pProgress = (float) ((animTime * 0.30 + p * 0.15) % 1.0);
                    float theta = (float) (0.06 * Math.PI + pProgress * 0.42 * Math.PI);
                    float rDist = (domeRadius * 1.015f) * (float) Math.sin(theta);
                    float yDist = (domeRadius * 1.015f) * (float) Math.cos(theta);
                    float cx = (float) (Math.cos(pAngle) * rDist);
                    float cy = yDist;
                    float cz = (float) (Math.sin(pAngle) * rDist);
                    float pPulse = 0.7f + 0.3f * (float) Math.sin(animTime * 3.0 + p);
                    int petalAlpha = Mth.clamp((int) ((1.0f - pProgress * 0.4f) * maxAlpha), 0, maxAlpha);

                    if (p % 2 == 0) {
                        emitSakuraFlower(consumer, positionMatrix, cx, cy, cz, camRight, camUp, 0.055f * pPulse, (float) (animTime * 2.0 + p), 244, 114, 182, petalAlpha);
                    } else {
                        emitPetalGlint(consumer, positionMatrix, cx, cy, cz, camRight, camUp, 0.045f * pPulse, 251, 113, 133, petalAlpha);
                    }
                }
            }
        }

        // Prismatic Crystal Sparkling Geode Facet Diamonds
        if (theme == VisualTheme.CRYSTAL) {
            int geodeCount = (int) (48 * density);
            for (int c = 0; c < geodeCount; c++) {
                int qIdx = (c * 29 + 11) % totalQuads;
                PredictionRenderData.DomeQuad q = state.domeQuads().get(qIdx);
                float cx = (float) ((q.p1().x + q.p2().x + q.p3().x + q.p4().x) * 0.2525);
                float cy = (float) ((q.p1().y + q.p2().y + q.p3().y + q.p4().y) * 0.2525);
                float cz = (float) ((q.p1().z + q.p2().z + q.p3().z + q.p4().z) * 0.2525);
                float sparkle = (float) Math.sin(animTime * 5.0 + c * 2.3);
                if (sparkle > 0.1f) {
                    float dSize = 0.035f + 0.02f * sparkle;
                    int dAlpha = Mth.clamp((int) (sparkle * maxAlpha * 1.25f), 0, maxAlpha);
                    int cCol = sparkle > 0.6f ? 0xFFFFFF : 0xE9D5FF;
                    emitBillboardGlint(consumer, positionMatrix, cx, cy, cz, camRight, camUp, dSize,
                            VisualTheme.extractR(cCol), VisualTheme.extractG(cCol), VisualTheme.extractB(cCol), dAlpha);
                }
            }
        }

        // 8-Bit Arcade Retro Sprites Multi-Tier Constellation
        if (theme == VisualTheme.ARCADE) {
            float domeRadius = (float) state.domeQuads().get(0).p1().length();
            int ring1 = (int) (14 * density);
            int ring2 = (int) (12 * density);
            int ring3 = (int) (10 * density);
            if (ring1 >= 4) renderArcadeDomeRing(consumer, positionMatrix, domeRadius, camRight, camUp, maxAlpha, animTime, ring1, 0.40f, 1.2f, 3.0f, 0, 0.055f);
            if (ring2 >= 4) renderArcadeDomeRing(consumer, positionMatrix, domeRadius, camRight, camUp, maxAlpha, animTime, ring2, 0.25f, -0.9f, 2.5f, 3, 0.060f);
            if (ring3 >= 4) renderArcadeDomeRing(consumer, positionMatrix, domeRadius, camRight, camUp, maxAlpha, animTime, ring3, 0.12f, 1.5f, 4.0f, 5, 0.050f);
        }
    }

    private static void renderArcadeDomeRing(
            VertexConsumer consumer, Matrix4f pose, float domeRadius,
            Vec3 camRight, Vec3 camUp, int maxAlpha, double animTime,
            int count, float thetaFrac, float rotSpeed, float spriteSpeed, int spriteOffset, float size
    ) {
        float r = domeRadius * 1.02f;
        float theta = thetaFrac * (float) Math.PI;
        float rDist = r * (float) Math.sin(theta);
        float cy = r * (float) Math.cos(theta);

        for (int a = 0; a < count; a++) {
            float aAngle = (float) (a * 2.0 * Math.PI / count + animTime * rotSpeed);
            float cx = (float) (Math.cos(aAngle) * rDist);
            float cz = (float) (Math.sin(aAngle) * rDist);
            int spriteIdx = (a + spriteOffset + (int) (animTime * spriteSpeed)) & 7;
            int[] col = ThemeVisualAssets.getArcadeSpriteColor(spriteIdx);
            emitArcadeSprite(consumer, pose, cx, cy, cz, camRight, camUp, size, spriteIdx, col[0], col[1], col[2], maxAlpha);
        }
    }

    // =========================================================================
    // 3D Procedural Theme Geometry Emitters
    // =========================================================================

    public static void emitMatrixGlyph(VertexConsumer consumer, Matrix4f pose, Vec3 center, Vec3 camRight, Vec3 camUp, float cellSize, int glyphIndex, int r, int g, int b, int alpha) {
        emitMatrixGlyph(consumer, pose, (float) center.x, (float) center.y, (float) center.z, camRight, camUp, cellSize, glyphIndex, r, g, b, alpha);
    }

    public static void emitMatrixGlyph(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, Vec3 camRight, Vec3 camUp, float cellSize, int glyphIndex, int r, int g, int b, int alpha) {
        int glyph = ThemeVisualAssets.MATRIX_GLYPHS[glyphIndex & 15] & 0xFFFF;
        emitBitmapBillboard(consumer, pose, cx, cy, cz, camRight, camUp, cellSize, glyph, 3, 5, r, g, b, alpha);
    }

    public static void emitBillboardGlint(VertexConsumer consumer, Matrix4f pose, Vec3 center, Vec3 camRight, Vec3 camUp, float size, int r, int g, int b, int alpha) {
        emitBillboardGlint(consumer, pose, (float) center.x, (float) center.y, (float) center.z, camRight, camUp, size, r, g, b, alpha);
    }

    public static void emitBillboardGlint(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, Vec3 camRight, Vec3 camUp, float size, int r, int g, int b, int alpha) {
        float rx = (float) (camRight.x * size), ry = (float) (camRight.y * size), rz = (float) (camRight.z * size);
        float ux = (float) (camUp.x * size), uy = (float) (camUp.y * size), uz = (float) (camUp.z * size);

        // Diamond quad
        consumer.addVertex(pose, cx - rx, cy - ry, cz - rz).setColor(r, g, b, 0);
        consumer.addVertex(pose, cx + ux, cy + uy, cz + uz).setColor(r, g, b, alpha);
        consumer.addVertex(pose, cx + rx, cy + ry, cz + rz).setColor(r, g, b, 0);
        consumer.addVertex(pose, cx - ux, cy - uy, cz - uz).setColor(r, g, b, alpha);
    }

    public static void emitHexGlint(VertexConsumer consumer, Matrix4f pose, Vec3 center, Vec3 camRight, Vec3 camUp, float size, int r, int g, int b, int alpha) {
        emitHexGlint(consumer, pose, (float) center.x, (float) center.y, (float) center.z, camRight, camUp, size, r, g, b, alpha);
    }

    public static void emitHexGlint(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, Vec3 camRight, Vec3 camUp, float size, int r, int g, int b, int alpha) {
        emitBillboardGlint(consumer, pose, cx, cy, cz, camRight, camUp, size, r, g, b, alpha);
        float dSize = size * 0.75f;
        float diag1x = (float) ((camRight.x + camUp.x) * 0.7071 * dSize);
        float diag1y = (float) ((camRight.y + camUp.y) * 0.7071 * dSize);
        float diag1z = (float) ((camRight.z + camUp.z) * 0.7071 * dSize);
        float diag2x = (float) ((camRight.x - camUp.x) * 0.7071 * dSize);
        float diag2y = (float) ((camRight.y - camUp.y) * 0.7071 * dSize);
        float diag2z = (float) ((camRight.z - camUp.z) * 0.7071 * dSize);

        int dAlpha = (int) (alpha * 0.85f);

        consumer.addVertex(pose, cx - diag1x, cy - diag1y, cz - diag1z).setColor(r, g, b, 0);
        consumer.addVertex(pose, cx + diag2x, cy + diag2y, cz + diag2z).setColor(r, g, b, dAlpha);
        consumer.addVertex(pose, cx + diag1x, cy + diag1y, cz + diag1z).setColor(r, g, b, 0);
        consumer.addVertex(pose, cx - diag2x, cy - diag2y, cz - diag2z).setColor(r, g, b, dAlpha);
    }

    public static void emitPetalGlint(VertexConsumer consumer, Matrix4f pose, Vec3 center, Vec3 camRight, Vec3 camUp, float size, int r, int g, int b, int alpha) {
        emitPetalGlint(consumer, pose, (float) center.x, (float) center.y, (float) center.z, camRight, camUp, size, r, g, b, alpha);
    }

    public static void emitPetalGlint(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, Vec3 camRight, Vec3 camUp, float size, int r, int g, int b, int alpha) {
        float rx = (float) (camRight.x * size * 0.5f), ry = (float) (camRight.y * size * 0.5f), rz = (float) (camRight.z * size * 0.5f);
        float ux = (float) (camUp.x * size * 0.85f), uy = (float) (camUp.y * size * 0.85f), uz = (float) (camUp.z * size * 0.85f);

        consumer.addVertex(pose, cx - rx, cy - ry, cz - rz).setColor(r, g, b, 0);
        consumer.addVertex(pose, cx + ux, cy + uy, cz + uz).setColor(255, 255, 255, alpha);
        consumer.addVertex(pose, cx + rx, cy + ry, cz + rz).setColor(r, g, b, 0);
        consumer.addVertex(pose, cx - ux, cy - uy, cz - uz).setColor(r, g, b, alpha / 2);
    }

    public static void emitSakuraFlower(VertexConsumer consumer, Matrix4f pose, Vec3 center, Vec3 camRight, Vec3 camUp, float size, float rotAngle, int r, int g, int b, int alpha) {
        emitSakuraFlower(consumer, pose, (float) center.x, (float) center.y, (float) center.z, camRight, camUp, size, rotAngle, r, g, b, alpha);
    }

    public static void emitSakuraFlower(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, Vec3 camRight, Vec3 camUp, float size, float rotAngle, int r, int g, int b, int alpha) {
        for (int p = 0; p < 5; p++) {
            double angle = rotAngle + p * (2.0 * Math.PI / 5.0);
            float cosA = (float) Math.cos(angle);
            float sinA = (float) Math.sin(angle);
            float dirX = (float) (camRight.x * cosA + camUp.x * sinA);
            float dirY = (float) (camRight.y * cosA + camUp.y * sinA);
            float dirZ = (float) (camRight.z * cosA + camUp.z * sinA);
            float perpX = (float) (-camRight.x * sinA + camUp.x * cosA);
            float perpY = (float) (-camRight.y * sinA + camUp.y * cosA);
            float perpZ = (float) (-camRight.z * sinA + camUp.z * cosA);

            float tipX = cx + dirX * size * 1.25f, tipY = cy + dirY * size * 1.25f, tipZ = cz + dirZ * size * 1.25f;
            float leftX = cx + dirX * size * 0.65f + perpX * size * 0.45f;
            float leftY = cy + dirY * size * 0.65f + perpY * size * 0.45f;
            float leftZ = cz + dirZ * size * 0.65f + perpZ * size * 0.45f;
            float rightX = cx + dirX * size * 0.65f - perpX * size * 0.45f;
            float rightY = cy + dirY * size * 0.65f - perpY * size * 0.45f;
            float rightZ = cz + dirZ * size * 0.65f - perpZ * size * 0.45f;

            consumer.addVertex(pose, cx, cy, cz).setColor(255, 240, 245, alpha);
            consumer.addVertex(pose, leftX, leftY, leftZ).setColor(r, g, b, alpha);
            consumer.addVertex(pose, tipX, tipY, tipZ).setColor(255, 245, 250, alpha);
            consumer.addVertex(pose, rightX, rightY, rightZ).setColor(r, g, b, alpha);
        }
        // Center pistil (soft yellow-gold glint)
        emitBillboardGlint(consumer, pose, cx, cy, cz, camRight, camUp, size * 0.28f, 254, 240, 138, alpha);
    }

    public static void emitArcadeSprite(VertexConsumer consumer, Matrix4f pose, Vec3 center, Vec3 camRight, Vec3 camUp, float cellSize, int spriteIndex, int r, int g, int b, int alpha) {
        emitArcadeSprite(consumer, pose, (float) center.x, (float) center.y, (float) center.z, camRight, camUp, cellSize, spriteIndex, r, g, b, alpha);
    }

    public static void emitArcadeSprite(VertexConsumer consumer, Matrix4f pose, float cx, float cy, float cz, Vec3 camRight, Vec3 camUp, float cellSize, int spriteIndex, int r, int g, int b, int alpha) {
        int sprite = ThemeVisualAssets.ARCADE_SPRITES[spriteIndex & 7];
        emitBitmapBillboard(consumer, pose, cx, cy, cz, camRight, camUp, cellSize, sprite, 5, 5, r, g, b, alpha);
    }

    public static void emitBitmapBillboard(
            VertexConsumer consumer, Matrix4f pose,
            float cx, float cy, float cz, Vec3 camRight, Vec3 camUp,
            float cellSize, int bitmask, int cols, int rows,
            int r, int g, int b, int alpha
    ) {
        float hs = cellSize * 0.45f;
        float rx = (float) camRight.x, ry = (float) camRight.y, rz = (float) camRight.z;
        float ux = (float) camUp.x, uy = (float) camUp.y, uz = (float) camUp.z;
        float halfCol = (cols - 1) * 0.5f;
        float halfRow = (rows - 1) * 0.5f;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int bitIndex = (rows - 1 - row) * cols + (cols - 1 - col);
                if (((bitmask >> bitIndex) & 1) == 1) {
                    float ox = (col - halfCol) * cellSize;
                    float oy = (halfRow - row) * cellSize;

                    float ccX = cx + rx * ox + ux * oy;
                    float ccY = cy + ry * ox + uy * oy;
                    float ccZ = cz + rz * ox + uz * oy;

                    float p1x = ccX - rx * hs - ux * hs, p1y = ccY - ry * hs - uy * hs, p1z = ccZ - rz * hs - uz * hs;
                    float p2x = ccX + rx * hs - ux * hs, p2y = ccY + ry * hs - uy * hs, p2z = ccZ + rz * hs - uz * hs;
                    float p3x = ccX + rx * hs + ux * hs, p3y = ccY + ry * hs + uy * hs, p3z = ccZ + rz * hs + uz * hs;
                    float p4x = ccX - rx * hs + ux * hs, p4y = ccY - ry * hs + uy * hs, p4z = ccZ - rz * hs + uz * hs;

                    consumer.addVertex(pose, p1x, p1y, p1z).setColor(r, g, b, alpha);
                    consumer.addVertex(pose, p2x, p2y, p2z).setColor(r, g, b, alpha);
                    consumer.addVertex(pose, p3x, p3y, p3z).setColor(r, g, b, alpha);
                    consumer.addVertex(pose, p4x, p4y, p4z).setColor(r, g, b, alpha);
                }
            }
        }
    }

    public static void emitTacticalAirplane(
            VertexConsumer consumer, Matrix4f pose,
            Vec3 center, Vec3 forward, Vec3 right, Vec3 up,
            float scale, int r, int g, int b, int alpha
    ) {
        float cx = (float) center.x, cy = (float) center.y, cz = (float) center.z;
        float fx = (float) forward.x, fy = (float) forward.y, fz = (float) forward.z;
        float rx = (float) right.x, ry = (float) right.y, rz = (float) right.z;
        float ux = (float) up.x, uy = (float) up.y, uz = (float) up.z;

        float noseX = cx + fx * scale * 1.6f, noseY = cy + fy * scale * 1.6f, noseZ = cz + fz * scale * 1.6f;
        float tailX = cx - fx * scale * 0.8f, tailY = cy - fy * scale * 0.8f, tailZ = cz - fz * scale * 0.8f;
        float lWingX = cx - fx * scale * 0.3f - rx * scale * 1.3f;
        float lWingY = cy - fy * scale * 0.3f - ry * scale * 1.3f;
        float lWingZ = cz - fz * scale * 0.3f - rz * scale * 1.3f;
        float rWingX = cx - fx * scale * 0.3f + rx * scale * 1.3f;
        float rWingY = cy - fy * scale * 0.3f + ry * scale * 1.3f;
        float rWingZ = cz - fz * scale * 0.3f + rz * scale * 1.3f;
        float finX = cx - fx * scale * 0.7f + ux * scale * 0.75f;
        float finY = cy - fy * scale * 0.7f + uy * scale * 0.75f;
        float finZ = cz - fz * scale * 0.7f + uz * scale * 0.75f;

        // Left wing quad
        consumer.addVertex(pose, noseX, noseY, noseZ).setColor(r, g, b, alpha);
        consumer.addVertex(pose, lWingX, lWingY, lWingZ).setColor(r, g, b, alpha);
        consumer.addVertex(pose, tailX, tailY, tailZ).setColor(r, g, b, alpha);
        consumer.addVertex(pose, noseX, noseY, noseZ).setColor(r, g, b, alpha);

        // Right wing quad
        consumer.addVertex(pose, noseX, noseY, noseZ).setColor(r, g, b, alpha);
        consumer.addVertex(pose, rWingX, rWingY, rWingZ).setColor(r, g, b, alpha);
        consumer.addVertex(pose, tailX, tailY, tailZ).setColor(r, g, b, alpha);
        consumer.addVertex(pose, noseX, noseY, noseZ).setColor(r, g, b, alpha);

        // Vertical stabilizer / tail fin
        consumer.addVertex(pose, finX, finY, finZ).setColor(255, 255, 255, alpha);
        consumer.addVertex(pose, tailX, tailY, tailZ).setColor(r, g, b, alpha);
        consumer.addVertex(pose, cx, cy, cz).setColor(r, g, b, alpha);
        consumer.addVertex(pose, finX, finY, finZ).setColor(255, 255, 255, alpha);
    }
}
