package com.simonconrad.fireballpredictor.config;

import com.simonconrad.fireballpredictor.client.gui.preview.ConfigPreviewRenderer;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRules;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

public class ModConfig {
    // 1. Create the handler that manages loading, saving, and the instance
    public static final ConfigClassHandler<ModConfig> HANDLER = ConfigClassHandler.createBuilder(ModConfig.class)
            .id(Identifier.fromNamespaceAndPath("fireballpredictor", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("fireballpredictor.json"))
                    .build())
            .build();

    private static final Set<String> COLLAPSED_GROUP_KEYS = Set.of(
        "tracking_mobs",
        "tracking_other"
    );

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
    public static YetAnotherConfigLib generateGui() {
        // 1. Evaluate server tracking restrictions before building the GUI options
        int serverMask = ServerTrackingRules.mask();
        boolean playerAvailable = !ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER);
        boolean dispenserAvailable = !ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER);
        boolean commandAvailable = !ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND);
        boolean otherGroupAvailable = (serverMask & TrackingRules.OTHER_GROUP) != TrackingRules.OTHER_GROUP;

        String prefix = "yacl3.config." + HANDLER.id().getNamespace() + ":" + HANDLER.id().getPath() + ".";
        String playerKey = prefix + "trackPlayerProjectiles";
        String dispenserKey = prefix + "trackDispenserProjectiles";
        String commandKey = prefix + "trackCommandProjectiles";
        String otherKey = prefix + "trackOtherOwnerProjectiles";

        YetAnotherConfigLib baseGui = HANDLER.generateGui();
        Minecraft client = Minecraft.getInstance();
        String serverIp = (client != null && client.getCurrentServer() != null)
                ? client.getCurrentServer().ip
                : null;

        Option<Float> serverOption = null;
        if (serverIp != null && !serverIp.trim().isEmpty()) {
            final String ip = serverIp.trim().toLowerCase(java.util.Locale.ROOT);
            ModConfig config = instance();

            serverOption = Option.<Float>createBuilder()
                .name(Component.translatable("yacl.config.fireballpredictor:serverFallbackFireballPower", ip))
                .description(OptionDescription.of(
                        Component.translatable("yacl.config.fireballpredictor:serverFallbackFireballPower.desc", ip)
                ))
                .binding(
                        0.0f,
                        () -> config.serverFallbackPowers.getOrDefault(ip, 0.0f),
                        val -> config.setServerFallbackPower(ip, val)
                )
                .controller(opt -> FloatFieldControllerBuilder.create(opt)
                        .min(0.0f)
                        .max(100.0f)
                        .formatValue(v -> v <= 0.0f
                                ? Component.literal("0.00 (Auto / None)")
                                : Component.literal(String.format(java.util.Locale.ROOT, "%.2f", v))))
                .build();
        }

        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(baseGui.title())
                .save(ModConfig::save);

        for (ConfigCategory category : baseGui.categories()) {
            ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
                    .name(category.name());

            if (category.tooltip() != null) {
                categoryBuilder.tooltip(category.tooltip());
            }

            for (OptionGroup group : category.groups()) {
                if (group.isRoot()) {
                    categoryBuilder.group(group);
                    continue;
                }

                // Determine if this group should be collapsed
                boolean shouldCollapse = false;
                String groupKey = group.name().getContents() instanceof TranslatableContents tc
                        ? tc.getKey()
                        : group.name().getString();

                for (String suffix : COLLAPSED_GROUP_KEYS) {
                    if (groupKey.endsWith(suffix) || groupKey.endsWith("group." + suffix)) {
                        shouldCollapse = true;
                        break;
                    }
                }

                // Build option group with availability set directly on existing YACL option
                OptionGroup.Builder groupBuilder = OptionGroup.createBuilder()
                        .name(group.name())
                        .collapsed(shouldCollapse);

                if (group.description() != null) {
                    groupBuilder.description(group.description());
                }

                for (Option<?> opt : group.options()) {
                    if (opt != null && opt.name() != null && opt.name().getContents() instanceof TranslatableContents tc) {
                        String key = tc.getKey();
                        if ((key.equals(playerKey) && !playerAvailable)
                                || (key.equals(dispenserKey) && !dispenserAvailable)
                                || (key.equals(commandKey) && !commandAvailable)
                                || (key.equals(otherKey) && !otherGroupAvailable)) {
                            opt.setAvailable(false);
                        }
                    }

                    groupBuilder.option(opt);
                }

                categoryBuilder.group(groupBuilder.build());
            }

            if (serverOption != null && category.name().getContents() instanceof TranslatableContents translatable) {
                if (translatable.getKey().endsWith("general")) {
                    categoryBuilder.option(serverOption);
                }
            }

            builder.category(categoryBuilder.build());
        }

        return builder.build();
    }

    public static Screen createScreen(Screen parentScreen) {
        return generateGui().generateScreen(parentScreen);
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
