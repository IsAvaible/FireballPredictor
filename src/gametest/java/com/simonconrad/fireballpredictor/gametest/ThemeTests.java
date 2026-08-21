package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import com.simonconrad.fireballpredictor.projectile.WarningProjectileType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class ThemeTests extends GameTestBase {

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWarningProjectileTypeResolution(GameTestHelper context) {
        resetGlobalState();

        LargeFireball largeFireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.0, false);
        SmallFireball smallFireball = spawnProjectile(context, EntityTypes.SMALL_FIREBALL, 0.0, false);
        DragonFireball dragonFireball = spawnProjectile(context, EntityTypes.DRAGON_FIREBALL, 0.0, false);
        WitherSkull normalSkull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, false);
        WitherSkull chargedSkull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        WindCharge windCharge = spawnProjectile(context, EntityTypes.WIND_CHARGE, 0.0, false);

        if (WarningProjectileType.fromProjectile(largeFireball) != WarningProjectileType.FIREBALL
                || WarningProjectileType.FIREBALL.icon().getItem() != Items.FIRE_CHARGE) {
            throw fail("LargeFireball should resolve to WarningProjectileType.FIREBALL with FIRE_CHARGE icon");
        }
        if (WarningProjectileType.fromProjectile(smallFireball) != WarningProjectileType.FIREBALL) {
            throw fail("SmallFireball should resolve to WarningProjectileType.FIREBALL");
        }
        if (WarningProjectileType.fromProjectile(dragonFireball) != WarningProjectileType.DRAGON_FIREBALL
                || WarningProjectileType.DRAGON_FIREBALL.barFillColor() != 0xFFC832D4
                || WarningProjectileType.DRAGON_FIREBALL.customTexture() == null) {
            throw fail("DragonFireball should resolve to WarningProjectileType.DRAGON_FIREBALL with purple bar and dragon fireball texture");
        }
        if (WarningProjectileType.fromProjectile(normalSkull) != WarningProjectileType.WITHER_SKULL
                || WarningProjectileType.WITHER_SKULL.icon().getItem() != Items.WITHER_SKELETON_SKULL) {
            throw fail("Normal WitherSkull should resolve to WarningProjectileType.WITHER_SKULL with WITHER_SKELETON_SKULL icon");
        }
        if (WarningProjectileType.fromProjectile(chargedSkull) != WarningProjectileType.WITHER_SKULL) {
            throw fail("Charged WitherSkull should resolve to WarningProjectileType.WITHER_SKULL");
        }
        if (WarningProjectileType.fromProjectile(windCharge) != WarningProjectileType.WIND_CHARGE
                || WarningProjectileType.WIND_CHARGE.icon().getItem() != Items.WIND_CHARGE) {
            throw fail("WindCharge should resolve to WarningProjectileType.WIND_CHARGE with WIND_CHARGE icon");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testVisualThemesRosterAndColorMath(GameTestHelper context) {
        resetGlobalState();

        VisualTheme[] themes = VisualTheme.values();
        if (themes.length != 16) {
            throw fail("Expected 16 visual themes, found: " + themes.length);
        }

        if (VisualTheme.DEFAULT.isCustomTheme()) {
            throw fail("VisualTheme.DEFAULT should return false for isCustomTheme()");
        }

        int fallbackRgb = 0xFF8000;
        for (VisualTheme theme : themes) {
            if (theme.getKey() == null || theme.getKey().isEmpty()) {
                throw fail("Theme " + theme.name() + " has null or empty key");
            }
            if (theme.getDisplayName() == null) {
                throw fail("Theme " + theme.name() + " returned null getDisplayName()");
            }

            if (theme != VisualTheme.DEFAULT && !theme.isCustomTheme()) {
                throw fail("Theme " + theme.name() + " should return true for isCustomTheme()");
            }

            // Test ribbon color evaluation across progress
            for (float p = 0.0f; p <= 1.0f; p += 0.25f) {
                int shroudRgb = theme.getRibbonColorPacked(p, 10.0, 0, false, fallbackRgb);
                int coreRgb = theme.getRibbonColorPacked(p, 10.0, 0, true, fallbackRgb);
                float alphaMod = theme.getRibbonAlphaModulation(p, 10.0, 0);

                if (shroudRgb < 0 || shroudRgb > 0xFFFFFF) {
                    throw fail("Invalid shroud RGB for theme " + theme.name() + ": 0x" + Integer.toHexString(shroudRgb));
                }
                if (coreRgb < 0 || coreRgb > 0xFFFFFF) {
                    throw fail("Invalid core RGB for theme " + theme.name() + ": 0x" + Integer.toHexString(coreRgb));
                }
                if (alphaMod < 0.0f || alphaMod > 3.0f) {
                    throw fail("Invalid alpha modulation for theme " + theme.name() + ": " + alphaMod);
                }
            }

            // Test dome color evaluation across lat/lon
            for (float lat = 0.0f; lat <= 1.0f; lat += 0.25f) {
                for (float lon = 0.0f; lon <= 1.0f; lon += 0.25f) {
                    int domeRgb = theme.getDomeColorPacked(null, null, lat, lon, 10.0, fallbackRgb);
                    if (domeRgb < 0 || domeRgb > 0xFFFFFF) {
                        throw fail("Invalid dome RGB for theme " + theme.name() + ": 0x" + Integer.toHexString(domeRgb));
                    }
                }
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testThemePreviewGallery(GameTestHelper context) {
        resetGlobalState();

        // 1. Test procedural dome mesh creation used by the theme preview gallery
        PredictionRenderData domeMesh = TrajectoryPredictor.createRenderData(1.3f);
        if (domeMesh == null || domeMesh.domeQuads().isEmpty()) {
            throw fail("TrajectoryPredictor.createRenderData(1.3f) returned empty or null dome quads");
        }

        if (domeMesh.domeQuads().size() < 100) {
            throw fail("Expected at least 100 dome quads, found: " + domeMesh.domeQuads().size());
        }

        // Verify each dome quad has 4 valid vertices and alpha bounds
        for (PredictionRenderData.DomeQuad quad : domeMesh.domeQuads()) {
            if (quad.p1() == null || quad.p2() == null || quad.p3() == null || quad.p4() == null) {
                throw fail("Found null vertex in preview gallery dome mesh");
            }
            if (quad.alpha1() < 0 || quad.alpha1() > 255 || quad.alpha2() < 0 || quad.alpha2() > 255) {
                throw fail("Invalid alpha bounds in preview gallery dome mesh: " + quad.alpha1() + ", " + quad.alpha2());
            }
        }

        // 2. Test mathematical circular gallery track distribution and chord spacing
        int count = VisualTheme.values().length;
        double spacing = 6.5;
        double minRadius = 12.0;
        double dynamicRadius = spacing / (2.0 * Math.sin(Math.PI / count));
        double radius = Math.max(minRadius, dynamicRadius);

        if (radius < 12.0) {
            throw fail("Gallery radius must be at least 12.0 blocks, got: " + radius);
        }

        // Verify adjacent track points satisfy chord distance >= 6.49
        double angle0 = 0.0;
        double angle1 = (2.0 * Math.PI) / count;
        Vec3 pos0 = new Vec3(-Math.sin(angle0) * radius, 0.0, Math.cos(angle0) * radius);
        Vec3 pos1 = new Vec3(-Math.sin(angle1) * radius, 0.0, Math.cos(angle1) * radius);
        double chordDist = pos0.distanceTo(pos1);
        if (chordDist < 6.49) {
            throw fail("Adjacent gallery tracks chord distance " + chordDist + " is less than minimum 6.5 blocks");
        }

        // 3. Test ModConfig theme state modification and persistence
        ModConfig.instance().visualTheme = VisualTheme.INFERNO;
        if (ModConfig.instance().visualTheme != VisualTheme.INFERNO) {
            throw fail("Failed to set config visualTheme to INFERNO");
        }

        ModConfig.instance().visualTheme = VisualTheme.MATRIX;
        if (ModConfig.instance().visualTheme != VisualTheme.MATRIX) {
            throw fail("Failed to set config visualTheme to MATRIX");
        }

        ModConfig.instance().visualTheme = VisualTheme.DEFAULT;
        if (ModConfig.instance().visualTheme != VisualTheme.DEFAULT) {
            throw fail("Failed to reset config visualTheme to DEFAULT");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testVisualThemeConfigOptionDisabling(GameTestHelper context) {
        resetGlobalState();

        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) {
            context.succeed();
            return;
        }

        try {
            Class<?> guiClass = Class.forName("com.simonconrad.fireballpredictor.client.gui.ModConfigGui");
            java.lang.reflect.Method generateGuiMethod = guiClass.getMethod("generateGui");

            ModConfig.instance().visualTheme = VisualTheme.DEFAULT;
            dev.isxander.yacl3.api.YetAnotherConfigLib gui = (dev.isxander.yacl3.api.YetAnotherConfigLib) generateGuiMethod.invoke(null);

            dev.isxander.yacl3.api.Option<VisualTheme> themeOpt = null;
            java.util.Map<String, dev.isxander.yacl3.api.Option<?>> colorOpts = new java.util.HashMap<>();

            for (dev.isxander.yacl3.api.ConfigCategory cat : gui.categories()) {
                for (dev.isxander.yacl3.api.OptionGroup grp : cat.groups()) {
                    for (dev.isxander.yacl3.api.Option<?> opt : grp.options()) {
                        if (opt.name().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                            String key = tc.getKey();
                            if (key.endsWith("visualTheme")) {
                                //noinspection unchecked
                                themeOpt = (dev.isxander.yacl3.api.Option<VisualTheme>) opt;
                            } else if (key.endsWith("trajectoryColor") || key.endsWith("windChargeTrajectoryColor")
                                    || key.endsWith("shockwaveColor") || key.endsWith("windChargeShockwaveColor")) {
                                colorOpts.put(key, opt);
                            }
                        }
                    }
                }
            }

            if (themeOpt == null) {
                throw fail("Could not find visualTheme option in GUI");
            }
            if (colorOpts.size() != 4) {
                throw fail("Expected 4 theme-overridden color options, found: " + colorOpts.size());
            }

            // 1. In DEFAULT theme, all color options must be available (enabled)
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (!entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be available in DEFAULT theme");
                }
            }

            // 2. Switching to a non-default theme dynamically disables all 4 color options
            themeOpt.requestSet(VisualTheme.RAINBOW);
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be disabled in RAINBOW theme");
                }
            }

            themeOpt.requestSet(VisualTheme.CYBERPUNK);
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be disabled in CYBERPUNK theme");
                }
            }

            // 3. Switching back to DEFAULT theme re-enables all 4 color options
            themeOpt.requestSet(VisualTheme.DEFAULT);
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (!entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be re-enabled when returning to DEFAULT theme");
                }
            }

            // 4. GUI initialized with a non-default theme must have color options disabled initially
            ModConfig.instance().visualTheme = VisualTheme.MATRIX;
            dev.isxander.yacl3.api.YetAnotherConfigLib nonDefaultGui = (dev.isxander.yacl3.api.YetAnotherConfigLib) generateGuiMethod.invoke(null);
            for (dev.isxander.yacl3.api.ConfigCategory cat : nonDefaultGui.categories()) {
                for (dev.isxander.yacl3.api.OptionGroup grp : cat.groups()) {
                    for (dev.isxander.yacl3.api.Option<?> opt : grp.options()) {
                        if (opt.name().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                            String key = tc.getKey();
                            if (key.endsWith("trajectoryColor") || key.endsWith("windChargeTrajectoryColor")
                                    || key.endsWith("shockwaveColor") || key.endsWith("windChargeShockwaveColor")) {
                                if (opt.available()) {
                                    throw fail("Color option " + key + " should be initially disabled when config has MATRIX theme");
                                }
                            }
                        }
                    }
                }
            }

            ModConfig.instance().visualTheme = VisualTheme.DEFAULT;
        } catch (GameTestAssertException gae) {
            throw gae;
        } catch (Exception e) {
            throw fail("Failed to test GUI: " + e.getMessage());
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testThemeTimeAndColorPins(GameTestHelper context) {
        resetGlobalState();

        // Frozen animation speed must freeze the dome pulse at full alpha (not at the sine midpoint),
        // and the pulse must stay within its designed range for normal speeds.
        if (VisualTheme.computePulseFactor(0.0) != 1.0f) {
            throw fail("computePulseFactor(0.0) must return 1.0f, got: " + VisualTheme.computePulseFactor(0.0));
        }
        float pulse = VisualTheme.computePulseFactor(1.0);
        if (pulse < 0.6f || pulse > 1.0f) {
            throw fail("computePulseFactor(1.0) out of range: " + pulse);
        }

        // The DEFAULT theme must preserve the pre-theme trail core brightening (0.35 white mix)
        // so the default rendering stays pixel-identical to the pre-theme renderer.
        int fallback = 0xFF8000;
        int core = VisualTheme.DEFAULT.getRibbonColorPacked(0.5f, 1.0, 0, true, fallback);
        int expected = VisualTheme.lightenRgb(fallback, 0.35f);
        if (core != expected) {
            throw fail("DEFAULT core color changed: got 0x" + Integer.toHexString(core)
                    + ", expected 0x" + Integer.toHexString(expected));
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testVisualThemesPreviewRepresentation(GameTestHelper context) {
        resetGlobalState();

        VisualTheme[] themes = VisualTheme.values();
        if (themes.length != 16) {
            throw fail("Expected exactly 16 visual themes, got: " + themes.length);
        }

        int fallbackRgb = 0xFF8000;
        for (VisualTheme theme : themes) {
            if (theme.getKey() == null || theme.getKey().isEmpty()) {
                throw fail("Theme " + theme.name() + " has null/empty key");
            }
            if (theme.getDisplayName() == null) {
                throw fail("Theme " + theme.name() + " has null displayName");
            }

            // Test color evaluations at multiple time and spatial sample points
            for (double t = 0.0; t <= 10.0; t += 2.5) {
                for (float p = 0.0f; p <= 1.0f; p += 0.2f) {
                    int ribbonShroud = theme.getRibbonColorPacked(p, t, 0, false, fallbackRgb);
                    int ribbonCore = theme.getRibbonColorPacked(p, t, 0, true, fallbackRgb);
                    float ribbonAlphaMod = theme.getRibbonAlphaModulation(p, t, 0);

                    if (ribbonShroud < 0 || ribbonShroud > 0xFFFFFF) {
                        throw fail("Theme " + theme.name() + " invalid ribbon shroud: 0x" + Integer.toHexString(ribbonShroud));
                    }
                    if (ribbonCore < 0 || ribbonCore > 0xFFFFFF) {
                        throw fail("Theme " + theme.name() + " invalid ribbon core: 0x" + Integer.toHexString(ribbonCore));
                    }
                    if (ribbonAlphaMod < 0.0f || ribbonAlphaMod > 4.0f) {
                        throw fail("Theme " + theme.name() + " invalid ribbon alpha mod: " + ribbonAlphaMod);
                    }

                    int domePacked = theme.getDomeColorPacked(null, null, p, p, t, fallbackRgb);
                    float domeAlphaMod = theme.getDomeAlphaModulation(p, p, t);

                    if (domePacked < 0 || domePacked > 0xFFFFFF) {
                        throw fail("Theme " + theme.name() + " invalid dome color: 0x" + Integer.toHexString(domePacked));
                    }
                    if (domeAlphaMod < 0.0f || domeAlphaMod > 4.0f) {
                        throw fail("Theme " + theme.name() + " invalid dome alpha mod: " + domeAlphaMod);
                    }
                }
            }
        }

        context.succeed();
    }
}
