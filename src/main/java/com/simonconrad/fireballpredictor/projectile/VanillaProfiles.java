package com.simonconrad.fireballpredictor.projectile;

import java.util.EnumMap;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of every projectile the mod understands, mapping a live {@link AbstractHurtingProjectile}
 * entity onto its {@link ProjectileProfile}.
 *
 * <p>All vanilla {@code instanceof} branching used to be spread across six files. It now lives in
 * exactly one place: {@link #kindOf(AbstractHurtingProjectile)}. Consumers (impact/trajectory
 * predictors, damage calculator, tracked-projectile filter, config theme/color lookup, warning
 * type) all read from the resulting profile.
 *
 * <p>The class is deliberately side-neutral: it references only shared Minecraft types, so it can
 * be exercised headlessly by the GameTest suite.
 */
public final class VanillaProfiles {

    private static final EnumMap<ProjectileKind, ProjectileProfile> ALL = new EnumMap<>(ProjectileKind.class);

    private VanillaProfiles() {
    }

    static {
        register(new ProjectileProfile(
                ProjectileKind.LARGE_FIREBALL,
                0.95, 0.8, 0.95,
                1.0f, true, 6.0f, true,
                ProjectileFilterKey.FIREBALL, WarningProjectileType.FIREBALL,
                0xFFFF8000, 0xFFFF8000));
        register(new ProjectileProfile(
                ProjectileKind.SMALL_FIREBALL,
                0.95, 0.8, 0.95,
                0.0f, false, 5.0f, false,
                ProjectileFilterKey.FIREBALL, WarningProjectileType.FIREBALL,
                0xFFFF8000, 0xFFFF8000));
        register(new ProjectileProfile(
                ProjectileKind.DRAGON_FIREBALL,
                0.95, 0.8, 0.95,
                0.0f, false, 0.0f, false,
                ProjectileFilterKey.FIREBALL, WarningProjectileType.DRAGON_FIREBALL,
                0xFFC832D4, 0xFFC832D4));
        register(new ProjectileProfile(
                ProjectileKind.WITHER_SKULL,
                0.95, 0.8, 0.73,
                1.0f, false, 8.0f, true,
                ProjectileFilterKey.WITHER_SKULL, WarningProjectileType.WITHER_SKULL,
                0xFFFF8000, 0xFFFF8000));
        register(new ProjectileProfile(
                ProjectileKind.WIND_CHARGE,
                1.0, 1.0, 1.0,
                1.2f, false, 0.0f, false,
                ProjectileFilterKey.WIND_CHARGE, WarningProjectileType.WIND_CHARGE,
                0xFFFFFFFF, 0xFFFFFFFF));
        register(new ProjectileProfile(
                ProjectileKind.BREEZE_WIND_CHARGE,
                1.0, 1.0, 1.0,
                3.0f, false, 0.0f, false,
                ProjectileFilterKey.WIND_CHARGE, WarningProjectileType.WIND_CHARGE,
                0xFFFFFFFF, 0xFFFFFFFF));
    }

    private static void register(ProjectileProfile profile) {
        ALL.put(profile.kind(), profile);
    }

    /** Returns the registered profile for a kind, or {@code null} if it has none. */
    @Nullable
    public static ProjectileProfile of(ProjectileKind kind) {
        return ALL.get(kind);
    }

    /**
     * Resolves the profile for a live projectile, or {@code null} for kinds the mod does not track.
     */
    @Nullable
    public static ProjectileProfile from(AbstractHurtingProjectile projectile) {
        ProjectileKind kind = kindOf(projectile);
        return kind == null ? null : ALL.get(kind);
    }

    /**
     * The single vanilla {@code instanceof} mapping: entity class {@literal ->} {@link ProjectileKind}.
     * Everything else in the mod consumes a {@link ProjectileProfile} derived from this.
     */
    @Nullable
    public static ProjectileKind kindOf(AbstractHurtingProjectile projectile) {
        // Breeze wind charges extend AbstractWindCharge, so check them first.
        if (projectile instanceof BreezeWindCharge) {
            return ProjectileKind.BREEZE_WIND_CHARGE;
        }
        if (projectile instanceof AbstractWindCharge) {
            return ProjectileKind.WIND_CHARGE;
        }
        if (projectile instanceof WitherSkull) {
            return ProjectileKind.WITHER_SKULL;
        }
        if (projectile instanceof LargeFireball) {
            return ProjectileKind.LARGE_FIREBALL;
        }
        if (projectile instanceof SmallFireball) {
            return ProjectileKind.SMALL_FIREBALL;
        }
        if (projectile instanceof DragonFireball) {
            return ProjectileKind.DRAGON_FIREBALL;
        }
        return null;
    }
}
