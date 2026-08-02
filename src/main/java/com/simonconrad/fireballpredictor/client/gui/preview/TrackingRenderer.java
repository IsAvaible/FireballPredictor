package com.simonconrad.fireballpredictor.client.gui.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.awt.Color;
import java.util.List;

import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.*;

/**
 * Renders the tracking-toggle schematics:
 * <ul>
 *   <li>Single-target lock-on badge (per-mob / per-source toggles)</li>
 *   <li>Master overview: miniature row of sources + status plate</li>
 *   <li>Mob-master overview: miniature row of hostile mobs + status plate</li>
 * </ul>
 */
final class TrackingRenderer {

    private TrackingRenderer() {
    }

    /** Which schematic flavour to draw. */
    enum Kind {
        /** Global master — all source categories. */
        MASTER,
        /** Hostile-mob master — blaze/ghast/dragon/wither. */
        MOB_MASTER,
        /** Single lock-on badge for one projectile source. */
        SINGLE
    }

    /** Concrete single-source identity for icons + colours. */
    enum Target {
        FIREBALL(Items.FIRE_CHARGE, new Color(255, 128, 0)),
        WITHER(Items.WITHER_SKELETON_SKULL, new Color(30, 30, 30)),
        WIND(Items.WIND_CHARGE, new Color(255, 255, 255)),
        BLAZE(Items.BLAZE_POWDER, new Color(255, 160, 32)),
        GHAST(Items.GHAST_TEAR, new Color(240, 240, 240)),
        DRAGON(Items.DRAGON_HEAD, new Color(160, 40, 180)),
        PLAYER(Items.PLAYER_HEAD, new Color(90, 170, 255)),
        DISPENSER(Items.DISPENSER, new Color(140, 140, 150)),
        COMMAND(Items.COMMAND_BLOCK, new Color(200, 160, 60));

        final Item icon;
        final Color fallbackColor;

        Target(Item icon, Color fallbackColor) {
            this.icon = icon;
            this.fallbackColor = fallbackColor;
        }
    }

    // ---- Public entry points ------------------------------------------------

    static void renderSingle(Painter p, int x, int y, int w, int h,
                             Target target, boolean tracked, Color color) {
        renderLockOn(p, x, y, w, h, target, tracked, color);
    }

    static void renderMaster(Painter p, int x, int y, int w, int h,
                             boolean enabled,
                             boolean fireballs, boolean witherSkulls, boolean windCharges) {
        List<SourceChip> chips = List.of(
                new SourceChip(Target.FIREBALL, fireballs && enabled),
                new SourceChip(Target.WITHER, witherSkulls && enabled),
                new SourceChip(Target.WIND, windCharges && enabled)
        );
        renderOverview(p, x, y, w, h, enabled, chips, "TYPES", Items.FIRE_CHARGE);
    }

    static void renderMobMaster(Painter p, int x, int y, int w, int h,
                                boolean enabled,
                                boolean blaze, boolean ghast,
                                boolean dragon, boolean wither) {
        List<SourceChip> chips = List.of(
                new SourceChip(Target.BLAZE, blaze && enabled),
                new SourceChip(Target.GHAST, ghast && enabled),
                new SourceChip(Target.DRAGON, dragon && enabled),
                new SourceChip(Target.WITHER, wither && enabled)
        );
        renderOverview(p, x, y, w, h, enabled, chips, "MOBS", Items.ENDERMAN_SPAWN_EGG);
    }

    static void renderOtherMaster(Painter p, int x, int y, int w, int h,
                                  boolean enabled,
                                  boolean player, boolean dispenser, boolean command) {
        List<SourceChip> chips = List.of(
                new SourceChip(Target.PLAYER, player && enabled),
                new SourceChip(Target.DISPENSER, dispenser && enabled),
                new SourceChip(Target.COMMAND, command && enabled)
        );
        renderOverview(p, x, y, w, h, enabled, chips, "OTHER", Items.TARGET);
    }

    // ---- Lock-on badge (single target) --------------------------------------

