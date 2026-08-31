package com.simonconrad.fireballpredictor.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Minimal client configuration for the 1.8.9 backport.
 * (The original 26.2 mod's themes/config GUI are intentionally not ported.)
 */
public final class ModConfig {

    private ModConfig() {
    }

    // ---- master switch -----------------------------------------------------
    public static boolean masterEnabled = true;

    // ---- trajectory ribbon -------------------------------------------------
    public static boolean renderTrajectory = true;
    public static float trajectoryWidth = 0.12F;
    public static int trajectoryColor = 0xFFFF8000; // orange

    // ---- shockwave dome ----------------------------------------------------
    public static boolean renderShockwaveDome = true;
    public static int domeColor = 0xFFFF8000;
    /**
     * Strength of the Schlick fresnel rim shading (0 = legacy latitude profile,
     * 1 = full fresnel). Because the dome is rendered without back-face culling,
     * the rim term also makes the far side of the shell readable when the camera
     * is inside the blast sphere (mirrors master's PredictionFeatureRenderer).
     */
    public static float domeFresnelStrength = 0.3F;

    // ---- block destruction highlight ---------------------------------------
    public static boolean renderBlockHighlights = true;

    // ---- HUD ---------------------------------------------------------------
    public static boolean renderImpactWarning = true;
    public static boolean renderDamageText = true;
    public static boolean renderHeartsOverlay = true;
    public static int badgeOffsetX = 0;
    public static int badgeOffsetY = 0;

    // ---- prediction ---------------------------------------------------------
    /** Upper bound used instead of vanilla's random (0.7 .. 1.3) ray power, matches the original mod. */
    public static float rayPowerMultiplier = 1.3F;
    public static int maxTrackedProjectiles = 16;
    public static int maxTicks = 200;

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();

            masterEnabled = cfg.getBoolean("masterEnabled", Configuration.CATEGORY_GENERAL, masterEnabled,
                    "Master switch for the whole mod.");

            renderTrajectory = cfg.getBoolean("renderTrajectory", "trajectory", renderTrajectory,
                    "Render the predicted flight path ribbon.");
            trajectoryWidth = cfg.getFloat("trajectoryWidth", "trajectory", trajectoryWidth, 0.02F, 1.0F,
                    "Width of the trajectory ribbon in blocks.");
            trajectoryColor = parseColor(cfg.getString("trajectoryColor", "trajectory", "FF8000",
                    "Trajectory ribbon color as RRGGBB hex."));

            renderShockwaveDome = cfg.getBoolean("renderShockwaveDome", "dome", renderShockwaveDome,
                    "Render the shockwave dome at the predicted impact point.");
            domeColor = parseColor(cfg.getString("domeColor", "dome", "FF8000",
                    "Shockwave dome color as RRGGBB hex."));
            domeFresnelStrength = cfg.getFloat("domeFresnelStrength", "dome", domeFresnelStrength, 0.0F, 1.0F,
                    "Strength of the fresnel rim shading (0 = legacy latitude profile, 1 = full fresnel)."
                            + " The rim glow keeps the dome visible from the inside.");

            renderBlockHighlights = cfg.getBoolean("renderBlockHighlights", "blocks", renderBlockHighlights,
                    "Highlight blocks that are predicted to be destroyed.");

            renderImpactWarning = cfg.getBoolean("renderImpactWarning", "hud", renderImpactWarning,
                    "Render the impact warning badge.");
            renderDamageText = cfg.getBoolean("renderDamageText", "hud", renderDamageText,
                    "Render the damage & knockback readout next to the badge.");
            renderHeartsOverlay = cfg.getBoolean("renderHeartsOverlay", "hud", renderHeartsOverlay,
                    "Render the cracking hearts overlay on the health bar.");
            badgeOffsetX = cfg.getInt("badgeOffsetX", "hud", badgeOffsetX, -1000, 1000, "Badge X offset.");
            badgeOffsetY = cfg.getInt("badgeOffsetY", "hud", badgeOffsetY, -1000, 1000, "Badge Y offset.");

            rayPowerMultiplier = cfg.getFloat("rayPowerMultiplier", "prediction", rayPowerMultiplier, 0.7F, 1.3F,
                    "Explosion ray power multiplier (vanilla random is 0.7-1.3; we use the upper bound).");
            maxTrackedProjectiles = cfg.getInt("maxTrackedProjectiles", "prediction", maxTrackedProjectiles, 1, 64,
                    "Maximum number of simultaneously tracked projectiles.");
            maxTicks = cfg.getInt("maxTicks", "prediction", maxTicks, 20, 600,
                    "Maximum number of ticks to simulate ahead.");
        } finally {
            if (cfg.hasChanged()) {
                cfg.save();
            }
        }
    }

    private static int parseColor(String hex) {
        try {
            return 0xFF000000 | Integer.parseInt(hex.trim(), 16);
        } catch (NumberFormatException e) {
            return 0xFFFF8000;
        }
    }
}
