package com.simonconrad.fireballpredictor.config;

import com.google.gson.annotations.SerializedName;
import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Special render themes that dynamically override standard color, style, and animation
 * settings for the trajectory ribbon and shockwave blast dome.
 *
 * <p>All color math and pattern calculations are fully zero-allocation, operating on
 * bit-packed 32-bit ARGB/RGB integers and precomputed lookup tables (LUTs).
 */
public enum VisualTheme implements NameableEnum {
    @SerializedName("default")
    DEFAULT("default"),
    @SerializedName("rainbow")
    RAINBOW("rainbow"),
    @SerializedName("cyberpunk")
    CYBERPUNK("cyberpunk"),
    @SerializedName("matrix")
    MATRIX("matrix"),
    @SerializedName("inferno")
    INFERNO("inferno"),
    @SerializedName("heatmap")
    HEATMAP("heatmap"),
    @SerializedName("celestial")
    CELESTIAL("celestial"),
    @SerializedName("ghost")
    GHOST("ghost"),
    @SerializedName("sculk_void")
    SCULK_VOID("sculk_void"),
    @SerializedName("electric_arc")
    ELECTRIC_ARC("electric_arc"),
    @SerializedName("tactical_hud")
    TACTICAL_HUD("tactical_hud"),
    @SerializedName("aurora")
    AURORA("aurora"),
    @SerializedName("singularity")
    SINGULARITY("singularity"),
    @SerializedName("sakura")
    SAKURA("sakura"),
    @SerializedName("crystal")
    CRYSTAL("crystal"),
    @SerializedName("arcade")
    ARCADE("arcade");

    private final String key;

    VisualTheme(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("yacl3.config.fireballpredictor:config.visualTheme." + key);
    }

    public boolean isCustomTheme() {
        return this != DEFAULT;
    }

    // =========================================================================
    // Precomputed Lookup Tables (LUTs) for Zero-Allocation Math
    // =========================================================================

    private static final int LUT_SIZE = 256;
    private static final int[] HUE_LUT = new int[LUT_SIZE];
    private static final float[] SINE_LUT = new float[LUT_SIZE];

    static {
        for (int i = 0; i < LUT_SIZE; i++) {
            float hue = (float) i / LUT_SIZE;
            HUE_LUT[i] = computeHsvRgb(hue, 1.0f, 1.0f);
            SINE_LUT[i] = (float) Math.sin(hue * 2.0 * Math.PI);
        }
    }

    /**
     * Fast sine lookup from 0.0 to 1.0 phase cycle.
     */
    public static float fastSin(float phase) {
        int idx = ((int) (phase * LUT_SIZE)) & (LUT_SIZE - 1);
        return SINE_LUT[idx];
    }

    /**
     * Fast hue lookup from 0.0 to 1.0.
     */
    public static int sampleHue(float hue) {
        int idx = ((int) (hue * LUT_SIZE)) & (LUT_SIZE - 1);
        return HUE_LUT[idx];
    }

    /**
     * Dome breathing pulse at 0.5 Hz (period 2 s) driven by animSeconds.
     */
    public static float computePulseFactor(double animSeconds) {
        if (animSeconds <= 0.0) return 1.0f;
        double angle = animSeconds * Math.PI; // 2π per 2 s
        return 0.8f + 0.2f * (float) Math.sin(angle);
    }

    // =========================================================================
    // Ribbon Trajectory Color Evaluation (Packed RGB: 0xRRGGBB)
    // =========================================================================