    private static void renderLockOn(Painter p, int x, int y, int w, int h,
                                     Target target, boolean tracked, Color color) {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        if (!tracked) {
            int lum = (r * 30 + g * 59 + b * 11) / 100;
            r = (r + lum * 3) / 4;
            g = (g + lum * 3) / 4;
            b = (b + lum * 3) / 4;
        }

        float time = seconds();

        int icon = Mth.clamp(Math.min(h - 8, w / 4), 10, 24);
        int ix = x + Math.max(3, w / 20);
        int iy = y + (h - icon) / 2;

        drawItemIcon(p, target.icon, ix, iy, icon, tracked ? 0xFF9AA3AE : 0xFF4A5058);

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

            float t = tracked ? (time * 0.55f) % 1.0f : 0.0f;
            float hx = arc.xAt(t);
            float hy = arc.yAt(t);
            softDisc(p, hx, hy, 3.0f, r, g, b, 0.28f * alphaScale);
            softDisc(p, hx, hy, 1.6f,
                    lighten(r, 0.35f), lighten(g, 0.35f), lighten(b, 0.35f),
                    0.95f * alphaScale);

            float pulse = tracked
                    ? 0.85f + 0.15f * (float) Math.sin(time * 3.4f)
                    : 1.0f;
            reticle(p, ex, cy, Math.max(3.5f, h * 0.16f) * pulse,
                    pack(r, g, b, tracked ? 230 : 70), tracked);
        }

