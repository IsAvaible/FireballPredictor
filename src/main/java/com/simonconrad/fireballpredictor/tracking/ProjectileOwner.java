package com.simonconrad.fireballpredictor.tracking;

/**
 * Inferred origin of a hostile projectile for owner-based tracking filters
 * (1.8.9 subset of master's ProjectileOwner - no breeze, dragon or wind charges).
 */
public enum ProjectileOwner {
    BLAZE,
    GHAST,
    WITHER,
    PLAYER,
    DISPENSER,
    /** Summoned / command-block / unmatched environmental spawn. */
    COMMAND,
    UNKNOWN;

    public boolean isMob() {
        return this == BLAZE || this == GHAST || this == WITHER;
    }

    public static ProjectileOwner fromOrdinalClamped(int ordinal) {
        ProjectileOwner[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return UNKNOWN;
        }
        return values[ordinal];
    }
}
