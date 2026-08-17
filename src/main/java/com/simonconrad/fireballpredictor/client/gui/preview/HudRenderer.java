package com.simonconrad.fireballpredictor.client.gui.preview;

import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.*;

/**
 * Renders the HUD schematic: a miniature game-screen frame with the impact-warning
 * badge placed according to the selected anchor and offset.
 */
final class HudRenderer {

    private HudRenderer() {
    }

    // ---- Public entry point -------------------------------------------------

    static void render(Painter p, int x, int y, int w, int h,
                       boolean show, ImpactWarningBadgeAnchor anchor,
                       int offX, int offY) {

        GuiGraphicsExtractor g = p.graphics();

        // Miniature "game screen" frame
        int framePad = 3;
        int fx = x + framePad;
        int fy = y + framePad;
        int fw = w - framePad * 2;
        int fh = h - framePad * 2;

        p.fill(fx, fy, fx + fw, fy + fh, 0xFF1A1F28);
        // Fake hotbar / crosshair hints so it reads as a screen
        p.fill(fx + fw / 2 - 8, fy + fh - 6, fx + fw / 2 + 8, fy + fh - 4, 0x33FFFFFF);
        p.fill(fx + fw / 2 - 1, fy + fh / 2 - 3, fx + fw / 2 + 1, fy + fh / 2 + 3, 0x44FFFFFF);
        p.fill(fx + fw / 2 - 3, fy + fh / 2 - 1, fx + fw / 2 + 3, fy + fh / 2 + 1, 0x44FFFFFF);
        // Border
        p.fill(fx, fy, fx + fw, fy + 1, 0x66FFFFFF);
        p.fill(fx, fy + fh - 1, fx + fw, fy + fh, 0x66FFFFFF);
        p.fill(fx, fy, fx + 1, fy + fh, 0x66FFFFFF);
        p.fill(fx + fw - 1, fy, fx + fw, fy + fh, 0x66FFFFFF);

        if (!show) {
            drawDisabledLabel(p, x, y, w, h);
            return;
        }

        int badge = Math.max(12, Math.min(fw, fh) / 5);
        int margin = Math.max(3, badge / 4);

        // Map large config offsets into the mini-frame (config range +-1000 -> a few badge widths)
        float scale = badge / 40.0f;
        int scaledOffX = Math.round(Mth.clamp(offX * scale, -fw / 3.0f, fw / 3.0f));
        int scaledOffY = Math.round(Mth.clamp(offY * scale, -fh / 3.0f, fh / 3.0f));

        int bx = switch (anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> fx + fw - badge - margin;
            case TOP_CENTER, BOTTOM_CENTER -> fx + (fw - badge) / 2;
            default -> fx + margin;
        } + scaledOffX;

        int by = switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> fy + fh - badge - margin;
            default -> fy + margin;
        } + scaledOffY;

        // Keep on-frame
        bx = Mth.clamp(bx, fx + 1, Math.max(fx + 1, fx + fw - badge - 1));
        by = Mth.clamp(by, fy + 1, Math.max(fy + 1, fy + fh - badge - 1));

        // Soft anchor guide lines
        p.fill(fx + 1, by + badge / 2, fx + fw - 1, by + badge / 2 + 1, 0x18FFAA44);
        p.fill(bx + badge / 2, fy + 1, bx + badge / 2 + 1, fy + fh - 1, 0x18FFAA44);

        // Badge background (vanilla effect plate if available)
        drawEffectBackground(p, g, bx, by, badge);

        // Icon
        int iconPad = Math.max(1, badge / 8);
        drawItemIcon(p, Items.FIRE_CHARGE, bx + iconPad, by + iconPad,
                badge - iconPad * 2, 0xFFE67A00);

        // Progress bar
        float progress = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(seconds() * 2.2f));
        int barX = bx + 2;
        int barY = by + badge - 3;
        int barW = badge - 4;
        int filled = Math.max(1, Math.round(progress * barW));
        p.fill(barX, barY, barX + barW, barY + 1, 0xAA1A0B00);
        p.fill(barX, barY, barX + filled, barY + 1, 0xFFE67A00);

        // Offset readout
        if (offX != 0 || offY != 0) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                String label = offX + "," + offY;
                int tw = mc.font.width(label);
                int tx = Mth.clamp(bx + (badge - tw) / 2,
                        fx + 2, Math.max(fx + 2, fx + fw - tw - 2));
                int ty = Mth.clamp(by + badge + 2,
                        fy + 2, Math.max(fy + 2, fy + fh - mc.font.lineHeight - 1));
                g.text(mc.font, label, tx, ty, 0xFFB0B8C0);
            }
        }
    }
}
