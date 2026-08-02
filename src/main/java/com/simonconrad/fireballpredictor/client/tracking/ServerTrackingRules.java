package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.FireballPredictor;
import com.simonconrad.fireballpredictor.network.TrackingRulesPayload;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side store of the tracking restrictions enforced by the current
 * server, pushed via {@link TrackingRulesPayload}.
 *
 * An empty mask (the default, and after leaving a server) means the
 * server does not restrict anything and the local YACL config alone decides.
 * Restrictions always override the local config — including the deflection
 * bypass for player-owned projectiles — because the server owns the fair-play
 * policy on its own game.
 *
 * Like {@link ClientOwnerCache}, packet wiring lives behind
 * {@link #registerReceivers()} so the plain mask accessors stay loadable from
 * GameTest (server) environments.
 */
public final class ServerTrackingRules {

    private static volatile int disabledOwnerMask = 0;

    private ServerTrackingRules() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(TrackingRulesPayload.ID, (payload, context) ->
                context.client().execute(() -> applyMask(payload.disabledOwnerMask())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    /** Store a new restriction mask (from a packet or a test) and log the effective change. */
    public static void applyMask(int mask) {
        int sanitized = TrackingRules.sanitize(mask);
        if (sanitized == disabledOwnerMask) {
            return;
        }
        disabledOwnerMask = sanitized;
        FireballPredictor.LOGGER.debug("Server tracking restrictions updated: {}", describe(sanitized));
    }

    /** Whether the current server disabled prediction tracking for the given owner. */
    public static boolean isDisabled(ProjectileOwner owner) {
        return TrackingRules.isDisabled(disabledOwnerMask, owner);
    }

    public static int mask() {
        return disabledOwnerMask;
    }

    /** Lift all restrictions (disconnect / server switch / tests). */
    public static void clear() {
        applyMask(0);
    }

    private static String describe(int mask) {
        if (mask == 0) {
            return "none (all \"other\" sources trackable)";
        }
        List<String> names = new ArrayList<>();
        if ((mask & TrackingRules.PLAYER) != 0) {
            names.add("player");
        }
        if ((mask & TrackingRules.DISPENSER) != 0) {
            names.add("dispenser");
        }
        if ((mask & TrackingRules.COMMAND) != 0) {
            names.add("command");
        }
        return "tracking disabled for " + String.join(", ", names) + " projectiles";
    }
}
