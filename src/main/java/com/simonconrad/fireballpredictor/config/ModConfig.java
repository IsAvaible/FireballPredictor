package com.simonconrad.fireballpredictor.config;

import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.autogen.Dropdown;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.FloatField;
import dev.isxander.yacl3.config.v2.api.autogen.IntField;
import dev.isxander.yacl3.config.v2.api.autogen.ColorField;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
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
    @SerialEntry
    @AutoGen(category = "general")
    @FloatField(min = 0.0f, max = 100.0f)
    public float clientFallbackFireballPower = 1.0F;

    @SerialEntry
    @AutoGen(category = "general")
    @FloatField(min = 0.7f, max = 1.3f)
    public float rayPowerMultiplier = 1.3F;

    @SerialEntry
    @AutoGen(category = "general")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean trackWitherSkulls = true;

    @SerialEntry
    @AutoGen(category = "visuals")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean renderTrajectory = true;

    @SerialEntry
    @AutoGen(category = "visuals")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean renderShockwaveDome = true;

    @SerialEntry
    @AutoGen(category = "visuals")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean renderBlockHighlights = true;

    @SerialEntry
    @AutoGen(category = "visuals")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean renderParticleAccents = true;

    @SerialEntry
    @AutoGen(category = "visuals")
    @ColorField
    public Color trajectoryColor = new Color(255, 128, 0);

    @SerialEntry
    @AutoGen(category = "visuals")
    @FloatField(min = 0.1f, max = 2.0f)
    public float trajectoryWidth = 0.5f;

    @SerialEntry
    @AutoGen(category = "visuals")
    @ColorField
    public Color shockwaveColor = new Color(255, 128, 0);

    @SerialEntry
    @AutoGen(category = "visuals")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean renderImpactWarning = true;

    @SerialEntry
    @AutoGen(category = "visuals")
    @Dropdown(values = {"topleft", "topcenter", "topright", "bottomleft", "bottomcenter", "bottomright"})
    public String impactWarningBadgeAnchor = "topleft";

    @SerialEntry
    @AutoGen(category = "visuals")
    @IntField(min = -1000, max = 1000, format = "%d")
    public int impactWarningBadgeOffsetX = 0;

    @SerialEntry
    @AutoGen(category = "visuals")
    @IntField(min = -1000, max = 1000, format = "%d")
    public int impactWarningBadgeOffsetY = 0;

    // 3. Helper methods to match your existing client initialization calls
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
