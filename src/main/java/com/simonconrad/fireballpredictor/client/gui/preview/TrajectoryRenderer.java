package com.simonconrad.fireballpredictor.client.gui.preview;

import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import net.minecraft.util.Mth;

import java.awt.Color;

import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.*;

/**
 * Renders the trajectory schematic: a parabolic ribbon arc with a travelling
 * projectile head and an impact-flash ground ring.
 */
final class TrajectoryRenderer {

    // ---- Timing -------------------------------------------------------------

    /** Full launch -> impact -> flash loop, in seconds. */
    private static final float CYCLE = 2.6f;
    /** Fraction of the cycle spent travelling; the remainder is the impact flash. */
    private static final float TRAVEL = 0.78f;
    /** Normalised position of the arc apex. */
    private static final float APEX_T = 0.30f;

    private TrajectoryRenderer() {
    }

    // ---- Public entry point -------------------------------------------------

    static void render(Painter p, int x, int y, int w, int h, boolean wind,
                       boolean show, Color color, float widthFactor,
                       TrajectoryStyle style, boolean coreGlow, boolean pulsing,
                       VisualTheme theme, float animSpeed) {

        // Ground line + a whisper of floor shading beneath it
        int groundY = y + h - Math.max(4, h / 10);
        p.fill(x + 2, groundY, x + w - 2, groundY + 1, 0x33FFFFFF);
        p.fill(x + 2, groundY + 1, x + w - 2, y + h, 0x10FFFFFF);

        // Leave room on the right so the impact ring isn't clipped in half.
        float left = x + 4f;
        float right = x + w - 10f;
        if (right - left < 8f) {
            return;
        }

        // Parabola: apex inside the panel, terminating exactly on the ground line.
        Arc arc = new Arc(left, right, y + h * 0.13f, groundY - 1f, APEX_T);

        boolean dashed = style == TrajectoryStyle.DASHED;
        boolean coreOnly = style == TrajectoryStyle.CORE_ONLY;
        boolean drawShroud = !coreOnly;
        boolean drawCore = coreGlow || coreOnly;

        float halfWidth = Mth.clamp(1.1f + widthFactor * 7.0f, 1.1f, 9.0f);

        int fallbackRgb = VisualTheme.packRgb(color.getRed(), color.getGreen(), color.getBlue());
        VisualTheme activeTheme = theme == null ? VisualTheme.DEFAULT : theme;
        // DEFAULT must stay pixel-identical to the pre-theme preview renderer.
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        float time = seconds() * Math.max(0.0f, animSpeed);
        int dashScroll = (int) (time * 16.0f);

        // Column rasterisation: the arc is single-valued in x, so one soft slice per pixel column.
        if (show) {
            int pxStart = Mth.ceil(left);
            int pxEnd = Mth.floor(right);
            for (int px = pxStart; px <= pxEnd; px++) {
                if (dashed && Math.floorMod(px + dashScroll, DASH_PERIOD_PX) >= DASH_ON_PX) {
                    continue;
                }

                float t = arc.tAtX(px + 0.5f);
                if (t < 0.0f || t > 1.0f) {
                    continue;
                }

                float cy = arc.yAt(t);
                float slope = arc.slopeAt(t);
                // A stroke of constant perpendicular width covers more vertical pixels as it steepens.
                float stretch = Math.min(2.5f, (float) Math.sqrt(1.0f + slope * slope));
                float half = halfWidth * widthProfile(t) * stretch;

                float wave = pulsing
                        ? 0.78f + 0.22f * (float) Math.sin(time * 2.6f - t * 7.0f)
                        : 1.0f;
                float themeAlphaMod = activeTheme.getRibbonAlphaModulation(t, time, px);
                float alpha = (1.0f - 0.28f * t) * wave * themeAlphaMod;

                int sr, sg, sb, cr, cg, cb;
                if (activeTheme.isCustomTheme()) {
                    int segShroudRgb = activeTheme.getRibbonColorPacked(t, time, px, false, fallbackRgb);
                    int segCoreRgb = activeTheme.getRibbonColorPacked(t, time, px, true, fallbackRgb);
                    sr = VisualTheme.extractR(segShroudRgb);
                    sg = VisualTheme.extractG(segShroudRgb);
                    sb = VisualTheme.extractB(segShroudRgb);
                    cr = coreOnly ? sr : VisualTheme.extractR(segCoreRgb);
                    cg = coreOnly ? sg : VisualTheme.extractG(segCoreRgb);
                    cb = coreOnly ? sb : VisualTheme.extractB(segCoreRgb);
                } else {
                    // DEFAULT: original formulas (core brightened by 0.45 white mix).
                    sr = r; sg = g; sb = b;
                    cr = coreOnly ? r : lighten(r, 0.45f);
                    cg = coreOnly ? g : lighten(g, 0.45f);
                    cb = coreOnly ? b : lighten(b, 0.45f);
                }

                if (drawShroud) {
                    ribbonColumn(p, px, cy, half, sr, sg, sb, 0.60f * alpha);
                }
                if (drawCore) {
                    float coreHalf = Math.max(0.45f, half * (coreOnly ? 0.50f : 0.30f));
                    ribbonColumn(p, px, cy, coreHalf, cr, cg, cb,
                            (coreOnly ? 0.80f : 0.95f) * alpha);
                }
            }

            // Theme-specific decorative overlays along the trajectory path
            if (activeTheme.isCustomTheme()) {
                PreviewThemeDecorations.renderTrajectoryThemeDecorations(p, arc, activeTheme, time, left, right, groundY, halfWidth, fallbackRgb);
            }
        }

        float cycle = animSpeed <= 0.0f ? 0.45f : (time % CYCLE) / CYCLE;
        if (cycle < TRAVEL) {
            // Projectile head riding the arc
            float t = cycle / TRAVEL;
            float hx = arc.xAt(t);
            float hy = arc.yAt(t);
            float rad = Math.max(1.8f, halfWidth * 1.05f);

            if (activeTheme.isCustomTheme()) {
                int headRgb = activeTheme.getRibbonColorPacked(t, time, (int) hx, true, fallbackRgb);
                int hr = VisualTheme.extractR(headRgb);
                int hg = VisualTheme.extractG(headRgb);
                int hb = VisualTheme.extractB(headRgb);
                softDisc(p, hx, hy, rad * 2.0f, hr, hg, hb, 0.30f);
                softDisc(p, hx, hy, rad,
                        lighten(hr, 0.25f), lighten(hg, 0.25f), lighten(hb, 0.25f), 0.90f);
            } else {
                // DEFAULT: original head rendering.
                softDisc(p, hx, hy, rad * 2.0f, r, g, b, 0.30f);
                softDisc(p, hx, hy, rad,
                        lighten(r, 0.25f), lighten(g, 0.25f), lighten(b, 0.25f), 0.90f);
            }
            softDisc(p, hx, hy, Math.max(0.9f, rad * 0.45f), 255, 255, 255, 0.95f);
        } else {
            // Impact flash: expanding ground ring, no static marker left behind
            float f = (cycle - TRAVEL) / (1.0f - TRAVEL);
            float fade = (1.0f - f) * (1.0f - f);
            float ix = arc.xAt(1.0f);
            float rx = 1.5f + f * Math.max(7.0f, w * 0.075f);

            int impactRgb = activeTheme.getRibbonColorPacked(1.0f, time, (int) ix, false, fallbackRgb);
            int ir = VisualTheme.extractR(impactRgb);
            int ig = VisualTheme.extractG(impactRgb);
            int ib = VisualTheme.extractB(impactRgb);

            softDisc(p, ix, groundY - 1f,
                    Math.max(1.5f, halfWidth * (1.0f - f) * 1.6f),
                    lighten(ir, 0.3f), lighten(ig, 0.3f), lighten(ib, 0.3f),
                    0.85f * fade);
            ellipseRing(p, ix, groundY, rx, rx * 0.36f,
                    pack(impactRgb, Math.round(210 * fade)));
        }
    }

    // ---- Private helpers ----------------------------------------------------

    /**
     * Thickness envelope along the arc: thin at the muzzle, tapering to nothing
     * at the impact.
     */
    private static float widthProfile(float t) {
        float launch = Mth.clamp(t / 0.06f, 0.0f, 1.0f);
        float impact = Mth.clamp((1.0f - t) / 0.14f, 0.0f, 1.0f);
        float grow = 0.55f + 0.45f * t;
        return grow * (0.30f + 0.70f * launch) * smoothstep(impact);
    }

    /** Elliptical ring via line segments. */
    private static void ellipseRing(Painter p, float cx, float cy,
                                    float rx, float ry, int argb) {
        if ((argb >>> 24) == 0 || rx <= 0.5f) {
            return;
        }
        int segments = Math.max(20, Math.round(rx * 2.2f));
        float prevX = cx + rx;
        float prevY = cy;
        for (int i = 1; i <= segments; i++) {
            double a = (Math.PI * 2.0) * i / segments;
            float qx = cx + (float) Math.cos(a) * rx;
            float qy = cy + (float) Math.sin(a) * ry;
            p.line(prevX, prevY, qx, qy, argb);
            prevX = qx;
            prevY = qy;
        }
    }
}
