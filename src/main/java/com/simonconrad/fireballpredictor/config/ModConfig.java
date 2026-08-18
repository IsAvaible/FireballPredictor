package com.simonconrad.fireballpredictor.config;

import com.simonconrad.fireballpredictor.client.gui.ModConfigGui;
import com.simonconrad.fireballpredictor.client.gui.preview.ConfigPreviewRenderer;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.ColorField;
import dev.isxander.yacl3.config.v2.api.autogen.CustomImage;
import dev.isxander.yacl3.config.v2.api.autogen.EnumCycler;
import dev.isxander.yacl3.config.v2.api.autogen.FloatField;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.MasterTickBox;
import dev.isxander.yacl3.config.v2.api.autogen.TickBox;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.awt.Color;

public class ModConfig {
    // 1. Create the handler that manages loading, saving, and the instance
    public static final ConfigClassHandler<ModConfig> HANDLER = ConfigClassHandler.createBuilder(ModConfig.class)
            .id(Identifier.fromNamespaceAndPath("fireballpredictor", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("fireballpredictor.json"))
                    .build())
            .build();

    // 2. Define your config values using annotations

    // ---- General / power ----------------------------------------------------

    @SerialEntry
    @AutoGen(category = "general", group = "power")
    @FloatField(min = 0.0f, max = 100.0f)
    public float globalFallbackFireballPower = 1.0F;

    @SerialEntry
    public java.util.Map<String, Float> serverFallbackPowers = new java.util.HashMap<>();

    public Float getServerFallbackPower(String serverIp) {
        if (serverIp == null || serverIp.isEmpty()) {
            return null;
        }
        return serverFallbackPowers.get(serverIp.toLowerCase(java.util.Locale.ROOT));
    }

    public void setServerFallbackPower(String serverIp, Float power) {
        if (serverIp == null || serverIp.isEmpty()) {
            return;
        }
        String key = serverIp.toLowerCase(java.util.Locale.ROOT);
        if (power == null || power <= 0.0f) {
            serverFallbackPowers.remove(key);
        } else {
            serverFallbackPowers.put(key, power);
        }
    }

    @SerialEntry
    @AutoGen(category = "general", group = "power")
    @FloatField(min = 0.7f, max = 1.3f)
    public float rayPowerMultiplier = 1.3F;

    // ---- Owner & Projectile Tracking ----------------------------------------

    /**
     * Master toggle for projectile tracking. Disables all projectile type & owner filters when off.
     */
    @SerialEntry
    @AutoGen(category = "general", group = "tracking")
    @CustomImage(factory = ConfigPreviewRenderer.TrackMasterFactory.class)
    @MasterTickBox({
            "trackFireballs",
            "trackWitherSkulls",
            "trackWindCharges",
            "trackMobProjectiles",
            "trackOtherOwnerProjectiles"
    })
    public boolean trackProjectiles = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking")
    @CustomImage(factory = ConfigPreviewRenderer.TrackFireballFactory.class)
    @TickBox
    public boolean trackFireballs = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking")
    @CustomImage(factory = ConfigPreviewRenderer.TrackWitherFactory.class)
    @TickBox
    public boolean trackWitherSkulls = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking")
    @CustomImage(factory = ConfigPreviewRenderer.TrackWindFactory.class)
    @TickBox
    public boolean trackWindCharges = true;

    /**
     * Master for hostile-mob-sourced projectiles. Children are the per-mob filters.
     */
    @SerialEntry
    @AutoGen(category = "general", group = "tracking_mobs")
    @CustomImage(factory = ConfigPreviewRenderer.TrackMobMasterFactory.class)
    @MasterTickBox({
            "trackBlazeFireballs",
            "trackGhastFireballs",
            "trackEnderDragonFireballs",
            "trackWitherMob"
    })
    public boolean trackMobProjectiles = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking_mobs")
    @CustomImage(factory = ConfigPreviewRenderer.TrackBlazeFactory.class)
    @TickBox
    public boolean trackBlazeFireballs = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking_mobs")
    @CustomImage(factory = ConfigPreviewRenderer.TrackGhastFactory.class)
    @TickBox
    public boolean trackGhastFireballs = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking_mobs")
    @CustomImage(factory = ConfigPreviewRenderer.TrackDragonFactory.class)
    @TickBox
    public boolean trackEnderDragonFireballs = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking_mobs")
    @CustomImage(factory = ConfigPreviewRenderer.TrackWitherMobFactory.class)
    @TickBox
    public boolean trackWitherMob = true;

