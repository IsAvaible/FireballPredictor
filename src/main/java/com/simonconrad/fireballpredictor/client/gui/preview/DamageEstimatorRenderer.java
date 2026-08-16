package com.simonconrad.fireballpredictor.client.gui.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.*;

/**
 * Renders live schematic previews for damage estimation config options:
 * <ul>
 *   <li>Cracking damage hearts overlay on the player's health bar.</li>
 *   <li>Damage &amp; knockback speed HUD readout next to the impact warning badge.</li>
 * </ul>
 */
final class DamageEstimatorRenderer {

    private static final Identifier CONTAINER_SPRITE =
            Identifier.withDefaultNamespace("hud/heart/container");
    private static final Identifier FULL_SPRITE =
            Identifier.withDefaultNamespace("hud/heart/full");
    private static final Identifier CRACKING_FULL =
            Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_full");
    private static final Identifier CRACKING_FULL_BLINKING =
            Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_full_blinking");

    static boolean heartSpriteAvailable = true;

    private DamageEstimatorRenderer() {
    }

    // ---- Damage Hearts Overlay Preview --------------------------------------

    static void renderHearts(Painter p, int x, int y, int w, int h, boolean enabled) {
        GuiGraphicsExtractor g = p.graphics();

        // Miniature screen frame
        int framePad = 3;
        int fx = x + framePad;
        int fy = y + framePad;
        int fw = w - framePad * 2;
        int fh = h - framePad * 2;

        if (!enabled) {
            drawDisabledLabel(p, x, y, w, h);
            return;
        }

        float time = seconds();
        boolean blinking = ((int) (time * 4.5f)) % 2 == 0;

        // 10 Heart slots: 6 healthy (left), 4 cracked (right)
        int numHearts = 10;
        int heartSep = 8;
        int totalHeartsW = (numHearts - 1) * heartSep + 9;
        int hx0 = fx + (fw - totalHeartsW) / 2;
        int hy0 = fy + fh / 2 - 2;

        int crackedCount = 4;

        for (int i = 0; i < numHearts; i++) {
            int hx = hx0 + i * heartSep;
            boolean cracked = (i >= numHearts - crackedCount);
            drawHeart(g, p, hx, hy0, cracked, cracked && blinking);

            if (cracked) {
                // Rising fiery ember particles above cracked hearts
                float emberPhase = (time * 2.2f + i * 0.75f) % 1.0f;
                int emberY = hy0 - Math.round(emberPhase * 6.0f);
                int emberX = hx + 2 + Math.round((float) Math.sin(time * 3.5f + i * 1.2f) * 2.0f);
                int emberAlpha = Math.round(220 * (1.0f - emberPhase));
                p.pixel(emberX, emberY, pack(255, 140 + (int) (emberPhase * 80), 20, emberAlpha));
            }
        }
    }

    // ---- Knockback & Damage Readout Preview ---------------------------------

    static void renderKnockback(Painter p, int x, int y, int w, int h, boolean enabled) {
        GuiGraphicsExtractor g = p.graphics();

        // Miniature screen frame
        int framePad = 3;
        int fx = x + framePad;
        int fy = y + framePad;
        int fw = w - framePad * 2;
        int fh = h - framePad * 2;

        if (!enabled) {
            drawDisabledLabel(p, x, y, w, h);
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        // Prominent Readout Text
        if (mc != null && mc.font != null) {
            String readout = "-4.5\u2764  \u26a114.2b/s";
            int readoutX = fx + (fw - mc.font.width(readout)) / 2;
            int readoutY = fy + (fh - mc.font.lineHeight) / 2;

            // Draw vibrant text with drop shadow
            g.text(mc.font, readout, readoutX, readoutY, 0xFFE67A00, true);
        }
    }

    // ---- Heart Drawing Helpers ----------------------------------------------

    private static void drawHeart(GuiGraphicsExtractor g, Painter p, int x, int y, boolean cracked, boolean blinking) {
        boolean drawn = false;
        if (heartSpriteAvailable) {
            try {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, CONTAINER_SPRITE, x, y, 9, 9);
                g.blitSprite(RenderPipelines.GUI_TEXTURED, FULL_SPRITE, x, y, 9, 9);
                if (cracked) {
                    Identifier crackSprite = blinking ? CRACKING_FULL_BLINKING : CRACKING_FULL;
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, crackSprite, x, y, 9, 9);
                }
                drawn = true;
            } catch (RuntimeException | LinkageError ignored) {
                heartSpriteAvailable = false;
            }
        }
        if (!drawn) {
            drawProceduralHeart(p, x, y, cracked, blinking);
        }
    }

    private static void drawProceduralHeart(Painter p, int x, int y, boolean cracked, boolean blinking) {
        int border = 0xFF2A0000;
        int fill = cracked ? (blinking ? 0xFFFF7722 : 0xFFDD3311) : 0xFFEE1133;
        int highlight = cracked ? 0xFFFFDD44 : 0xFFFF8888;
        int crackColor = 0xFFFFCC00;

        // Outer border
        p.fill(x + 1, y, x + 3, y + 1, border);
        p.fill(x + 5, y, x + 7, y + 1, border);
        p.fill(x, y + 1, x + 1, y + 4, border);
        p.fill(x + 3, y + 1, x + 5, y + 2, border);
        p.fill(x + 7, y + 1, x + 8, y + 4, border);
        p.fill(x + 1, y + 4, x + 2, y + 5, border);
        p.fill(x + 6, y + 4, x + 7, y + 5, border);
        p.fill(x + 2, y + 5, x + 3, y + 6, border);
        p.fill(x + 5, y + 5, x + 6, y + 6, border);
        p.fill(x + 3, y + 6, x + 5, y + 7, border);

        // Inner body
        p.fill(x + 1, y + 1, x + 3, y + 4, fill);
        p.fill(x + 3, y + 2, x + 5, y + 5, fill);
        p.fill(x + 5, y + 1, x + 7, y + 4, fill);
        p.fill(x + 2, y + 4, x + 6, y + 5, fill);
        p.fill(x + 3, y + 5, x + 5, y + 6, fill);

        // Specular highlight
        p.pixel(x + 1, y + 1, highlight);

        // Fissure cracks
        if (cracked) {
            p.pixel(x + 2, y + 2, crackColor);
            p.pixel(x + 3, y + 3, crackColor);
            p.pixel(x + 4, y + 3, crackColor);
            p.pixel(x + 5, y + 2, crackColor);
            p.pixel(x + 4, y + 4, crackColor);
            p.pixel(x + 3, y + 5, crackColor);
        }
    }
}
