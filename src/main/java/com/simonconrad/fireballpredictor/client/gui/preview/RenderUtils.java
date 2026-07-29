package com.simonconrad.fireballpredictor.client.gui.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shared rendering utilities: colour packing, AA primitives, item-icon drawing,
 * and the monotonic animation clock.
 */
final class RenderUtils {

    private RenderUtils() {
    }

    // ---- Tuning constants ---------------------------------------------------

    static final int DASH_PERIOD_PX = 10;
    static final int DASH_ON_PX = 6;

    private static final long START_NANOS = System.nanoTime();

    // ---- Item-render state --------------------------------------------------

    /** Cached capability probes so a missing sprite/API doesn't throw per frame. */
    static boolean effectSpriteAvailable = true;

    /**
     * Only disabled for actual API/linkage problems. Runtime failures can be
     * title-screen / no-world transient failures.
     */
    static boolean itemRenderApiAvailable = true;

    static long nextItemRenderAttemptNanos = 0L;
    static final long ITEM_RENDER_RETRY_NANOS = 500_000_000L;

    // ---- Vanilla texture identifiers ----------------------------------------

    static final Identifier FIRE_CHARGE_ICON =
            Identifier.withDefaultNamespace("textures/item/fire_charge.png");
    static final Identifier WIND_CHARGE_ICON =
            Identifier.withDefaultNamespace("textures/item/wind_charge.png");
    static final Identifier WITHER_SKELETON_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");

    // ---- Clock --------------------------------------------------------------

    /** Monotonic seconds since class load — no hourly wrap discontinuity. */
    static float seconds() {
        return (System.nanoTime() - START_NANOS) / 1_000_000_000.0f;
    }

    // ---- Colour helpers -----------------------------------------------------

    /** Lightens a single channel by blending toward 255. */
    static int lighten(int channel, float amount) {
        return Math.min(255, channel + Math.round((255 - channel) * amount));
    }

    /** Hermite smoothstep. */
    static float smoothstep(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /** Pack RGBA into an ARGB int (Minecraft's convention). */
    static int pack(int r, int g, int b, int a) {
        return ((Mth.clamp(a, 0, 255)) << 24)
                | ((Mth.clamp(r, 0, 255)) << 16)
                | ((Mth.clamp(g, 0, 255)) << 8)
                | (Mth.clamp(b, 0, 255));
    }

    // ---- UI helpers ---------------------------------------------------------

    /** Centred "disabled" label drawn with the Minecraft font. */
    static void drawDisabledLabel(Painter p, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }
        String label = "disabled";
        int tw = mc.font.width(label);
        p.graphics().text(mc.font, label,
                x + (w - tw) / 2,
                y + h / 2 - mc.font.lineHeight / 2,
                0x88A0A8B0);
    }

    // ---- Item icon drawing --------------------------------------------------

    /**
     * Renders a vanilla item at an arbitrary size, with layered fallbacks:
     * <ol>
     *   <li>Proper GUI item render (only when a ClientLevel exists).</li>
     *   <li>Known vanilla texture blit (fire charge, wind charge, wither skull).</li>
     *   <li>Flat coloured swatch as emergency fallback.</li>
     * </ol>
     */
    static void drawItemIcon(Painter p, Item item, int x, int y, int size, int fallbackArgb) {
        if (tryDrawRenderedItemIcon(p, item, x, y, size)) {
            return;
        }
        if (drawKnownVanillaIconTexture(p, item, x, y, size)) {
            return;
        }
        p.fill(x + 1, y + 1, x + size - 1, y + size - 1, fallbackArgb);
    }

