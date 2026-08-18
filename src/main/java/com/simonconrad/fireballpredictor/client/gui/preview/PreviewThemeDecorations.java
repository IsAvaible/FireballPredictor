package com.simonconrad.fireballpredictor.client.gui.preview;

import com.simonconrad.fireballpredictor.client.render.ThemeVisualAssets;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import net.minecraft.util.Mth;

import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.pack;
import static com.simonconrad.fireballpredictor.client.gui.preview.RenderUtils.softDisc;

/**
 * Handles 2D schematic preview theme overlays and theme-specific UI drawing primitives
 * for config description side-panels.
 */
public final class PreviewThemeDecorations {

    private PreviewThemeDecorations() {
    }

    /**
     * Renders theme-specific decorative overlays along the 2D parabolic trajectory arc.
     */
    public static void renderTrajectoryThemeDecorations(
            Painter p, Arc arc, VisualTheme theme, float time,
            float left, float right, float groundY,
            float halfWidth, int fallbackRgb
    ) {
        switch (theme) {
            case ELECTRIC_ARC -> {
                // High-voltage plasma arcs branching and jumping off trajectory
                int timeStep = (int) (time * 14.0f);
                int branches = 4;
                for (int b = 0; b < branches; b++) {
                    int seed = (b * 37 + timeStep * 19) % 7;
                    if (seed > 3) continue;
                    float t1 = 0.12f + b * 0.22f;
                    float t2 = t1 + 0.14f;
                    float x1 = arc.xAt(t1), y1 = arc.yAt(t1);
                    float x2 = arc.xAt(t2), y2 = arc.yAt(t2);
                    float jSign = (seed % 2 == 0) ? 1.0f : -1.0f;
                    float jDist = (halfWidth * 1.8f + (seed * 1.2f)) * jSign;
                    float mx = (x1 + x2) * 0.5f;
                    float my = arc.yAt((t1 + t2) * 0.5f) + jDist;

                    // Corona
                    p.line(x1, y1, mx, my, 0xB000E5FF);
                    p.line(mx, my, x2, y2, 0xB00284C7);
                    // Core
                    p.line(x1, y1, mx, my, 0xE0FFFFFF);
                    p.line(mx, my, x2, y2, 0xE0FFFFFF);
                }
            }
            case SCULK_VOID -> {
                // Undulating organic Warden soul tendril
                int steps = 28;
                float prevX = arc.xAt(0.05f);
                float prevY = arc.yAt(0.05f);
                for (int s = 1; s <= steps; s++) {
                    float t = 0.05f + (s / (float) steps) * 0.88f;
                    float tx = arc.xAt(t);
                    float ty = arc.yAt(t);
                    float wave = (float) Math.sin(time * -4.5f + t * 14.0f) * (halfWidth * 1.3f);
                    float cy = ty + wave;
                    p.line(prevX, prevY, tx, cy, 0xD000F5D4);
                    prevX = tx;
                    prevY = cy;
                }
            }
            case INFERNO -> {
                // Licking flame tongues along top of arc
                int flameSteps = 8;
                for (int f = 0; f < flameSteps; f++) {
                    float t = 0.10f + f * 0.10f;
                    float fx = arc.xAt(t);
                    float fy = arc.yAt(t);
                    float flamePhase = time * 7.5f - f * 0.7f;
                    float flameH = (0.35f + 0.55f * Math.max(0.0f, (float) Math.sin(flamePhase))) * halfWidth * 2.0f;
                    float flameSway = (float) Math.sin(flamePhase * 1.4f) * (halfWidth * 0.4f);
                    p.line(fx, fy, fx + flameSway, fy - flameH, 0xD0FF5500);
                    p.line(fx, fy, fx + flameSway * 0.5f, fy - flameH * 0.6f, 0xF0FFAA00);
                }
                // Rising embers
                int emberCount = 5;
                for (int e = 0; e < emberCount; e++) {
                    float t = ((time * 0.4f + e * 0.22f) % 1.0f) * 0.75f + 0.10f;
                    float ex = arc.xAt(t) + (float) Math.sin(time * 3.0f + e * 2.0f) * 4.0f;
                    float rise = ((time * 1.5f + e * 0.35f) % 1.0f) * 14.0f;
                    float ey = arc.yAt(t) - rise;
                    float fade = 1.0f - (rise / 14.0f);
                    int alpha = Math.round(240 * fade);
                    drawDiamondGlint(p, ex, ey, 1.5f, pack(255, 200, 50, alpha));
                }
            }
            case GHOST -> {
                // Spectral soul wisps and floating turquoise soul orbs
                int wispSteps = 24;
                float prevX = arc.xAt(0.08f);
                float prevY = arc.yAt(0.08f);
                for (int s = 1; s <= wispSteps; s++) {
                    float t = 0.08f + (s / (float) wispSteps) * 0.82f;
                    float tx = arc.xAt(t);
                    float ty = arc.yAt(t);
                    float wave = (float) Math.sin(time * 2.8f - t * 8.0f) * (halfWidth * 1.1f);
                    float cy = ty + wave;
                    p.line(prevX, prevY, tx, cy, 0xA02DD4BF);
                    prevX = tx;
                    prevY = cy;
                }
                // Rising spirit orbs
                int orbCount = 4;
                for (int o = 0; o < orbCount; o++) {
                    float t = ((time * 0.25f + o * 0.25f) % 1.0f) * 0.70f + 0.15f;
                    float ox = arc.xAt(t) + (float) Math.sin(time * 2.0f + o * 1.7f) * 3.5f;
                    float rise = ((time * 1.2f + o * 0.29f) % 1.0f) * 12.0f;
                    float oy = arc.yAt(t) - rise;
                    float fade = 1.0f - (rise / 12.0f);
                    softDisc(p, ox, oy, 1.8f, 45, 212, 191, 0.75f * fade);
                }
            }
            case MATRIX -> {
                // 3x5 matrix digital glyphs stamped along the trajectory
                int glyphCount = 5;
                for (int g = 0; g < glyphCount; g++) {
                    float t = 0.15f + g * 0.16f;
                    float gx = arc.xAt(t);
                    float gy = arc.yAt(t) - halfWidth * 1.5f;
                    int glyphIdx = (int) (time * 6.0f + g * 3) & 15;
                    drawMatrixGlyph(p, gx, gy, 1.2f, glyphIdx, 0xD000FF41);
                }
            }
            case AURORA -> {
                // Drifting ice crystal hex glints
                int glintCount = 5;
                for (int i = 0; i < glintCount; i++) {
                    float t = ((time * 0.35f + i * 0.22f) % 1.0f) * 0.75f + 0.10f;
                    float gx = arc.xAt(t) + (float) Math.sin(time * 2.5f + i * 1.5f) * 4.0f;
                    float drift = ((time * 1.2f + i * 0.31f) % 1.0f) * 11.0f;
                    float gy = arc.yAt(t) - drift;
                    float fade = 1.0f - (drift / 11.0f);
                    drawHexGlint(p, gx, gy, 2.5f, pack(96, 239, 255, Math.round(230 * fade)));
                }
            }
            case SINGULARITY -> {
                // Accretion glints and photon particles orbiting along the trajectory
                int pCount = 6;
                for (int i = 0; i < pCount; i++) {
                    float t = 0.15f + i * 0.13f;
                    float angle = time * 4.5f + i * 1.2f;
                    float rDist = halfWidth * 1.6f;
                    float px = arc.xAt(t) + (float) Math.cos(angle) * rDist;
                    float py = arc.yAt(t) + (float) Math.sin(angle) * rDist * 0.8f;
                    int col = (i % 2 == 0) ? 0xE0FF6A00 : 0xE0D946EF;
                    drawDiamondGlint(p, px, py, 1.8f, col);
                }
            }
            case SAKURA -> {
                // Fluttering 5-petal sakura blossoms
                int flowerCount = 3;
                for (int f = 0; f < flowerCount; f++) {
                    float t = 0.20f + f * 0.28f;
                    float fx = arc.xAt(t) + (float) Math.sin(time * 1.8f + f) * 3.0f;
                    float fy = arc.yAt(t) - halfWidth * 1.4f;
                    drawSakuraFlower(p, fx, fy, 2.8f, time * 2.0f + f * 1.5f, 0xE0F472B6, 0xFFFEF08A);
                }
                // Drifting petals
                int petalCount = 4;
                for (int pt = 0; pt < petalCount; pt++) {
                    float t = ((time * 0.3f + pt * 0.25f) % 1.0f) * 0.70f + 0.15f;
                    float px = arc.xAt(t) + (float) Math.sin(time * 2.8f + pt * 1.3f) * 5.0f;
                    float fall = ((time * 1.0f + pt * 0.29f) % 1.0f) * 10.0f;
                    float py = arc.yAt(t) + fall - 4.0f;
                    float fade = 1.0f - (fall / 10.0f);
                    drawSakuraPetal(p, px, py, 1.8f, time * 2.5f + pt, pack(251, 113, 133, Math.round(220 * fade)));
                }
            }
            case CRYSTAL -> {
                // Prismatic crystal facet diamonds
                int diamondCount = 6;
                for (int d = 0; d < diamondCount; d++) {
                    float t = 0.12f + d * 0.14f;
                    float dx = arc.xAt(t);
                    float dy = arc.yAt(t) + (d % 2 == 0 ? -1 : 1) * halfWidth * 1.3f;
                    float sparkle = (float) Math.sin(time * 4.0f + d * 1.2f);
                    if (sparkle > 0.1f) {
                        int col = sparkle > 0.6f ? 0xFFFFFFFF : ((d % 2 == 0) ? 0xFFE9D5FF : 0xFF34D399);
                        drawDiamondGlint(p, dx, dy, 2.2f + sparkle * 1.0f, col);
                    }
                }
            }
            case ARCADE -> {
                // 5x5 retro 8-bit arcade bitmap sprites stamped along the arc
                int spriteCount = 4;
                for (int s = 0; s < spriteCount; s++) {
                    float t = 0.16f + s * 0.22f;
                    float sx = arc.xAt(t);
                    float sy = arc.yAt(t) - halfWidth * 1.8f;
                    int spriteIdx = (s + (int) (time * 3.0f)) & 7;
                    int colPacked = ThemeVisualAssets.getArcadeSpriteColorPacked(spriteIdx);
                    drawArcadeSprite(p, sx, sy, 1.1f, spriteIdx, pack(colPacked, 240));
                }
            }
            case TACTICAL_HUD -> {
                // Range notch tick marks crossing the trajectory
                int notchCount = 6;
                for (int n = 0; n < notchCount; n++) {
                    float t = 0.12f + n * 0.14f;
                    float nx = arc.xAt(t);
                    float ny = arc.yAt(t);
                    float slope = arc.slopeAt(t);
                    float perpDx = -slope * 2.5f;
                    float perpDy = 2.5f;
                    p.line(nx - perpDx, ny - perpDy, nx + perpDx, ny + perpDy, 0xD0FBBF24);
                }
                // Escort fighter jet cruising along trajectory with afterburner contrails
                float jetT = ((time * 0.35f) % 1.0f);
                float jx = arc.xAt(jetT);
                float jy = arc.yAt(jetT) - halfWidth * 1.8f - 2.0f;
                float slope = arc.slopeAt(jetT);
                float angle = (float) Math.atan(slope);
                float cosA = (float) Math.cos(angle);
                float sinA = (float) Math.sin(angle);
                float scale = 4.5f;

                float noseX = jx + cosA * scale;
                float noseY = jy + sinA * scale;
                float tailX = jx - cosA * scale * 0.7f;
                float tailY = jy - sinA * scale * 0.7f;
                float lWingX = jx - cosA * scale * 0.2f - sinA * scale * 0.8f;
                float lWingY = jy - sinA * scale * 0.2f + cosA * scale * 0.8f;
                float rWingX = jx - cosA * scale * 0.2f + sinA * scale * 0.8f;
                float rWingY = jy - sinA * scale * 0.2f - cosA * scale * 0.8f;

                p.line(noseX, noseY, lWingX, lWingY, 0xFFF59E0B);
                p.line(noseX, noseY, rWingX, rWingY, 0xFFF59E0B);
                p.line(lWingX, lWingY, tailX, tailY, 0xFFF59E0B);
                p.line(rWingX, rWingY, tailX, tailY, 0xFFF59E0B);

                // Afterburner flame
                float flameLen = scale * 1.2f * (0.8f + 0.2f * (float) Math.sin(time * 12.0f));
                float flameX = tailX - cosA * flameLen;
                float flameY = tailY - sinA * flameLen;
                p.line(tailX, tailY, flameX, flameY, 0xFFFEF08A);
            }
            default -> {}
        }
    }