    /**
     * Evaluates the packed RGB color (0xRRGGBB) for a trajectory ribbon segment.
     *
     * @param progress     normalized flight progress (0.0 = source, 1.0 = impact)
     * @param time         continuous elapsed time in seconds
     * @param segmentIndex ordinal index along the trajectory path
     * @param isCore       true if rendering the inner bright core, false for outer shroud
     * @param fallbackRgb  standard user-configured color for this projectile type
     */
    public int getRibbonColorPacked(float progress, double time, int segmentIndex, boolean isCore, int fallbackRgb) {
        if (this == DEFAULT) {
            // Preserve the pre-theme trail core brightening exactly (0.35 white mix), so DEFAULT
            // renders pixel-identical to the old renderer.
            return isCore ? lightenRgb(fallbackRgb, 0.35f) : fallbackRgb;
        }

        return switch (this) {
            case RAINBOW -> {
                // Flowing HSV rainbow wave along the trajectory
                float hue = (float) (time * 0.35 - progress * 1.0);
                int rgb = sampleHue(hue);
                yield isCore ? lightenRgb(rgb, 0.45f) : rgb;
            }
            case CYBERPUNK -> {
                // Electric Neon Cyan (#00F0FF) to Hot Magenta (#FF007F)
                int cyan = 0x00F0FF;
                int magenta = 0xFF007F;
                int base = lerpRgb(cyan, magenta, progress);
                yield isCore ? lightenRgb(base, 0.55f) : base;
            }
            case MATRIX -> {
                // Fast cascading digital matrix packets
                int pulse = ((int) (time * 18.0)) % 10;
                boolean isGlyph = (segmentIndex % 4) == (pulse % 4);
                int base = isGlyph ? 0x00FF41 : 0x059669;
                yield isCore ? (isGlyph ? 0xFFFFFF : 0xA7F3D0) : base;
            }
            case INFERNO -> {
                // Volcanic firestorm: incandescent solar gold -> blazing flame orange -> deep magma crimson
                float magmaWave = fastSin((float) (time * 2.0 - progress * 5.0));
                int col = lerp3Rgb(0xF59E0B, 0xEA580C, 0xDC2626, 0.40f, progress);
                if (magmaWave > 0.3f) {
                    col = lerpRgb(col, 0xFACC15, (magmaWave - 0.3f) * 0.8f);
                }
                yield isCore ? lerpRgb(col, 0xFEF08A, 0.50f) : col;
            }
            case HEATMAP -> {
                // FLIR false-color thermal ramp
                int col;
                if (progress < 0.25f) {
                    col = lerpRgb(0x0011FF, 0x00D4FF, progress * 4.0f);
                } else if (progress < 0.50f) {
                    col = lerpRgb(0x00D4FF, 0x00FF44, (progress - 0.25f) * 4.0f);
                } else if (progress < 0.75f) {
                    col = lerpRgb(0x00FF44, 0xFFEE00, (progress - 0.50f) * 4.0f);
                } else {
                    col = lerpRgb(0xFFEE00, 0xFF0000, (progress - 0.75f) * 4.0f);
                }
                yield isCore ? lightenRgb(col, 0.50f) : col;
            }
            case CELESTIAL -> {
                // Cosmic Nebula gradient (Galactic Violet -> Nebula Teal -> Starlight Lilac)
                int col = lerp3Rgb(0x6D28D9, 0x06B6D4, 0xC084FC, progress);
                yield isCore ? 0xF3E8FF : col;
            }
            case GHOST -> {
                // Spectral Soul-Fire: turquoise with flowing spirit pulses
                float spiritPulse = fastSin((float) (time * 2.0 - progress * 5.0));
                int deepTeal = 0x0D9488;
                int brightTurquoise = 0x2DD4BF;
                int ghostWhite = 0x99F6E4;
                int base = lerpRgb(deepTeal, brightTurquoise, progress);
                if (spiritPulse > 0.3f) {
                    base = lerpRgb(base, ghostWhite, (spiritPulse - 0.3f) * 0.8f);
                }
                yield isCore ? 0xCCFBF1 : base;
            }
            case SCULK_VOID -> {
                // Abyssal deep void with Warden soul-echo pulses
                float pulse = (float) Math.pow(Math.max(0.0f, Math.sin(time * -3.0 + progress * 8.0)), 3.0);
                int baseTeal = 0x06B6D4;
                int soulEcho = 0x00F5D4;
                if (isCore) {
                    yield pulse > 0.05f ? lerpRgb(0x0D9488, 0x5EEAD4, pulse) : 0x0F766E;
                } else {
                    yield lerpRgb(baseTeal, soulEcho, pulse);
                }
            }
            case ELECTRIC_ARC -> {
                // High-voltage plasma arc with searing white core and crackling electric cyan/cobalt discharge flashes
                int step = (int) (time * 16.0);
                boolean isFlash = ((segmentIndex * 13 + step * 7) % 7) == 0;
                int base = isFlash ? 0xBAE6FD : (((segmentIndex + step) % 3 == 0) ? 0x00E5FF : 0x0284C7);
                yield isCore ? (isFlash ? 0xFFFFFF : 0xF0F9FF) : base;
            }
            case TACTICAL_HUD -> {
                // Collimated Aviation Amber-Gold (#F59E0B) with range notches
                int col = (segmentIndex % 4 == 0) ? 0xFBBF24 : 0xD97706;
                yield isCore ? 0xFEF3C7 : col;
            }
            case AURORA -> {
                // Polar Aurora Borealis: emerald green -> glacial cyan -> polar violet
                float wave = fastSin((float) (time * 0.9 - progress * 3.0));
                int col = lerp3Rgb(0x00FF87, 0x60EFFF, 0xA855F7, progress);
                if (wave > 0.2f) {
                    col = lerpRgb(col, 0xE0F2FE, (wave - 0.2f) * 0.7f);
                }
                yield isCore ? 0xF0FDFA : col;
            }
            case SINGULARITY -> {
                // Gravitational Singularity: luminous accretion orange to cosmic ultraviolet
                float wave = fastSin((float) (time * 2.5 + progress * 6.0));
                int shroud = lerp3Rgb(0xFF6A00, 0xC026D3, 0x4338CA, 0.40f, progress);
                if (wave > 0.40f) {
                    shroud = lerpRgb(shroud, 0xF97316, (wave - 0.40f) * 0.8f);
                }
                // Core: Dark obsidian singularity void tunnel running down the trajectory center
                int coreVoid = 0x05010B;
                yield isCore ? coreVoid : shroud;
            }
            case SAKURA -> {
                // Sakura Drift: pastel blossom pink -> rose quartz -> coral -> ivory
                float wave = fastSin((float) (time * 1.2 - progress * 4.0));
                int col = lerp3Rgb(0xFFB7C5, 0xF472B6, 0xFB7185, progress);
                if (wave > 0.3f) {
                    col = lerpRgb(col, 0xFFF1F2, (wave - 0.3f) * 0.6f);
                }
                yield isCore ? 0xFFF1F2 : col;
            }
            case CRYSTAL -> {
                // Prismatic Crystal: deep amethyst -> quartz lilac -> emerald glints
                float sparkle = fastSin((float) (time * 3.5 + segmentIndex * 0.45));
                int col = lerpRgb(0x7E22CE, 0xC084FC, progress);
                if (sparkle > 0.4f) {
                    col = lerpRgb(col, 0x34D399, (sparkle - 0.4f) * 0.8f);
                }
                yield isCore ? (sparkle > 0.2f ? 0xFFFFFF : 0xE9D5FF) : col;
            }
            case ARCADE -> {
                // 8-Bit Arcade: stepped quantized pixel palette
                int step = (segmentIndex / 3 + (int) (time * 8.0)) % 4;
                int col = switch (step) {
                    case 0 -> 0x00F0FF; // Arcade Cyan
                    case 1 -> 0xFF0055; // Pixel Magenta
                    case 2 -> 0x39FF14; // Phosphor Green
                    default -> 0xFFE600; // Coin Gold
                };
                yield isCore ? (step == 0 ? 0xFFFFFF : 0xFFFB96) : col;
            }
            default -> fallbackRgb;
        };
    }

