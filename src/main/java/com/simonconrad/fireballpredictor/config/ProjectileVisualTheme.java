package com.simonconrad.fireballpredictor.config;

import dev.isxander.yacl3.api.NameableEnum;
import net.minecraft.network.chat.Component;

/** A per-projectile theme; GLOBAL deliberately inherits the legacy global setting. */
public enum ProjectileVisualTheme implements NameableEnum {
    GLOBAL(null),
    DEFAULT(VisualTheme.DEFAULT), RAINBOW(VisualTheme.RAINBOW), CYBERPUNK(VisualTheme.CYBERPUNK),
    MATRIX(VisualTheme.MATRIX), INFERNO(VisualTheme.INFERNO), HEATMAP(VisualTheme.HEATMAP),
    CELESTIAL(VisualTheme.CELESTIAL), GHOST(VisualTheme.GHOST), SCULK_VOID(VisualTheme.SCULK_VOID),
    ELECTRIC_ARC(VisualTheme.ELECTRIC_ARC), TACTICAL_HUD(VisualTheme.TACTICAL_HUD),
    AURORA(VisualTheme.AURORA), SINGULARITY(VisualTheme.SINGULARITY), SAKURA(VisualTheme.SAKURA),
    CRYSTAL(VisualTheme.CRYSTAL), ARCADE(VisualTheme.ARCADE);

    private final VisualTheme theme;
    ProjectileVisualTheme(VisualTheme theme) { this.theme = theme; }

    public VisualTheme resolve(VisualTheme globalTheme) {
        return theme == null ? (globalTheme == null ? VisualTheme.DEFAULT : globalTheme) : theme;
    }

    public static ProjectileVisualTheme fromVisualTheme(VisualTheme theme) {
        if (theme == null) return GLOBAL;
        for (ProjectileVisualTheme pTheme : values()) {
            if (pTheme.theme == theme) {
                return pTheme;
            }
        }
        return GLOBAL;
    }

    @Override public Component getDisplayName() {
        if (this == GLOBAL) return Component.translatable("yacl3.config.fireballpredictor:config.projectileVisualTheme.global");
        if (this == DEFAULT) return Component.translatable("yacl3.config.fireballpredictor:config.projectileVisualTheme.default");
        return theme.getDisplayName();
    }
}
