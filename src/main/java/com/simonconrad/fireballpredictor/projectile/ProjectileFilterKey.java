package com.simonconrad.fireballpredictor.projectile;

/**
 * Coarse filter category used to map a projectile onto the user-configurable "what" toggles in
 * {@code ModConfig} ({@code trackFireballs}, {@code trackWitherSkulls}, {@code trackWindCharges}).
 *
 * <p>Note that the filter category is coarser than {@link ProjectileKind}: e.g. large, small and
 * dragon fireballs all fall under {@link #FIREBALL}, while the visual theme/warning layer keeps
 * the dragon fireball distinct.
 */
public enum ProjectileFilterKey {
    FIREBALL,
    WITHER_SKULL,
    WIND_CHARGE
}