    // =========================================================================
    // Dome Blast Sphere Color Evaluation (Packed RGB: 0xRRGGBB)
    // =========================================================================

    /**
     * Evaluates the packed RGB color (0xRRGGBB) for a shockwave dome vertex or quad.
     *
     * @param vertex       vertex position relative to dome centre
     * @param cameraLocal  camera position relative to dome centre
     * @param latProgress  normalized latitude progress from ground (0.0) to apex (1.0)
     * @param lonProgress  normalized longitude progress around the azimuth (0.0 to 1.0)
     * @param time         continuous elapsed time in seconds
     * @param fallbackRgb  standard user-configured shockwave color
     */
    public int getDomeColorPacked(Vec3 vertex, Vec3 cameraLocal, float latProgress, float lonProgress, double time, int fallbackRgb) {
        if (this == DEFAULT) {
            return fallbackRgb;
        }

        return switch (this) {
            case RAINBOW -> {
                // Chromatic latitude + time wave: both front and back hemisphere faces share the same
                // hue at equal heights, eliminating complementary-color cancellation and giving vivid saturated color.
                float hue = (float) (time * 0.35 + latProgress * 1.25f);
                yield sampleHue(hue);
            }
            case CYBERPUNK -> {
                // Sweeping Outrun synthwave gradient
                float wave = Mth.clamp((float) (latProgress + 0.15f * Math.sin(time * 0.8 + lonProgress * 2.0 * Math.PI)), 0.0f, 1.0f);
                yield lerpRgb(0x00F0FF, 0xFF007F, wave);
            }
            case MATRIX -> {
                // Falling Matrix code rain: bright white-green heads and terminal green glyphs
                int col = (int) (lonProgress * 24.0f);
                float speed = 1.6f + ((col * 17 + 3) % 5) * 0.4f;
                float offset = ((col * 31 + 11) % 100) / 100.0f;
                float streamY = (float) ((latProgress * 2.5f + time * speed + offset) % 1.0);
                if (streamY < 0.18f) {
                    yield 0xE6FFE6; // Head: bright white-green phosphor
                } else if (streamY < 0.55f) {
                    yield 0x00FF41; // Body: terminal green digital glyph
                } else {
                    yield 0x059669; // Fading digital trace
                }
            }
            case INFERNO -> {
                // Volcanic magma convection dome with rolling lava flows and fissure veins
                float magmaFlow = fastSin((float) (latProgress * 8.0 - time * 1.8 + lonProgress * 4.0)) * 0.5f + 0.5f;
                float fissure = (float) Math.pow(Math.max(0.0, Math.sin(latProgress * 14.0 + lonProgress * 6.0 + time * 1.2)), 4.0);
                int basalt = lerpRgb(0x450A0A, 0xDC2626, latProgress);
                int lava = lerpRgb(0xFF5500, 0xFFEA00, magmaFlow);
                int blended = lerpRgb(basalt, lava, 0.45f + 0.35f * magmaFlow);
                if (fissure > 0.20f) {
                    blended = lerpRgb(blended, 0xFFF5A0, fissure);
                }
                yield blended;
            }
            case HEATMAP -> {
                // Concentric thermal temperature zones (Searing Red -> Amber Yellow -> Lime Green -> Cyan)
                if (latProgress < 0.33f) {
                    yield lerpRgb(0xFF2200, 0xFACC15, latProgress * 3.0f);
                } else if (latProgress < 0.66f) {
                    yield lerpRgb(0xFACC15, 0x22C55E, (latProgress - 0.33f) * 3.0f);
                } else {
                    yield lerpRgb(0x22C55E, 0x06B6D4, (latProgress - 0.66f) * 3.0f);
                }
            }
            case CELESTIAL -> {
                // Luminous Cosmic Nebula: Galactic Violet -> Cosmic Nebula Teal -> Starlight Lilac
                float wave = Mth.clamp((float) (latProgress + 0.20f * Math.sin(time * 0.5 + lonProgress * 2.0 * Math.PI)), 0.0f, 1.0f);
                yield lerp3Rgb(0x6D28D9, 0x06B6D4, 0xC084FC, wave);
            }
            case GHOST -> {
                // Spectral Soul Shield: swirling spiritual aurora vortex
                float vortex = 0.5f + 0.5f * fastSin((float) (time * 0.9 + latProgress * 3.5 + lonProgress * 2.0 * Math.PI));
                int deepSoul = 0x0F766E;
                int brightSoul = 0x2DD4BF;
                int phantomWhite = 0xCCFBF1;
                yield vortex < 0.60f ? lerpRgb(deepSoul, brightSoul, vortex / 0.60f) : lerpRgb(brightSoul, phantomWhite, (vortex - 0.60f) / 0.40f);
            }
            case SCULK_VOID -> {
                // Concentric sonic boom shockwave rings
                float ring = (float) Math.pow(Math.max(0.0f, Math.sin(latProgress * 12.0 - time * 3.0)), 4.0);
                yield ring > 0.15f ? lerpRgb(0x0891B2, 0x00F5D4, ring) : 0x0E7490;
            }
            case ELECTRIC_ARC -> {
                // High-voltage ionized plasma field with crackling fractal lightning channels
                double vx = (vertex != null) ? vertex.x : (Math.cos(lonProgress * 2.0 * Math.PI) * Math.cos(latProgress * 0.5 * Math.PI) * 3.0);
                double vy = (vertex != null) ? vertex.y : (Math.sin(latProgress * 0.5 * Math.PI) * 3.0);
                double vz = (vertex != null) ? vertex.z : (Math.sin(lonProgress * 2.0 * Math.PI) * Math.cos(latProgress * 0.5 * Math.PI) * 3.0);

                float p1 = fastSin((float) (vx * 0.40 + vz * 0.35 + time * 2.2));
                float p2 = fastSin((float) (vy * 0.48 - vx * 0.30 - time * 3.1));
                float p3 = fastSin((float) (vz * 0.45 + vy * 0.38 + time * 1.8));

                // Dielectric breakdown streamers: narrow high-energy plasma arcs across the spherical field
                float discharge = Math.abs(p1 * p2 - p3 * 0.45f);
                if (discharge < 0.07f) {
                    yield 0xFFFFFF; // Core ultra-bright lightning streamer
                } else if (discharge < 0.18f) {
                    float tArc = (discharge - 0.07f) / 0.11f;
                    yield lerpRgb(0xE0F2FE, 0x38BDF8, tArc); // High-voltage ionized cyan streamer corona
                } else {
                    // Ionized ambient plasma convection field
                    float field = 0.5f + 0.5f * (p1 * 0.5f + p2 * 0.3f + p3 * 0.2f);
                    yield lerpRgb(0x0284C7, 0x00E5FF, field); // Deep electric blue to neon cyan plasma
                }
            }
            case TACTICAL_HUD -> {
                // High-contrast aviation radar sweep beam & glowing phosphor trail
                double angle = lonProgress * 2.0 * Math.PI;
                double sweep = (time * 3.0) % (2.0 * Math.PI);
                double delta = (angle - sweep) % (2.0 * Math.PI);
                if (delta < 0) delta += 2.0 * Math.PI;

                if (delta < 0.08) {
                    yield 0xFFFFFF; // Beam core: searing phosphor white
                } else if (delta < 0.20) {
                    yield 0xFEF08A; // Searing bright yellow-amber
                } else if (delta < 0.85) {
                    float fadeFactor = (float) ((0.85 - delta) / 0.65);
                    yield lerpRgb(0xD97706, 0xF59E0B, fadeFactor); // Glowing phosphor wake
                } else {
                    // Radar grid lines: concentric altitude rings & crosshairs
                    boolean ring = Math.abs(latProgress - 0.33f) < 0.03f || Math.abs(latProgress - 0.66f) < 0.03f || latProgress > 0.95f;
                    boolean spoke = (int) (lonProgress * 12.0f) % 3 == 0;
                    yield (ring || spoke) ? 0xB45309 : 0x78350F;
                }
            }
            case AURORA -> {
                // Polar Aurora Borealis: vertical emerald -> cyan curtains with polar violet apex crown
                float curtain = fastSin((float) (latProgress * 5.0 - time * 1.5 + lonProgress * 4.0));
                int base = lerpRgb(0x00FF87, 0x60EFFF, latProgress);
                if (curtain > 0.1f) {
                    base = lerpRgb(base, 0xA855F7, (curtain - 0.1f) * 0.7f);
                }
                if (latProgress > 0.85f) {
                    base = lerpRgb(base, 0xE0F2FE, (latProgress - 0.85f) * 5.0f);
                }
                yield base;
            }
            case SINGULARITY -> {
                // Gravitational Singularity: rotating plasma accretion disk, black hole shadow base & luminous deep ultraviolet event horizon
                if (latProgress < 0.08f) {
                    // Deep Event Horizon black hole shadow transitioning into photon ring
                    float shadowT = latProgress / 0.08f;
                    yield lerpRgb(0x04010A, 0xFF6A00, shadowT);
                } else if (latProgress < 0.30f) {
                    float diskSpin = (float) (lonProgress * 2.0 * Math.PI - time * 3.5);
                    float diskWave = 0.5f + 0.5f * fastSin(diskSpin);
                    yield lerpRgb(0xFF4500, 0xFB923C, diskWave); // Glowing rotating accretion disk: fiery orange-red to bright solar amber
                } else if (latProgress > 0.82f) {
                    float apexBlend = (latProgress - 0.82f) / 0.18f;
                    yield lerpRgb(0x818CF8, 0xFFFBEB, apexBlend); // Relativistic polar jet core
                } else {
                    float horizon = (latProgress - 0.30f) / 0.52f;
                    // Smooth transition from hot magenta/violet into deep luminous indigo
                    yield lerpRgb(0x9333EA, 0x312E81, horizon);
                }
            }
            case SAKURA -> {
                // Sakura Drift: swirling floral vortex rings
                float vortex = fastSin((float) (latProgress * 6.0 + lonProgress * 6.0 * Math.PI + time * 1.5));
                int base = lerpRgb(0xFFB7C5, 0xF472B6, latProgress);
                if (vortex > 0.25f) {
                    base = lerpRgb(base, 0xFFF1F2, (vortex - 0.25f) * 0.8f);
                }
                yield base;
            }
            case CRYSTAL -> {
                // Prismatic Crystal: faceted gemstone geode
                int facetX = (int) (lonProgress * 16.0f);
                int facetY = (int) (latProgress * 8.0f);
                boolean isFacetGlint = ((facetX * 7 + facetY * 13 + (int) (time * 4.0)) % 5) == 0;
                int base = lerpRgb(0x7E22CE, 0xC084FC, latProgress);
                if (isFacetGlint) {
                    base = lerpRgb(base, 0xFFFFFF, 0.85f);
                }
                yield base;
            }
            case ARCADE -> {
                // 8-Bit Arcade: retro CRT scanlines & pixel matrix
                int scanline = (int) (latProgress * 24.0f);
                boolean isScan = (scanline % 2) == 0;
                int col = isScan ? 0x00F0FF : 0xFF0055;
                if (latProgress > 0.85f) {
                    col = 0xFFE600;
                }
                yield col;
            }
            default -> fallbackRgb;
        };
    }