        if (!tracked) {
            int slash = 0xCCE04A3C;
            p.line(ix - 2, iy + icon + 2, ix + icon + 2, iy - 2, slash);
            p.line(ix - 1, iy + icon + 2, ix + icon + 2, iy - 1, slash);
        }
    }

    // ---- Master / mob-master overview ---------------------------------------

    private record SourceChip(Target target, boolean active) {
    }

    /**
     * Miniature overview: status plate + a row of source chips.
     * Mirrors the "miniature screen" vocabulary used by {@link HudRenderer}.
     */
    private static void renderOverview(Painter p, int x, int y, int w, int h,
                                       boolean enabled, List<SourceChip> chips,
                                       String label, Item plateIcon) {
        float time = seconds();

        // Soft panel background
        // p.fill(x, y, x + w, y + h, 0xFF161B22);
        // p.fill(x, y, x + w, y + 1, 0x33FFFFFF);
        // p.fill(x, y + h - 1, x + w, y + h, 0x22000000);

        // Status plate (vanilla effect background when available)
        int plate = Math.max(14, Math.min(h - 6, w / 5));
        int px = x + Math.max(4, w / 24);
        int py = y + (h - plate) / 2;

        GuiGraphicsExtractor g = p.graphics();
        if (effectSpriteAvailable) {
            try {
                g.blitSprite(RenderPipelines.GUI_TEXTURED,
                        Identifier.withDefaultNamespace("hud/effect_background"),
                        px, py, plate, plate);
            } catch (RuntimeException | LinkageError ignored) {
                effectSpriteAvailable = false;
            }
        }
        if (!effectSpriteAvailable) {
            p.fill(px, py, px + plate, py + plate, 0xCC2A2A2A);
            p.fill(px, py, px + plate, py + 1, 0x66FFFFFF);
        }

        // Fire-charge or custom icon glyph on the plate
        // Fire charge texture has built-in transparent margin, so it needs smaller padding to visually match
        int iconPad = plateIcon == Items.FIRE_CHARGE
                ? Math.max(1, Math.round(plate * 0.10f))
                : Math.max(3, Math.round(plate * 0.22f));
        drawItemIcon(p, plateIcon, px + iconPad, py + iconPad,
                plate - iconPad * 2, enabled ? 0xFFE67A00 : 0xFF5A5A5A);

        // Status tick / cross
        int statusColor = enabled ? 0xFF5AD67A : 0xFFE04A3C;
        int cx = px + plate - 3;
        int cy = py + 3;
        if (enabled) {
            // small checkmark
            p.line(cx - 4, cy + 2, cx - 2, cy + 4, statusColor);
            p.line(cx - 2, cy + 4, cx + 2, cy - 1, statusColor);
        } else {
            p.line(cx - 3, cy - 1, cx + 1, cy + 3, statusColor);
            p.line(cx + 1, cy - 1, cx - 3, cy + 3, statusColor);
        }

        // Label under/beside plate
        // Minecraft mc = Minecraft.getInstance();
        // if (mc != null && mc.font != null) {
        //     String text = enabled ? label : "OFF";
        //     int tw = mc.font.width(text);
        //     int tx = Mth.clamp(px + (plate - tw) / 2, x + 2, x + w - tw - 2);
        //     int ty = Mth.clamp(py + plate + 1, y + 1, y + h - mc.font.lineHeight - 1);
        //     // Only draw if it fits without colliding with the chip row
        //     if (ty + mc.font.lineHeight <= y + h - 2 && plate + 6 < h) {
        //         g.text(mc.font, text, tx, ty, enabled ? 0xFFB8C0C8 : 0xFF8A5050);
        //     }
        // }

        // Chip row on the right
        int chipAreaLeft = px + plate + Math.max(6, w / 20);
        int chipAreaRight = x + w - Math.max(4, w / 30);
        int chipAreaW = Math.max(8, chipAreaRight - chipAreaLeft);
        int n = chips.size();
        int chip = Mth.clamp(Math.min(h - 8, chipAreaW / Math.max(1, n) - 2), 8, 18);
        int totalChipsW = n * chip + Math.max(0, n - 1) * 2;
        int startX = chipAreaLeft + Math.max(0, (chipAreaW - totalChipsW) / 2);
        int chipY = y + (h - chip) / 2;

        for (int i = 0; i < n; i++) {
            SourceChip source = chips.get(i);
            int cx0 = startX + i * (chip + 2);
            boolean on = enabled && source.active;

            // Chip plate
            int bg = on ? 0xFF2A3140 : 0xFF1A1F28;
            p.fill(cx0, chipY, cx0 + chip, chipY + chip, bg);
            // p.fill(cx0, chipY, cx0 + chip, chipY + 1, on ? 0x44FFFFFF : 0x22FFFFFF);

            int pad = Math.max(1, chip / 8);
            Color col = source.target.fallbackColor;
            if (!on) {
                int rr = col.getRed(), gg = col.getGreen(), bb = col.getBlue();
                int lum = (rr * 30 + gg * 59 + bb * 11) / 100;
                col = new Color((rr + lum * 3) / 4, (gg + lum * 3) / 4, (bb + lum * 3) / 4);
            }
            drawItemIcon(p, source.target.icon, cx0 + pad, chipY + pad,
                    chip - pad * 2, pack(col.getRed(), col.getGreen(), col.getBlue(), 255));

            if (on) {
                // Tiny pulse reticle corner
                float pulse = 0.7f + 0.3f * (float) Math.sin(time * 3.2f + i * 0.7f);
                int pr = Mth.clamp((int) (180 * pulse), 40, 220);
                int accent = pack(col.getRed(), col.getGreen(), col.getBlue(), pr);
                p.fill(cx0, chipY, cx0 + 2, chipY + 1, accent);
                p.fill(cx0, chipY, cx0 + 1, chipY + 2, accent);
                p.fill(cx0 + chip - 2, chipY + chip - 1, cx0 + chip, chipY + chip, accent);
                p.fill(cx0 + chip - 1, chipY + chip - 2, cx0 + chip, chipY + chip, accent);
            } else {
                // Dim slash
                p.line(cx0 + 1, chipY + chip - 2, cx0 + chip - 2, chipY + 1, 0x88E04A3C);
            }
        }

        // if (!enabled) {
        //     // Soft veil so the whole overview reads as off
        //     p.fill(x, y, x + w, y + h, 0x550A0C10);
        // }
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
        int x1 = icx + rr + 1;
        int y0 = icy - rr;
        int y1 = icy + rr + 1;

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