    /**
     * Master for non-mob source projectiles (player, dispenser, command).
     */
    @SerialEntry
    @AutoGen(category = "general", group = "tracking_other")
    @CustomImage(factory = ConfigPreviewRenderer.TrackOtherMasterFactory.class)
    @MasterTickBox({
            "trackPlayerProjectiles",
            "trackDispenserProjectiles",
            "trackCommandProjectiles"
    })
    public boolean trackOtherOwnerProjectiles = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking_other")
    @CustomImage(factory = ConfigPreviewRenderer.TrackPlayerFactory.class)
    @TickBox
    public boolean trackPlayerProjectiles = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking_other")
    @CustomImage(factory = ConfigPreviewRenderer.TrackDispenserFactory.class)
    @TickBox
    public boolean trackDispenserProjectiles = true;

    @SerialEntry
    @AutoGen(category = "general", group = "tracking_other")
    @CustomImage(factory = ConfigPreviewRenderer.TrackCommandFactory.class)
    @TickBox
    public boolean trackCommandProjectiles = true;

    // ---- Visuals ------------------------------------------------------------

    @SerialEntry
    @AutoGen(category = "visuals", group = "themes")
    @CustomImage(factory = ConfigPreviewRenderer.VisualThemeFactory.class)
    @EnumCycler
    public VisualTheme visualTheme = VisualTheme.DEFAULT;

    @SerialEntry
    @AutoGen(category = "visuals", group = "themes")
    @CustomImage(factory = ConfigPreviewRenderer.VisualThemeFactory.class)
    @FloatField(min = 0.0f, max = 3.0f)
    public float themeAnimationSpeed = 1.0f;

    @SerialEntry
    @AutoGen(category = "visuals", group = "elements")
    @CustomImage(factory = ConfigPreviewRenderer.TrajectoryFactory.class)
    @TickBox
    public boolean renderTrajectory = true;

    @SerialEntry
    @AutoGen(category = "visuals", group = "elements")
    @CustomImage(factory = ConfigPreviewRenderer.ShockwaveFactory.class)
    @TickBox
    public boolean renderShockwaveDome = true;

    @SerialEntry
    @AutoGen(category = "visuals", group = "elements")
    @CustomImage(factory = ConfigPreviewRenderer.ShockwaveFactory.class)
    @TickBox
    public boolean renderBlockHighlights = true;

    @SerialEntry
    @AutoGen(category = "visuals", group = "elements")
    @TickBox
    public boolean renderParticleAccents = true;

    @SerialEntry
    @AutoGen(category = "visuals", group = "trajectory")
    @CustomImage(factory = ConfigPreviewRenderer.TrajectoryFactory.class)
    @ColorField
    public Color trajectoryColor = new Color(255, 128, 0);

    @SerialEntry
    @AutoGen(category = "visuals", group = "trajectory")
    @CustomImage(factory = ConfigPreviewRenderer.TrajectoryWindFactory.class)
    @ColorField
    public Color windChargeTrajectoryColor = new Color(255, 255, 255);

    @SerialEntry
    @AutoGen(category = "visuals", group = "trajectory")
    @CustomImage(factory = ConfigPreviewRenderer.TrajectoryFactory.class)
    @FloatField(min = 0.1f, max = 2.0f)
    public float trajectoryWidth = 0.5f;