    // =========================================================================
    // Alpha Modulation & Special Dynamics
    // =========================================================================

    /**
     * Multiplier applied to ribbon alpha to produce unique theme dynamics (e.g. stippling, scanlines, flickering).
     */
    public float getRibbonAlphaModulation(float progress, double time, int segmentIndex) {
        return switch (this) {
            case RAINBOW -> 1.40f; // Boost saturation and density
            case MATRIX -> {
                int pulse = ((int) (time * 18.0)) % 10;
                yield ((segmentIndex % 4) == (pulse % 4)) ? 1.40f : 0.90f;
            }
            case INFERNO -> 1.30f + 0.20f * fastSin((float) (time * 1.6 - progress * 3.5f));
            case CELESTIAL -> 0.90f + 0.10f * fastSin((float) (time * 0.6 + segmentIndex * 0.12f));
            case GHOST -> 0.85f + 0.25f * fastSin((float) (time * 1.2 - progress * 3.0f));
            case SCULK_VOID -> {
                float pulse = (float) Math.pow(Math.max(0.0f, Math.sin(time * -3.0 + progress * 8.0)), 3.0);
                yield 0.10f + 1.20f * pulse;
            }
            case ELECTRIC_ARC -> {
                int step = (int) (time * 12.0);
                int seed = (segmentIndex * 19 + step * 7) % 5;
                yield (seed == 0) ? 1.25f : ((seed == 1) ? 0.85f : 1.0f);
            }
            case TACTICAL_HUD -> 1.25f;
            case AURORA -> 1.05f + 0.30f * fastSin((float) (time * 1.4 + progress * 3.0f));
            case SINGULARITY -> 0.90f + 0.30f * fastSin((float) (time * 3.5 - progress * 8.0f));
            case SAKURA -> 0.85f + 0.25f * fastSin((float) (time * 1.5 - progress * 2.5f));
            case CRYSTAL -> 1.15f + 0.25f * fastSin((float) (time * 4.0 + segmentIndex * 0.35f));
            case ARCADE -> {
                int step = (segmentIndex + (int) (time * 12.0)) % 4;
                yield (step < 3) ? 1.30f : 0.60f;
            }
            default -> 1.0f;
        };
    }

