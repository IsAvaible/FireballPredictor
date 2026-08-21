package com.simonconrad.fireballpredictor.projectile;

/**
 * Canonical registry of every distinct projectile kind the mod understands.
 *
 * <p>Adding a new vanilla or modded projectile type (e.g. a lingering potion or a ghast-fireball
 * variant) is now a single step: add a constant here and a profile in {@link VanillaProfiles}.
 * Nothing downstream re-encodes "what is this entity" with {@code instanceof} checks anymore; every
 * behaviour (drag, explosion power, direct-hit damage, block breaking, warning type, colors,
 * filter keys) is derived from a {@link ProjectileProfile} keyed by one of these kinds.
 */
public enum ProjectileKind {
    LARGE_FIREBALL,
    SMALL_FIREBALL,
    DRAGON_FIREBALL,
    WITHER_SKULL,
    WIND_CHARGE,
    BREEZE_WIND_CHARGE;

    /** True for every wind-charge flavour ({@code AbstractWindCharge} subclasses). */
    public boolean isWindCharge() {
        return this == WIND_CHARGE || this == BREEZE_WIND_CHARGE;
    }
}
