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
import net.minecraft.util.Identifier;
import java.awt.Color;

public class ModConfig {
    // 1. Create the handler that manages loading, saving, and the instance
    public static final ConfigClassHandler<ModConfig> HANDLER = ConfigClassHandler.createBuilder(ModConfig.class)
            .id(Identifier.of("fireballpredictor", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("fireballpredictor.json"))
                    .build())
            .build();

    // 2. Define your config values using annotations
    @SerialEntry
    @AutoGen(category = "general")
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
    @AutoGen(category = "general")
    @FloatField(min = 0.7f, max = 1.3f)
    public float rayPowerMultiplier = 1.3F;

    @SerialEntry
    @AutoGen(category = "general")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean trackWitherSkulls = true;

    @SerialEntry
    @AutoGen(category = "general")
    @dev.isxander.yacl3.config.v2.api.autogen.TickBox
    public boolean trackWindCharges = true;

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
    @ColorField
    public Color windChargeTrajectoryColor = new Color(255, 255, 255);

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
    @ColorField
    public Color windChargeShockwaveColor = new Color(255, 255, 255);

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

    public static net.minecraft.client.gui.screen.Screen createScreen(net.minecraft.client.gui.screen.Screen parentScreen) {
        dev.isxander.yacl3.api.YetAnotherConfigLib baseGui = HANDLER.generateGui();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        String serverIp = (client != null && client.getCurrentServerEntry() != null)
                ? client.getCurrentServerEntry().address
                : null;

        if (serverIp == null || serverIp.trim().isEmpty()) {
            return baseGui.generateScreen(parentScreen);
        }

        final String ip = serverIp.trim().toLowerCase(java.util.Locale.ROOT);
        ModConfig config = instance();

        dev.isxander.yacl3.api.Option<Float> serverOption = dev.isxander.yacl3.api.Option.<Float>createBuilder()
            .name(net.minecraft.text.Text.translatable("yacl.config.fireballpredictor:serverFallbackFireballPower", ip))
            .description(dev.isxander.yacl3.api.OptionDescription.of(
                    net.minecraft.text.Text.translatable("yacl.config.fireballpredictor:serverFallbackFireballPower.desc", ip)
            ))
            .binding(
                    0.0f,
                    () -> config.serverFallbackPowers.getOrDefault(ip, 0.0f),
                    val -> config.setServerFallbackPower(ip, val)
            )
            .controller(opt -> dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder.create(opt)
                    .min(0.0f)
                    .max(100.0f)
                    .formatValue(v -> v <= 0.0f
                            ? net.minecraft.text.Text.literal("0.00 (Auto / None)")
                            : net.minecraft.text.Text.literal(String.format("%.2f", v))))

            .build();

        dev.isxander.yacl3.api.YetAnotherConfigLib.Builder builder = dev.isxander.yacl3.api.YetAnotherConfigLib.createBuilder()
                .title(baseGui.title())
                .save(ModConfig::save);

        for (dev.isxander.yacl3.api.ConfigCategory category : baseGui.categories()) {
            dev.isxander.yacl3.api.ConfigCategory.Builder categoryBuilder = dev.isxander.yacl3.api.ConfigCategory.createBuilder()
                    .name(category.name());

            if (category.tooltip() != null) {
                categoryBuilder.tooltip(category.tooltip());
            }

            for (dev.isxander.yacl3.api.OptionGroup group : category.groups()) {
                categoryBuilder.group(group);
            }

            if (category.name().getContent() instanceof net.minecraft.text.TranslatableTextContent translatable) {
                if (translatable.getKey().endsWith("general")) {
                    categoryBuilder.option(serverOption);
                }
            }

            builder.category(categoryBuilder.build());
        }


        return builder.build().generateScreen(parentScreen);
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


