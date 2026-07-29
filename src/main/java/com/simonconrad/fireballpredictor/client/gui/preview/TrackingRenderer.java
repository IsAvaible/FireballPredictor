package com.simonconrad.fireballpredictor.client.gui.preview;

import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import java.awt.Color;

import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.*;

/**
 * Renders the tracking-toggle schematic: a compact badge showing whether a
 * projectile type is tracked, with a lock-on arc and reticle.
 */
final class TrackingRenderer {

    private TrackingRenderer() {
    }

    // ---- Public entry point -------------------------------------------------

    static void render(Painter p, int x, int y, int w, int h, boolean wind,
                       boolean tracked, Color color) {

        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        if (!tracked) {
            // Desaturate toward the panel grey so "off" reads at a glance.
            int lum = (r * 30 + g * 59 + b * 11) / 100;
            r = (r + lum * 3) / 4;
            g = (g + lum * 3) / 4;
            b = (b + lum * 3) / 4;
        }

        float time = seconds();

        int icon = Mth.clamp(Math.min(h - 8, w / 4), 10, 24);
        int ix = x + Math.max(3, w / 20);
        int iy = y + (h - icon) / 2;

        drawItemIcon(p, wind ? Items.WIND_CHARGE : Items.WITHER_SKELETON_SKULL,
                ix, iy, icon, tracked ? 0xFF9AA3AE : 0xFF4A5058);

        // Lock-on path: icon -> reticle on the right
        float sx = ix + icon + 3f;
        float ex = x + w - Math.max(10f, w * 0.13f);
        float cy = y + h * 0.5f;
        if (ex - sx >= 10f) {
            Arc arc = new Arc(sx, ex, cy - h * 0.22f, cy, 0.42f);
            int dashScroll = tracked ? (int) (time * 14.0f) : 0;
            float alphaScale = tracked ? 1.0f : 0.32f;

            for (int px = Mth.ceil(sx); px <= Mth.floor(ex); px++) {
                if (Math.floorMod(px - dashScroll, DASH_PERIOD_PX) >= DASH_ON_PX) {
                    continue;
                }
                float t = arc.tAtX(px + 0.5f);
                if (t < 0.0f || t > 1.0f) {
                    continue;
                }
                ribbonColumn(p, px, arc.yAt(t), 1.2f, r, g, b, 0.78f * alphaScale);
            }

            // Travelling head (frozen at launch when untracked)
            float t = tracked ? (time * 0.55f) % 1.0f : 0.0f;
            float hx = arc.xAt(t);
            float hy = arc.yAt(t);
            softDisc(p, hx, hy, 3.0f, r, g, b, 0.28f * alphaScale);
            softDisc(p, hx, hy, 1.6f,
                    lighten(r, 0.35f), lighten(g, 0.35f), lighten(b, 0.35f),
                    0.95f * alphaScale);

            // Reticle at the predicted impact
            float pulse = tracked
                    ? 0.85f + 0.15f * (float) Math.sin(time * 3.4f)
                    : 1.0f;
            reticle(p, ex, cy, Math.max(3.5f, h * 0.16f) * pulse,
                    pack(r, g, b, tracked ? 230 : 70), tracked);
        }

        if (!tracked) {
            // Red "no" slash across the icon plate
            int slash = 0xCCE04A3C;
            p.line(ix - 2, iy + icon + 2, ix + icon + 2, iy - 2, slash);
            p.line(ix - 1, iy + icon + 2, ix + icon + 2, iy - 1, slash);
        }
    }

    // ---- Private helpers ----------------------------------------------------

    /**
     * Corner-bracket reticle; filled centre dot only while locked on.
     */
    private static void reticle(Painter p, float cx, float cy, float radius,
                                int argb, boolean locked) {
        if ((argb >>> 24) == 0) {
            return;
        }

        int icx = Math.round(cx);
        int icy = Math.round(cy);
        int rr = Math.max(3, Math.round(radius));
        int arm = Math.max(2, rr / 2);

        // Define outer bounds centred symmetrically on icx and icy.
        // Total diameter is (2 * rr + 1) pixels, with (icx, icy) as the exact midpoint.
        int x0 = icx - rr;
        int x1 = icx + rr + 1; // Exclusive right bound
        int y0 = icy - rr;
        int y1 = icy + rr + 1; // Exclusive bottom bound

        // Top-Left Bracket
        p.fill(x0, y0, x0 + arm, y0 + 1, argb);
        p.fill(x0, y0, x0 + 1, y0 + arm, argb);

        // Top-Right Bracket
        p.fill(x1 - arm, y0, x1, y0 + 1, argb);
        p.fill(x1 - 1, y0, x1, y0 + arm, argb);

        // Bottom-Left Bracket
        p.fill(x0, y1 - 1, x0 + arm, y1, argb);
        p.fill(x0, y1 - arm, x0 + 1, y1, argb);

        // Bottom-Right Bracket
        p.fill(x1 - arm, y1 - 1, x1, y1, argb);
        p.fill(x1 - 1, y1 - arm, x1, y1, argb);

        // Center dot (1x1 pixel precisely at the midpoint)
        if (locked) {
            p.fill(icx, icy, icx + 1, icy + 1, argb);
        }
    }
}