    /**
     * Renders theme-specific decorative overlays across the 2D shockwave dome preview.
     */
    public static void renderDomeThemeDecorations(
            Painter p, VisualTheme theme, float cx, float cy,
            float radius, float pulse, float time, int fallbackRgb
    ) {
        switch (theme) {
            case CELESTIAL -> {
                // Twinkling stars scattered inside the celestial nebula dome
                int starCount = 14;
                for (int s = 0; s < starCount; s++) {
                    double angle = s * (Math.PI * 2.0 / starCount) + s * 1.3;
                    float rDist = ((s * 23 % 100) / 100.0f * 0.75f + 0.10f) * radius;
                    float sx = cx + (float) Math.cos(angle) * rDist;
                    float sy = cy + (float) Math.sin(angle) * rDist;
                    float twinkle = (float) Math.sin(time * 3.5f + s * 1.8f);
                    if (twinkle > -0.1f) {
                        int col = twinkle > 0.5f ? 0xFFFFFFFF : 0xFFDDD6FE;
                        int a = Math.round(Mth.clamp((0.6f + 0.4f * twinkle) * 255 * pulse, 0, 255));
                        drawDiamondGlint(p, sx, sy, 1.6f + Math.max(0.0f, twinkle) * 0.8f, pack(col, a));
                    }
                }
            }
            case MATRIX -> {
                // Falling vertical matrix digital code rain streams
                int cols = 5;
                for (int c = 0; c < cols; c++) {
                    float colX = cx + (c - (cols - 1) * 0.5f) * (radius * 0.38f);
                    float speed = 1.2f + (c % 3) * 0.3f;
                    float streamY = ((time * speed + c * 0.23f) % 1.0f);

                    for (int charIdx = 0; charIdx < 3; charIdx++) {
                        float prog = (streamY - charIdx * 0.18f + 1.0f) % 1.0f;
                        float charY = cy - radius * 0.8f + prog * radius * 1.6f;
                        float dist = (float) Math.sqrt((colX - cx) * (colX - cx) + (charY - cy) * (charY - cy));
                        if (dist > radius * 0.95f) continue;

                        int glyph = (int) (time * 5.0f + c * 4 + charIdx) & 15;
                        int col = (charIdx == 0) ? 0xE6FFE6 : (charIdx == 1 ? 0x00FF41 : 0x059669);
                        int alpha = (charIdx == 0) ? 240 : Math.max(40, 200 - charIdx * 60);
                        drawMatrixGlyph(p, colX, charY, 1.1f, glyph, pack(col, alpha));
                    }
                }
            }
            case INFERNO -> {
                // Volcanic fissure pulses across the dome
                int fissures = 4;
                for (int f = 0; f < fissures; f++) {
                    double a1 = f * (Math.PI * 2.0 / fissures) + Math.sin(time * 1.5f + f) * 0.2;
                    float fx = cx + (float) Math.cos(a1) * radius * 0.85f;
                    float fy = cy + (float) Math.sin(a1) * radius * 0.85f;
                    float mx = (cx + fx) * 0.5f + (float) Math.sin(time * 2.0f + f) * 3.0f;
                    float my = (cy + fy) * 0.5f + (float) Math.cos(time * 2.0f + f) * 3.0f;
                    p.line(cx, cy, mx, my, 0xC0FF5500);
                    p.line(mx, my, fx, fy, 0xC0FF5500);
                    p.line(cx, cy, mx, my, 0xE0FFEA00);
                }
                // Rising volcanic embers
                int emberCount = 6;
                for (int e = 0; e < emberCount; e++) {
                    float angle = (e * 2.0f * (float) Math.PI / emberCount) + time * 0.4f;
                    float rise = ((time * 1.2f + e * 0.27f) % 1.0f) * radius * 1.3f;
                    float ex = cx + (float) Math.cos(angle) * (radius * 0.6f) + (float) Math.sin(time * 3.0f + e) * 2.5f;
                    float ey = cy + radius * 0.6f - rise;
                    float fade = 1.0f - (rise / (radius * 1.3f));
                    drawDiamondGlint(p, ex, ey, 1.4f, pack(0xFFCC00, Math.round(230 * fade)));
                }
            }
            case GHOST -> {
                // Swirling spiritual soul vortex and floating spirit orbs
                int orbs = 5;
                for (int o = 0; o < orbs; o++) {
                    float angle = time * 1.5f + o * (2.0f * (float) Math.PI / orbs);
                    float rDist = (0.35f + 0.45f * (float) Math.sin(time * 0.8f + o)) * radius;
                    float ox = cx + (float) Math.cos(angle) * rDist;
                    float oy = cy + (float) Math.sin(angle) * rDist;
                    softDisc(p, ox, oy, 2.0f, 45, 212, 191, 0.80f * pulse);
                }
            }
            case SCULK_VOID -> {
                // Expanding concentric sonic boom ripple rings
                int rings = 3;
                for (int r = 0; r < rings; r++) {
                    float ringPhase = ((time * 0.8f + r * 0.33f) % 1.0f);
                    float ringR = ringPhase * radius * 1.05f;
                    float fade = (1.0f - ringPhase) * (1.0f - ringPhase);
                    int alpha = Math.round(220 * fade * pulse);
                    RenderUtils.ring(p, cx, cy, ringR, 1.2f, pack(0x00F5D4, alpha));
                }
            }
            case ELECTRIC_ARC -> {
                // High-voltage lightning channels across the dome shell
                int arcStep = (int) (time * 14.0f);
                int lightningChannels = 4;
                for (int a = 0; a < lightningChannels; a++) {
                    int seed = (a * 31 + arcStep * 17) % 7;
                    if (seed > 3) continue;
                    double angle = (a * (Math.PI * 2.0 / lightningChannels)) + ((seed * 13) % 100) / 100.0 * 0.4;
                    float tipX = cx + (float) Math.cos(angle) * radius * 0.95f;
                    float tipY = cy + (float) Math.sin(angle) * radius * 0.95f;
                    float jSign = (seed % 2 == 0) ? 1.0f : -1.0f;
                    float jDist = (3.0f + seed * 1.5f) * jSign;
                    float perpX = -(float) Math.sin(angle) * jDist;
                    float perpY = (float) Math.cos(angle) * jDist;
                    float midX = (cx + tipX) * 0.5f + perpX;
                    float midY = (cy + tipY) * 0.5f + perpY;

                    p.line(cx, cy, midX, midY, 0xB000E5FF);
                    p.line(midX, midY, tipX, tipY, 0xB000E5FF);
                    p.line(cx, cy, midX, midY, 0xE0FFFFFF);
                    p.line(midX, midY, tipX, tipY, 0xE0FFFFFF);
                }
            }
            case TACTICAL_HUD -> {
                // Rotating aviation radar sweep beam and reticle rings
                float sweepAngle = (time * 2.8f) % (2.0f * (float) Math.PI);
                float swX = cx + (float) Math.cos(sweepAngle) * radius;
                float swY = cy + (float) Math.sin(sweepAngle) * radius;

                // Fading sweep wake sector lines
                for (int w = 1; w <= 5; w++) {
                    float wakeAngle = sweepAngle - w * 0.08f;
                    float wx = cx + (float) Math.cos(wakeAngle) * radius;
                    float wy = cy + (float) Math.sin(wakeAngle) * radius;
                    int wakeAlpha = Math.round(180 * (1.0f - w / 5.5f));
                    p.line(cx, cy, wx, wy, pack(0xF59E0B, wakeAlpha));
                }
                // Searing sweep line
                p.line(cx, cy, swX, swY, 0xFFFEF08A);

                // Concentric altitude rings and crosshairs
                RenderUtils.ring(p, cx, cy, radius * 0.50f, 1.0f, 0x60F59E0B);
                p.line(cx - radius * 0.95f, cy, cx + radius * 0.95f, cy, 0x40F59E0B);
                p.line(cx, cy - radius * 0.95f, cx, cy + radius * 0.95f, 0x40F59E0B);
            }
            case AURORA -> {
                // Aurora polar curtain waves and ice crystal glints
                int glintCount = 6;
                for (int g = 0; g < glintCount; g++) {
                    double angle = g * (Math.PI * 2.0 / glintCount) + time * 0.5;
                    float rDist = (0.4f + 0.4f * (float) Math.sin(time * 1.2f + g)) * radius;
                    float gx = cx + (float) Math.cos(angle) * rDist;
                    float gy = cy + (float) Math.sin(angle) * rDist;
                    drawHexGlint(p, gx, gy, 2.2f, 0xD060EFFF);
                }
            }
            case SINGULARITY -> {
                // Central black hole shadow void
                float rEH = radius * 0.38f;
                softDisc(p, cx, cy, rEH, 4, 1, 10, 0.95f);
                // Incandescent photon sphere ring
                RenderUtils.ring(p, cx, cy, rEH * 1.15f, 1.5f, 0xFFFFFFFF);
                RenderUtils.ring(p, cx, cy, rEH * 1.35f, 1.0f, 0xE0FF6A00);
                // Accretion disk swirling lines
                int spiralArms = 4;
                for (int a = 0; a < spiralArms; a++) {
                    float aRot = time * 3.5f + a * (2.0f * (float) Math.PI / spiralArms);
                    float inX = cx + (float) Math.cos(aRot) * rEH * 1.2f;
                    float inY = cy + (float) Math.sin(aRot) * rEH * 1.2f;
                    float outX = cx + (float) Math.cos(aRot + 0.8f) * radius * 0.95f;
                    float outY = cy + (float) Math.sin(aRot + 0.8f) * radius * 0.95f;
                    p.line(inX, inY, outX, outY, (a % 2 == 0) ? 0xC0FB923C : 0xC0C026D3);
                }
            }
            case SAKURA -> {
                // 5-petal sakura flower ground base & swirling blossoms
                drawSakuraFlower(p, cx, cy, radius * 0.45f, time * 0.8f, 0xD0F472B6, 0xFFFEF08A);
                // Orbiting petals
                int petals = 5;
                for (int pt = 0; pt < petals; pt++) {
                    float angle = time * 1.8f + pt * (2.0f * (float) Math.PI / petals);
                    float rDist = (0.55f + 0.35f * (float) Math.sin(time * 1.2f + pt)) * radius;
                    float px = cx + (float) Math.cos(angle) * rDist;
                    float py = cy + (float) Math.sin(angle) * rDist;
                    drawSakuraPetal(p, px, py, 2.0f, angle + (float) Math.PI * 0.5f, 0xD0FB7185);
                }
            }
            case CRYSTAL -> {
                // Geode facet diamonds sparkling across the dome shell
                int diamonds = 8;
                for (int d = 0; d < diamonds; d++) {
                    double angle = d * (Math.PI * 2.0 / diamonds) + 0.2;
                    float rDist = (0.35f + (d % 3) * 0.25f) * radius;
                    float dx = cx + (float) Math.cos(angle) * rDist;
                    float dy = cy + (float) Math.sin(angle) * rDist;
                    float sparkle = (float) Math.sin(time * 4.5f + d * 1.5f);
                    if (sparkle > 0.0f) {
                        int col = sparkle > 0.5f ? 0xFFFFFFFF : 0xFFE9D5FF;
                        drawDiamondGlint(p, dx, dy, 2.2f + sparkle * 1.2f, col);
                    }
                }
            }
            case ARCADE -> {
                // Orbiting 5x5 retro arcade sprites around dome perimeter
                int spriteCount = 4;
                for (int s = 0; s < spriteCount; s++) {
                    float angle = time * 1.2f + s * (2.0f * (float) Math.PI / spriteCount);
                    float sx = cx + (float) Math.cos(angle) * radius * 0.78f;
                    float sy = cy + (float) Math.sin(angle) * radius * 0.78f;
                    int spriteIdx = (s + (int) (time * 2.5f)) & 7;
                    int col = ThemeVisualAssets.getArcadeSpriteColorPacked(spriteIdx);
                    drawArcadeSprite(p, sx, sy, 1.1f, spriteIdx, pack(col, 240));
                }
            }
            default -> {}
        }
    }