    /**
     * Multiplier applied to dome quad alpha to produce unique theme dynamics (e.g. dither dot matrices, falling code rain).
     */
    public float getDomeAlphaModulation(float latProgress, float lonProgress, double time) {
        return switch (this) {
            case RAINBOW -> 1.40f; // Vivid, saturated rainbow dome
            case MATRIX -> {
                int col = (int) (lonProgress * 24.0f);
                float speed = 1.6f + ((col * 17 + 3) % 5) * 0.4f;
                float offset = ((col * 31 + 11) % 100) / 100.0f;
                float streamY = (float) ((latProgress * 2.5f + time * speed + offset) % 1.0);
                yield (streamY < 0.55f) ? 1.30f : 0.25f;
            }
            case INFERNO -> {
                float magmaFlow = fastSin((float) (latProgress * 8.0 - time * 1.8 + lonProgress * 4.0)) * 0.5f + 0.5f;
                yield 1.15f + 0.35f * magmaFlow;
            }
            case CELESTIAL -> {
                // Translucent cosmic nebula veil: delicate opacity so terrain and stars are cleanly visible under shaders
                yield 0.60f + 0.20f * fastSin((float) (time * 0.5 + latProgress * 2.0f + lonProgress * 3.0f));
            }
            case GHOST -> {
                // Swirling ghostly wisp alpha
                yield 0.75f + 0.35f * fastSin((float) (time * 0.8 + latProgress * 2.5));
            }
            case SCULK_VOID -> {
                float ring = (float) Math.pow(Math.max(0.0f, Math.sin(latProgress * 12.0 - time * 3.0)), 4.0);
                yield 0.35f + 0.95f * ring;
            }
            case ELECTRIC_ARC -> {
                // High-frequency erratic dielectric jitter and discharge spikes
                float jitter = fastSin((float) (latProgress * 2.0 + lonProgress * 3.0 + time * 6.5));
                float dischargeSpike = fastSin((float) (time * 4.2));
                yield 0.70f + 0.35f * Math.max(0.0f, jitter) + (dischargeSpike > 0.75f ? 0.35f : 0.0f);
            }
            case TACTICAL_HUD -> {
                double angle = lonProgress * 2.0 * Math.PI;
                double sweep = (time * 3.0) % (2.0 * Math.PI);
                double delta = (angle - sweep) % (2.0 * Math.PI);
                if (delta < 0) delta += 2.0 * Math.PI;
                if (delta < 0.08) yield 1.70f;
                if (delta < 0.20) yield 1.45f;
                if (delta < 0.85) yield 0.80f + 0.40f * (float) ((0.85 - delta) / 0.65);
                yield 0.45f;
            }
            case AURORA -> 0.75f + 0.45f * Math.max(0.0f, fastSin((float) (latProgress * 5.0 + lonProgress * 3.0 - time * 1.5)));
            case SINGULARITY -> (latProgress < 0.30f) ? 1.50f : (latProgress > 0.82f ? 1.40f : 0.90f);
            case SAKURA -> 0.75f + 0.30f * fastSin((float) (time * 0.8 + latProgress * 2.0f));
            case CRYSTAL -> 0.80f + 0.35f * (((int) (latProgress * 8.0f) + (int) (lonProgress * 16.0f)) % 2 == 0 ? 1.0f : 0.3f);
            case ARCADE -> ((int) (latProgress * 24.0f) % 2 == 0) ? 1.30f : 0.50f;
            default -> 1.0f;
        };
    }