    private static boolean tryDrawRenderedItemIcon(Painter p, Item item, int x, int y, int size) {
        if (!itemRenderApiAvailable) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();

        // Do not call item()/fakeItem() from the title screen. In 26.x this can
        // fail before a ClientLevel exists, and that failure must not permanently
        // disable item rendering.
        if (mc == null || mc.level == null) {
            return false;
        }

        long now = System.nanoTime();
        if (now < nextItemRenderAttemptNanos) {
            return false;
        }

        try {
            GuiGraphicsExtractor g = p.graphics();
            var pose = g.pose();

            pose.pushMatrix();
            try {
                pose.translate(x, y);
                pose.scale(size / 16.0f, size / 16.0f);
                g.item(new ItemStack(item), 0, 0);
            } finally {
                pose.popMatrix();
            }

            nextItemRenderAttemptNanos = 0L;
            return true;
        } catch (LinkageError err) {
            // Actual API mismatch / unavailable method: safe to disable permanently.
            itemRenderApiAvailable = false;
            return false;
        } catch (RuntimeException ex) {
            // Runtime failures are often transient (title screen / world transitions).
            nextItemRenderAttemptNanos = System.nanoTime() + ITEM_RENDER_RETRY_NANOS;
            return false;
        }
    }

    /**
     * Draws the few icons used by this preview directly from vanilla textures.
     * Only used when the real item renderer is unavailable or fails.
     */
    private static boolean drawKnownVanillaIconTexture(Painter p, Item item, int x, int y, int size) {
        try {
            GuiGraphicsExtractor g = p.graphics();

            if (item == Items.FIRE_CHARGE) {
                g.blit(RenderPipelines.GUI_TEXTURED,
                        FIRE_CHARGE_ICON, x, y,
                        0, 0, size, size, 14, 14);
                return true;
            }

            if (item == Items.WIND_CHARGE) {
                g.blit(RenderPipelines.GUI_TEXTURED,
                        WIND_CHARGE_ICON, x, y,
                        0, 0, size, size, 28, 28);
                return true;
            }

            if (item == Items.WITHER_SKELETON_SKULL) {
                // Draw the front face from the wither skeleton entity texture.
                g.blit(RenderPipelines.GUI_TEXTURED,
                        WITHER_SKELETON_TEXTURE, x, y,
                        8, 8, size, size, 8, 8, 64, 32);
                return true;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Fall through to flat swatch fallback.
        }
        return false;
    }

    // ---- Anti-aliased primitives shared across renderers --------------------

    /**
     * One anti-aliased vertical slice of a ribbon.
     * Used by both the trajectory and tracking renderers.
     */
    static void ribbonColumn(Painter p, int px, float cy, float half,
                             int r, int g, int b, float alpha) {
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.004f || half <= 0.0f) {
            return;
        }
        if (half < 0.5f) {
            p.pixel(px, Mth.floor(cy),
                    pack(r, g, b, Math.round(255 * alpha * (half / 0.5f))));
            return;
        }
        int top = Mth.floor(cy - half);
        int bottom = Mth.floor(cy + half);
        for (int py = top; py <= bottom; py++) {
            float dist = Math.abs(py + 0.5f - cy);
            float coverage = Mth.clamp(half - dist, 0.0f, 1.0f);
            if (coverage <= 0.0f) {
                continue;
            }
            float glow = 0.75f + 0.25f * (1.0f - (dist / half) * (dist / half));
            int a = Math.round(255 * alpha * coverage * glow);
            if (a > 0) {
                p.pixel(px, py, pack(r, g, b, a));
            }
        }
    }

    /**
     * Small radially-faded blob used for the projectile head and impact spark.
     */
    static void softDisc(Painter p, float cx, float cy, float radius,
                         int r, int g, int b, float alpha) {
        alpha = Mth.clamp(alpha, 0.0f, 1.0f);
        if (alpha <= 0.004f || radius <= 0.0f) {
            return;
        }
        int y0 = Mth.floor(cy - radius);
        int y1 = Mth.ceil(cy + radius);
        int x0 = Mth.floor(cx - radius);
        int x1 = Mth.ceil(cx + radius);
        for (int py = y0; py <= y1; py++) {
            float dy = py + 0.5f - cy;
            for (int px = x0; px <= x1; px++) {
                float dx = px + 0.5f - cx;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float coverage = Mth.clamp(radius - dist, 0.0f, 1.0f);
                if (coverage <= 0.0f) {
                    continue;
                }
                float falloff = 1.0f - 0.55f * (dist / radius) * (dist / radius);
                int a = Math.round(255 * alpha * coverage * falloff);
                if (a > 0) {
                    p.pixel(px, py, pack(r, g, b, a));
                }
            }
        }
    }
}
