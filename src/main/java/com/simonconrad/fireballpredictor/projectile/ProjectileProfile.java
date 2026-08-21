package com.simonconrad.fireballpredictor.projectile;

import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;

/**
 * Immutable, per-kind behavioural description of a projectile. This is the single source of truth
 * for everything the mod used to re-derive with scattered {@code instanceof} checks (explosion
 * power, drag, direct-hit damage, block breaking, warning type, default colors, filter keys).
 *
 * <p>A profile is registered exactly once (see {@link VanillaProfiles}) and is what
 * {@code ImpactPredictor} / {@code TrajectoryPredictor} / {@code DamageCalculator} /
 * {@code TrackedProjectile} / {@code ModConfig} operate on, instead of branching on live entity
 * classes.
 */
public record ProjectileProfile(
        ProjectileKind kind,
        double dragAir,
        double dragWater,
        double dangerousDragAir,
        float staticExplosionPower,
        boolean dynamicExplosionPower,
        float directHitDamage,
        boolean breaksBlocks,
        ProjectileFilterKey filterKey,
        WarningProjectileType warningType,
        int defaultTrajectoryColorRgb,
        int defaultShockwaveColorRgb) {

    /**
     * Effective air drag, honouring the dynamic charged-wither-skull override ({@code 0.73}).
     * Every other projectile simply uses {@link #dragAir()}.
     */
    public double airDrag(AbstractHurtingProjectile projectile) {
        return (kind == ProjectileKind.WITHER_SKULL
                && projectile instanceof WitherSkull skull && skull.isDangerous())
                ? dangerousDragAir
                : dragAir;
    }
}