    // =========================================================================
    // Fast Primitive Bit Math Utilities
    // =========================================================================

    public static int packRgb(int r, int g, int b) {
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int extractR(int rgb) {
        return (rgb >> 16) & 0xFF;
    }

    public static int extractG(int rgb) {
        return (rgb >> 8) & 0xFF;
    }

    public static int extractB(int rgb) {
        return rgb & 0xFF;
    }

    public static int lerpRgb(int rgb1, int rgb2, float t) {
        float f = Mth.clamp(t, 0.0f, 1.0f);
        int r1 = (rgb1 >> 16) & 0xFF;
        int g1 = (rgb1 >> 8) & 0xFF;
        int b1 = rgb1 & 0xFF;

        int r2 = (rgb2 >> 16) & 0xFF;
        int g2 = (rgb2 >> 8) & 0xFF;
        int b2 = rgb2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * f);
        int g = (int) (g1 + (g2 - g1) * f);
        int b = (int) (b1 + (b2 - b1) * f);

        return (r << 16) | (g << 8) | b;
    }

    public static int lerp3Rgb(int rgb1, int rgb2, int rgb3, float t) {
        return lerp3Rgb(rgb1, rgb2, rgb3, 0.5f, t);
    }

    public static int lerp3Rgb(int rgb1, int rgb2, int rgb3, float split, float t) {
        float f = Mth.clamp(t, 0.0f, 1.0f);
        if (f < split) {
            return lerpRgb(rgb1, rgb2, split > 0.0f ? f / split : 0.0f);
        } else {
            float rem = 1.0f - split;
            return lerpRgb(rgb2, rgb3, rem > 0.0f ? (f - split) / rem : 1.0f);
        }
    }

    public static int packArgb(int rgb, int a) {
        return ((Mth.clamp(a, 0, 255)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lightenRgb(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        r = Math.min(255, r + (int) ((255 - r) * amount));
        g = Math.min(255, g + (int) ((255 - g) * amount));
        b = Math.min(255, b + (int) ((255 - b) * amount));

        return (r << 16) | (g << 8) | b;
    }

    private static int computeHsvRgb(float h, float s, float v) {
        float r = 0, g = 0, b = 0;
        float i = (float) Math.floor(h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);

        switch ((int) i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            case 5 -> { r = v; g = p; b = q; }
        }

        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
