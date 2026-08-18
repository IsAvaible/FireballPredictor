package com.simonconrad.fireballpredictor.client.gui;

import com.simonconrad.fireballpredictor.client.render.ThemePreviewGallery;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRules;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.FloatFieldControllerBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.Set;

@Environment(EnvType.CLIENT)
public final class ModConfigGui {

    private static final Set<String> COLLAPSED_GROUP_KEYS = Set.of(
        "tracking_mobs",
        "tracking_other"
    );

    private ModConfigGui() {}

    public static Screen createScreen(Screen parentScreen) {
        return generateGui().generateScreen(parentScreen);
    }

    public static YetAnotherConfigLib generateGui() {
        // 1. Evaluate server tracking restrictions before building the GUI options
        int serverMask = ServerTrackingRules.mask();
        boolean playerAvailable = !ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER);
        boolean dispenserAvailable = !ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER);
        boolean commandAvailable = !ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND);
        boolean otherGroupAvailable = (serverMask & TrackingRules.OTHER_GROUP) != TrackingRules.OTHER_GROUP;

        String prefix = "yacl3.config." + ModConfig.HANDLER.id().getNamespace() + ":" + ModConfig.HANDLER.id().getPath() + ".";
        String playerKey = prefix + "trackPlayerProjectiles";
        String dispenserKey = prefix + "trackDispenserProjectiles";
        String commandKey = prefix + "trackCommandProjectiles";
        String otherKey = prefix + "trackOtherOwnerProjectiles";
        String visualThemeKey = prefix + "visualTheme";

        String trajectoryColorKey = prefix + "trajectoryColor";
        String windTrajectoryColorKey = prefix + "windChargeTrajectoryColor";
        String shockwaveColorKey = prefix + "shockwaveColor";
        String windShockwaveColorKey = prefix + "windChargeShockwaveColor";

        java.util.Set<String> themeOverriddenKeys = java.util.Set.of(
                trajectoryColorKey,
                windTrajectoryColorKey,
                shockwaveColorKey,
                windShockwaveColorKey
        );

        YetAnotherConfigLib baseGui = ModConfig.HANDLER.generateGui();
        Minecraft client = Minecraft.getInstance();

        ButtonOption galleryButton = ButtonOption.createBuilder()
                .name(Component.translatable("yacl3.config.fireballpredictor:config.previewGallery"))
                .description(OptionDescription.of(
                        Component.translatable("yacl3.config.fireballpredictor:config.previewGallery.desc")
                ))
                .text(Component.translatable("yacl3.config.fireballpredictor:config.previewGallery.button"))
                .action((screen, buttonOpt) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.player != null) {
                        ThemePreviewGallery.toggle(mc.player);
                    }
                })
                .build();
        if (client == null || client.player == null) {
            galleryButton.setAvailable(false);
        }

        String serverIp = (client != null && client.getCurrentServer() != null)
                ? client.getCurrentServer().ip
                : null;

        Option<Float> serverOption = null;
        if (serverIp != null && !serverIp.trim().isEmpty()) {
            final String ip = serverIp.trim().toLowerCase(java.util.Locale.ROOT);
            ModConfig config = ModConfig.instance();

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

        Option<com.simonconrad.fireballpredictor.config.VisualTheme> themeOption = null;
        java.util.List<Option<?>> themeOverriddenOptions = new java.util.ArrayList<>();

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

                        if (themeOverriddenKeys.contains(key)) {
                            themeOverriddenOptions.add(opt);
                        }

                        if (key.equals(visualThemeKey)) {
                            @SuppressWarnings("unchecked")
                            Option<com.simonconrad.fireballpredictor.config.VisualTheme> themeOpt = (Option<com.simonconrad.fireballpredictor.config.VisualTheme>) opt;
                            Option<com.simonconrad.fireballpredictor.config.VisualTheme> dropdownThemeOpt = Option.<com.simonconrad.fireballpredictor.config.VisualTheme>createBuilder()
                                    .name(themeOpt.name())
                                    .description(themeOpt.description())
                                    .binding(themeOpt.binding())
                                    .controller(dev.isxander.yacl3.api.controller.EnumDropdownControllerBuilder::create)
                                    .flags(themeOpt.flags())
                                    .listener((o, val) -> themeOpt.requestSet(val))
                                    .build();
                            themeOption = dropdownThemeOpt;
                            groupBuilder.option(dropdownThemeOpt);
                            groupBuilder.option(galleryButton);
                            continue;
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

        if (themeOption != null) {
            boolean isDefaultTheme = (themeOption.pendingValue() == com.simonconrad.fireballpredictor.config.VisualTheme.DEFAULT);
            for (Option<?> opt : themeOverriddenOptions) {
                opt.setAvailable(isDefaultTheme);
            }

            themeOption.addEventListener((opt, event) -> {
                boolean isDefault = (opt.pendingValue() == com.simonconrad.fireballpredictor.config.VisualTheme.DEFAULT);
                for (Option<?> colorOpt : themeOverriddenOptions) {
                    colorOpt.setAvailable(isDefault);
                }
            });
        }

        return builder.build();
    }
}