    // =========================================================================
    // 2D Thematic Drawing Primitives
    // =========================================================================

    /** Draws a 3x5 matrix glyph centered at (cx, cy). */
    public static void drawMatrixGlyph(Painter p, float cx, float cy, float cellSize, int glyphIndex, int argb) {
        if ((argb >>> 24) == 0 || cellSize <= 0.0f) return;
        int glyph = ThemeVisualAssets.MATRIX_GLYPHS[glyphIndex & 15] & 0xFFFF;
        int cols = 3, rows = 5;
        float halfW = (cols * cellSize) * 0.5f;
        float halfH = (rows * cellSize) * 0.5f;
        int x0 = Math.round(cx - halfW);
        int y0 = Math.round(cy - halfH);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int bitIndex = (rows - 1 - r) * cols + (cols - 1 - c);
                if (((glyph >> bitIndex) & 1) == 1) {
                    int px = Math.round(x0 + c * cellSize);
                    int py = Math.round(y0 + r * cellSize);
                    int sz = Math.max(1, Math.round(cellSize));
                    p.fill(px, py, px + sz, py + sz, argb);
                }
            }
        }
    }

    /** Draws a 5x5 retro arcade sprite centered at (cx, cy). */
    public static void drawArcadeSprite(Painter p, float cx, float cy, float cellSize, int spriteIndex, int argb) {
        if ((argb >>> 24) == 0 || cellSize <= 0.0f) return;
        int sprite = ThemeVisualAssets.ARCADE_SPRITES[spriteIndex & 7];
        int cols = 5, rows = 5;
        float halfW = (cols * cellSize) * 0.5f;
        float halfH = (rows * cellSize) * 0.5f;
        int x0 = Math.round(cx - halfW);
        int y0 = Math.round(cy - halfH);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int bitIndex = (rows - 1 - r) * cols + (cols - 1 - c);
                if (((sprite >> bitIndex) & 1) == 1) {
                    int px = Math.round(x0 + c * cellSize);
                    int py = Math.round(y0 + r * cellSize);
                    int sz = Math.max(1, Math.round(cellSize));
                    p.fill(px, py, px + sz, py + sz, argb);
                }
            }
        }
    }

    /** Draws a 4-point diamond sparkle glint centered at (cx, cy). */
    public static void drawDiamondGlint(Painter p, float cx, float cy, float size, int argb) {
        if ((argb >>> 24) == 0 || size <= 0.0f) return;
        p.line(cx - size, cy, cx + size, cy, argb);
        p.line(cx, cy - size, cx, cy + size, argb);
        if (size >= 2.0f) {
            float inner = size * 0.5f;
            p.fillF(cx - inner, cy - inner, cx + inner, cy + inner, argb);
        }
    }

    /** Draws an 8-point ice / star glint centered at (cx, cy). */
    public static void drawHexGlint(Painter p, float cx, float cy, float size, int argb) {
        if ((argb >>> 24) == 0 || size <= 0.0f) return;
        drawDiamondGlint(p, cx, cy, size, argb);
        float diag = size * 0.65f;
        p.line(cx - diag, cy - diag, cx + diag, cy + diag, argb);
        p.line(cx - diag, cy + diag, cx + diag, cy - diag, argb);
    }

    /** Draws a 5-petal sakura blossom centered at (cx, cy). */
    public static void drawSakuraFlower(Painter p, float cx, float cy, float size, float rotAngle, int petalArgb, int pistilArgb) {
        if (size <= 0.0f) return;
        for (int i = 0; i < 5; i++) {
            double a = rotAngle + i * (Math.PI * 2.0 / 5.0);
            float px = cx + (float) Math.cos(a) * size * 0.75f;
            float py = cy + (float) Math.sin(a) * size * 0.75f;
            softDisc(p, px, py, size * 0.5f,
                    (petalArgb >> 16) & 0xFF,
                    (petalArgb >> 8) & 0xFF,
                    petalArgb & 0xFF,
                    ((petalArgb >>> 24) & 0xFF) / 255.0f);
        }
        softDisc(p, cx, cy, Math.max(0.8f, size * 0.35f),
                (pistilArgb >> 16) & 0xFF,
                (pistilArgb >> 8) & 0xFF,
                pistilArgb & 0xFF,
                ((pistilArgb >>> 24) & 0xFF) / 255.0f);
    }

    /** Draws an individual petal centered at (cx, cy). */
    public static void drawSakuraPetal(Painter p, float cx, float cy, float size, float angle, int argb) {
        if (size <= 0.0f) return;
        float dx = (float) Math.cos(angle) * size;
        float dy = (float) Math.sin(angle) * size;
        p.line(cx - dx, cy - dy, cx + dx, cy + dy, argb);
        softDisc(p, cx, cy, size * 0.6f,
                (argb >> 16) & 0xFF,
                (argb >> 8) & 0xFF,
                argb & 0xFF,
                ((argb >>> 24) & 0xFF) / 255.0f);
    }
}
