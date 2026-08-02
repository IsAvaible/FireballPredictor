package com.simonconrad.fireballpredictor.tracking;

/**
 * Server-authoritative switches for the "other" owner category
 * (Player / Dispenser / Command projectiles).
 *
 * <p>Bit-mask based so a server can disable prediction tracking either for
 * the whole group or for individual sub-options. The mask is built from
 * {@code ServerConfig}, transported via {@code TrackingRulesPayload} and
 * enforced by the client inside {@code TrackedProjectile.evaluateFilter}.
 */
public final class TrackingRules {

    /** Sub-option bit: projectiles fired (or deflected) by players. */
    public static final int PLAYER = 1;

    /** Sub-option bit: projectiles fired by dispensers. */
    public static final int DISPENSER = 1 << 1;

    /** Sub-option bit: command-summoned or unmatched projectiles. */
    public static final int COMMAND = 1 << 2;

    /** All bits of the "other" owner category (whole group). */
    public static final int OTHER_GROUP = PLAYER | DISPENSER | COMMAND;

    private TrackingRules() {
    }

    /**
     * Bit that gates the given owner, or {@code 0} when the owner is not
     * server-restrictable (hostile mobs stay unrestricted). Mirrors the
     * client config mapping where {@link ProjectileOwner#UNKNOWN} shares the
     * command sub-option.
     */
    public static int bitFor(ProjectileOwner owner) {
        if (owner == null) {
            return 0;
        }
        return switch (owner) {
            case PLAYER -> PLAYER;
            case DISPENSER -> DISPENSER;
            case COMMAND, UNKNOWN -> COMMAND;
            default -> 0;
        };
    }

    /** Whether the mask disables prediction tracking for the given owner. */
    public static boolean isDisabled(int mask, ProjectileOwner owner) {
        int bit = bitFor(owner);
        return bit != 0 && (mask & bit) != 0;
    }

    /** Clamp an arbitrary mask (e.g. from a packet) to the supported "other" bits. */
    public static int sanitize(int mask) {
        return mask & OTHER_GROUP;
    }
}