    @SerialEntry
    @AutoGen(category = "visuals", group = "trajectory")
    @CustomImage(factory = ConfigPreviewRenderer.TrajectoryFactory.class)
    @EnumCycler
    public TrajectoryStyle trajectoryStyle = TrajectoryStyle.SOLID;

    @SerialEntry
    @AutoGen(category = "visuals", group = "trajectory")
    @CustomImage(factory = ConfigPreviewRenderer.TrajectoryFactory.class)
    @TickBox
    public boolean renderCoreGlow = true;

    @SerialEntry
    @AutoGen(category = "visuals", group = "trajectory")
    @CustomImage(factory = ConfigPreviewRenderer.TrajectoryFactory.class)
    @TickBox
    public boolean enableRibbonPulse = true;

    @SerialEntry
    @AutoGen(category = "visuals", group = "shockwave")
    @CustomImage(factory = ConfigPreviewRenderer.ShockwaveFactory.class)
    @ColorField
    public Color shockwaveColor = new Color(255, 128, 0);

    @SerialEntry
    @AutoGen(category = "visuals", group = "shockwave")
    @CustomImage(factory = ConfigPreviewRenderer.ShockwaveWindFactory.class)
    @ColorField
    public Color windChargeShockwaveColor = new Color(255, 255, 255);

    @SerialEntry
    @AutoGen(category = "visuals", group = "shockwave")
    @CustomImage(factory = ConfigPreviewRenderer.ShockwaveFactory.class)
    @FloatField(min = 0.0f, max = 1.0f)
    public float domeFresnelStrength = 0.3f;

    @SerialEntry
    @AutoGen(category = "visuals", group = "impact_warning")
    @CustomImage(factory = ConfigPreviewRenderer.HudFactory.class)
    @TickBox
    public boolean renderImpactWarning = true;

    @SerialEntry
    @AutoGen(category = "visuals", group = "impact_warning")
    @CustomImage(factory = ConfigPreviewRenderer.HudFactory.class)
    @EnumCycler
    public ImpactWarningBadgeAnchor impactWarningBadgeAnchor = ImpactWarningBadgeAnchor.TOP_LEFT;

    @SerialEntry
    @AutoGen(category = "visuals", group = "impact_warning")
    @CustomImage(factory = ConfigPreviewRenderer.HudFactory.class)
    @IntField(min = -1000, max = 1000, format = "%d")
    public int impactWarningBadgeOffsetX = 0;

    @SerialEntry
    @AutoGen(category = "visuals", group = "impact_warning")
    @CustomImage(factory = ConfigPreviewRenderer.HudFactory.class)
    @IntField(min = -1000, max = 1000, format = "%d")
    public int impactWarningBadgeOffsetY = 0;

    // ---- Damage & Knockback Estimation -------------------------------------

    /**
     * Renders the "cracking fireball hearts" overlay on top of the health bar, highlighting the
     * exact hearts predicted to be lost if a tracked projectile detonates at its impact point.
     */
    @SerialEntry
    @AutoGen(category = "visuals", group = "damage_estimator")
    @CustomImage(factory = ConfigPreviewRenderer.DamageHeartsFactory.class)
    @TickBox
    public boolean renderDamageHeartsOverlay = true;

    /**
     * Shows the predicted damage (hearts) and knockback speed (blocks/second) readout next to the
     * impact warning badge for the most threatening incoming projectile.
     */
    @SerialEntry
    @AutoGen(category = "visuals", group = "damage_estimator")
    @CustomImage(factory = ConfigPreviewRenderer.KnockbackEstimatorFactory.class)
    @TickBox
    public boolean showKnockbackEstimator = true;

    // 3. Helper methods to match your existing client initialization calls
    public static Screen createScreen(Screen parentScreen) {
        return ModConfigGui.createScreen(parentScreen);
    }

    public static void load() {
        HANDLER.load();
    }

    public static void save() {
        HANDLER.save();
    }

    public static ModConfig instance() {
        return HANDLER.instance();
    }
}
