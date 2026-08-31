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
    /** Width in blocks; master's default is 0.5 (the old backport default of 0.12 looked thin). */
    public static float trajectoryWidth = 0.5F;
    public static int trajectoryColor = 0xFFFF8000; // orange
    public static TrajectoryStyle trajectoryStyle = TrajectoryStyle.SOLID;
    /** Extra bright core layer on top of the soft outer shroud (master's renderCoreGlow). */
    public static boolean renderCoreGlow = true;
    /** Subtle travelling brightness wave along the ribbon (master's enableRibbonPulse). */
    public static boolean enableRibbonPulse = true;

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

    // ---- owner-based tracking filters (simplified port of master's tracking section)
    /** Track projectiles fired by hostile mobs (ghast, blaze, wither). */
    public static boolean trackMobProjectiles = true;
    /** Master for the non-mob source group (player, dispenser, command). */
    public static boolean trackOtherOwnerProjectiles = true;
    /** Track projectiles fired (or deflected) by players. */
    public static boolean trackPlayerProjectiles = true;
    /** Track dispenser-fired projectiles. */
    public static boolean trackDispenserProjectiles = true;
    /** Track command-summoned / unmatched projectiles. */
    public static boolean trackCommandProjectiles = true;

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        try {
            cfg.load();

            masterEnabled = cfg.getBoolean("masterEnabled", Configuration.CATEGORY_GENERAL, masterEnabled,
                    "Master switch for the whole mod.");

            renderTrajectory = cfg.getBoolean("renderTrajectory", "trajectory", renderTrajectory,
                    "Render the predicted flight path ribbon.");
            trajectoryWidth = cfg.getFloat("trajectoryWidth", "trajectory", trajectoryWidth, 0.1F, 2.0F,
                    "Width of the trajectory ribbon in blocks (master default: 0.5).");
            // One-time migration: installations still on the old 0.12 default are moved to
            // master's 0.5 default so existing users see the improved ribbon.
            if (trajectoryWidth == 0.12F) {
                trajectoryWidth = 0.5F;
            }
            trajectoryColor = parseColor(cfg.getString("trajectoryColor", "trajectory", "FF8000",
                    "Trajectory ribbon color as RRGGBB hex."));
            trajectoryStyle = TrajectoryStyle.byName(
                    cfg.getString("trajectoryStyle", "trajectory", trajectoryStyle.getKey(),
                            "Trajectory ribbon style: solid | dashed | core_only."));
            renderCoreGlow = cfg.getBoolean("renderCoreGlow", "trajectory", renderCoreGlow,
                    "Draw the extra bright core layer on top of the soft outer shroud.");
            enableRibbonPulse = cfg.getBoolean("enableRibbonPulse", "trajectory", enableRibbonPulse,
                    "Animate a subtle travelling brightness wave along the ribbon.");

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

            trackMobProjectiles = cfg.getBoolean("trackMobProjectiles", "tracking", trackMobProjectiles,
                    "Track projectiles fired by hostile mobs (ghast, blaze, wither).");
            trackOtherOwnerProjectiles = cfg.getBoolean("trackOtherOwnerProjectiles", "tracking", trackOtherOwnerProjectiles,
                    "Master for the non-mob source group (player, dispenser, command).");
            trackPlayerProjectiles = cfg.getBoolean("trackPlayerProjectiles", "tracking", trackPlayerProjectiles,
                    "Track projectiles fired (or deflected) by players.");
            trackDispenserProjectiles = cfg.getBoolean("trackDispenserProjectiles", "tracking", trackDispenserProjectiles,
                    "Track dispenser-fired projectiles.");
            trackCommandProjectiles = cfg.getBoolean("trackCommandProjectiles", "tracking", trackCommandProjectiles,
                    "Track command-summoned / unmatched projectiles.");
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
