package com.simonconrad.fireballpredictor.tracking;

/**
 * Inferred origin of a hostile projectile for owner-based tracking filters.
 */
public enum ProjectileOwner {
    BLAZE,
    GHAST,
    ENDER_DRAGON,
    WITHER,
    BREEZE,
    PLAYER,
    DISPENSER,
    /** Summoned / command-block / unmatched environmental spawn. */
    COMMAND,
    UNKNOWN;

    public boolean isMob() {
        return this == BLAZE
                || this == GHAST
                || this == ENDER_DRAGON
                || this == WITHER
                || this == BREEZE;
    }

    public static ProjectileOwner fromOrdinalClamped(int ordinal) {
        ProjectileOwner[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return UNKNOWN;
        }
        return values[ordinal];
    }

    public static ProjectileOwner fromName(String name) {
        if (name == null || name.isEmpty()) {
            return UNKNOWN;
        }
        try {
            return ProjectileOwner.valueOf(name);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
