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

        java.util.Map<String, java.util.List<Option<?>>> projectileColorOptions = new java.util.HashMap<>();
        java.util.Map<String, Option<com.simonconrad.fireballpredictor.config.ProjectileVisualTheme>> projectileThemeOptions = new java.util.HashMap<>();

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

        final java.util.concurrent.atomic.AtomicReference<Option<com.simonconrad.fireballpredictor.config.VisualTheme>> themeOption =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.function.Function<String, com.simonconrad.fireballpredictor.config.VisualTheme> resolvePendingTheme = pGroup -> {
            Option<com.simonconrad.fireballpredictor.config.VisualTheme> configuredThemeOpt = themeOption.get();
            com.simonconrad.fireballpredictor.config.VisualTheme global =
                    configuredThemeOpt == null || configuredThemeOpt.pendingValue() == null
                            ? com.simonconrad.fireballpredictor.config.VisualTheme.DEFAULT : configuredThemeOpt.pendingValue();
            var typeTheme = projectileThemeOptions.get(pGroup);
            return (typeTheme == null || typeTheme.pendingValue() == null)
                    ? global : typeTheme.pendingValue().resolve(global);
        };

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
                        String field = key.substring(key.lastIndexOf('.') + 1);
                        String projectileGroup = switch (field) {
                            case "trajectoryColor", "shockwaveColor", "fireballVisualTheme" -> "fireball";
                            case "windChargeTrajectoryColor", "windChargeShockwaveColor", "windChargeVisualTheme" -> "wind_charge";
                            case "witherSkullTrajectoryColor", "witherSkullShockwaveColor", "witherSkullVisualTheme" -> "wither_skull";
                            case "dragonFireballTrajectoryColor", "dragonFireballShockwaveColor", "dragonFireballVisualTheme" -> "dragon_fireball";
                            default -> null;
                        };

                        if (projectileGroup != null && field.endsWith("VisualTheme") && !field.equals("visualTheme")) {
                            @SuppressWarnings("unchecked")
                            Option<com.simonconrad.fireballpredictor.config.ProjectileVisualTheme> origThemeOpt =
                                    (Option<com.simonconrad.fireballpredictor.config.ProjectileVisualTheme>) opt;
                            Option<com.simonconrad.fireballpredictor.config.ProjectileVisualTheme> dropdownProjThemeOpt = Option.<com.simonconrad.fireballpredictor.config.ProjectileVisualTheme>createBuilder()
                                    .name(origThemeOpt.name())
                                    .description(origThemeOpt.description())
                                    .binding(origThemeOpt.binding())
                                    .controller(dev.isxander.yacl3.api.controller.EnumDropdownControllerBuilder::create)
                                    .flags(origThemeOpt.flags())
                                    .listener((o, val) -> origThemeOpt.requestSet(val))
                                    .build();
                            projectileThemeOptions.put(projectileGroup, dropdownProjThemeOpt);
                            groupBuilder.option(dropdownProjThemeOpt);
                            continue;
                        }

                        if (projectileGroup != null && field.endsWith("Color")) {
                            @SuppressWarnings("unchecked")
                            Option<java.awt.Color> colorOpt = (Option<java.awt.Color>) opt;
                            OptionDescription origDesc = colorOpt.description();
                            final String pGroup = projectileGroup;

                            Option<java.awt.Color> colorOptionWithDynamicDesc = Option.<java.awt.Color>createBuilder()
                                    .name(colorOpt.name())
                                    .description(new OptionDescription() {
                                        @Override
                                        public Component text() {
                                            com.simonconrad.fireballpredictor.config.VisualTheme resolved = resolvePendingTheme.apply(pGroup);
                                            if (resolved != com.simonconrad.fireballpredictor.config.VisualTheme.DEFAULT) {
                                                return Component.translatable(
                                                        "yacl3.config.fireballpredictor:config.colorOption.lockedByTheme",
                                                        resolved.getDisplayName()
                                                ).append("\n\n").append(origDesc.text());
                                            }
                                            return origDesc.text();
                                        }

                                        @Override
                                        public java.util.concurrent.CompletableFuture<java.util.Optional<dev.isxander.yacl3.gui.image.ImageRenderer>> image() {
                                            return origDesc.image();
                                        }
                                    })
                                    .binding(colorOpt.binding())
                                    .controller(dev.isxander.yacl3.api.controller.ColorControllerBuilder::create)
                                    .flags(colorOpt.flags())
                                    .listener((o, val) -> colorOpt.requestSet(val))
                                    .build();

                            projectileColorOptions.computeIfAbsent(projectileGroup, ignored -> new java.util.ArrayList<>()).add(colorOptionWithDynamicDesc);
                            groupBuilder.option(colorOptionWithDynamicDesc);
                            continue;
                        }

                        if ((key.equals(playerKey) && !playerAvailable)
                                || (key.equals(dispenserKey) && !dispenserAvailable)
                                || (key.equals(commandKey) && !commandAvailable)
                                || (key.equals(otherKey) && !otherGroupAvailable)) {
                            opt.setAvailable(false);
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
                            themeOption.set(dropdownThemeOpt);
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

        Runnable refreshProjectileColorAvailability = () -> {
            for (var entry : projectileColorOptions.entrySet()) {
                boolean useCustomColors = resolvePendingTheme.apply(entry.getKey()) == com.simonconrad.fireballpredictor.config.VisualTheme.DEFAULT;
                for (Option<?> color : entry.getValue()) {
                    color.setAvailable(useCustomColors);
                }
            }
        };
        refreshProjectileColorAvailability.run();
        Option<com.simonconrad.fireballpredictor.config.VisualTheme> configuredThemeOpt = themeOption.get();
        if (configuredThemeOpt != null) {
            configuredThemeOpt.addListener((opt, val) -> refreshProjectileColorAvailability.run());
        }
        for (Option<com.simonconrad.fireballpredictor.config.ProjectileVisualTheme> opt : projectileThemeOptions.values()) {
            opt.addListener((changed, val) -> refreshProjectileColorAvailability.run());
        }

        return builder.build();
    }
}
