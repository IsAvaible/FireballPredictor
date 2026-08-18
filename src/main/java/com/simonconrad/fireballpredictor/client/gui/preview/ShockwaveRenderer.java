package com.simonconrad.fireballpredictor.client.gui.preview;

import com.simonconrad.fireballpredictor.config.VisualTheme;
import net.minecraft.util.Mth;

import java.awt.Color;

import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.*;

/**
 * Renders the shockwave schematic: a 3x3 block grid with an expanding/contracting
 * translucent dome and optional crack overlays.
 */
final class ShockwaveRenderer {

    private ShockwaveRenderer() {
    }

    // ---- Public entry point -------------------------------------------------

    static void render(Painter p, int x, int y, int w, int h, boolean wind,
                       boolean showDome, boolean showBlocks, Color color,
                       float fresnelStrength, VisualTheme theme, float animSpeed) {

        float time = seconds() * Math.max(0.0f, animSpeed);
        VisualTheme activeTheme = theme == null ? VisualTheme.DEFAULT : theme;

        final int grid = 3;
        int cell = Math.max(6, Math.min(w, h) / (grid + 1));
        int gridW = cell * grid;
        int gridH = cell * grid;
        int gx0 = x + (w - gridW) / 2;
        int gy0 = y + (h - gridH) / 2 + h / 16;

        // 3x3 block grid
        for (int row = 0; row < grid; row++) {
            for (int col = 0; col < grid; col++) {
                int bx = gx0 + col * cell;
                int by = gy0 + row * cell;
                p.fill(bx + 1, by + 1, bx + cell - 1, by + cell - 1, 0xFF3A414C);
                p.fill(bx + 1, by + cell - 3, bx + cell - 1, by + cell - 1, 0xFF2A3038);
                p.fill(bx + 1, by + 1, bx + cell - 1, by + 2, 0x22FFFFFF);

                if (showBlocks) {
                    // Staggered "cracking" severity for schematic feel
                    int severity = (row + col) % 3;
                    double flash = 0.55 + 0.45 * Math.sin(time * 4.5 + row * 1.3 + col);
                    int crackA = Mth.clamp((int) ((40 + severity * 45) * flash), 0, 255);
                    int crack = (crackA << 24) | 0x00E0E0E0;
                    p.line(bx + 3, by + 3, bx + cell - 4, by + cell - 4, crack);
                    p.line(bx + cell - 4, by + 3, bx + 3, by + cell - 4, crack);
                }
            }
        }

        if (!showDome) {
            if (!showBlocks) {
                drawDisabledLabel(p, x, y, w, h);
            }
            return;
        }

        float cx = gx0 + gridW / 2.0f;
        float cy = gy0 + gridH / 2.0f;
        float pulse = animSpeed <= 0.0f ? 1.0f : (0.82f + 0.18f * (float) Math.sin(time * 3.1f));
        float radius = Math.min(gridW, gridH) * 0.58f * pulse;

        int fallbackRgb = VisualTheme.packRgb(color.getRed(), color.getGreen(), color.getBlue());
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        // Translucent dome body with 3D radial Fresnel falloff rendered via stepped radial spans.
        int minY = Mth.floor(cy - radius);
        int maxY = Mth.ceil(cy + radius);
        float fresnelWeight = Mth.clamp(fresnelStrength, 0.0f, 1.0f);

        final int numBands = 16;
        float[] bandRadiiSq = new float[numBands + 1];
        int[] bandColors = new int[numBands];

        for (int i = 0; i <= numBands; i++) {
            float rBand = radius * ((float) i / numBands);
            bandRadiiSq[i] = rBand * rBand;
        }

        for (int i = 0; i < numBands; i++) {
            float d = (i + 0.5f) / numBands;
            float fresnel = 1.0f - (float) Math.sqrt(Math.max(0.0f, 1.0f - d * d));
            float flatAlpha = 70.0f;
            float modulatedAlpha = Mth.lerp(fresnel, 20.0f, 135.0f);
            float baseAlpha = Mth.lerp(fresnelWeight, flatAlpha, modulatedAlpha);
            float alphaMod = activeTheme.getDomeAlphaModulation(d, 0.0f, time);
            int a = Mth.clamp((int) (baseAlpha * pulse * alphaMod), 0, 255);

            int bandRgb = activeTheme.getDomeColorPacked(null, null, d, 0.0f, time, fallbackRgb);
            bandColors[i] = pack(bandRgb, a);
        }

        for (int py = minY; py <= maxY; py++) {
            float dy = py + 0.5f - cy;
            float dySq = dy * dy;
            if (dySq >= bandRadiiSq[numBands]) {
                continue;
            }

            float prevDx = 0.0f;
            for (int i = 0; i < numBands; i++) {
                float outerR2 = bandRadiiSq[i + 1];
                if (outerR2 <= dySq) {
                    continue;
                }
                float nextDx = (float) Math.sqrt(outerR2 - dySq);
                int bandColor = bandColors[i];

                if (prevDx == 0.0f) {
                    p.fillF(cx - nextDx, py, cx + nextDx, py + 1, bandColor);
                } else {
                    p.fillF(cx - nextDx, py, cx - prevDx, py + 1, bandColor);
                    p.fillF(cx + prevDx, py, cx + nextDx, py + 1, bandColor);
                }
                prevDx = nextDx;
            }
        }

        // Rim + inner echo ring
        int rimRgb = activeTheme.getDomeColorPacked(null, null, 1.0f, 0.0f, time, fallbackRgb);
        ring(p, cx, cy, radius, 1.6f,
                pack(rimRgb, Mth.clamp((int) (200 * pulse), 0, 255)));
        ring(p, cx, cy, radius * 0.72f, 1.0f,
                pack(rimRgb, Mth.clamp((int) (70 * pulse), 0, 120)));

        // Epicentre
        int epiRgb = activeTheme.getDomeColorPacked(null, null, 0.0f, 0.0f, time, fallbackRgb);
        int er = VisualTheme.extractR(epiRgb);
        int eg = VisualTheme.extractG(epiRgb);
        int eb = VisualTheme.extractB(epiRgb);

        softDisc(p, cx, cy, 2.2f,
                lighten(er, 0.2f), lighten(eg, 0.2f), lighten(eb, 0.2f), 0.86f);

        // Theme-specific decorative overlays across the shockwave dome
        if (activeTheme.isCustomTheme()) {
            PreviewThemeDecorations.renderDomeThemeDecorations(p, activeTheme, cx, cy, radius, pulse, time, fallbackRgb);
        }
    }
}
