package com.simonconrad.fireballpredictor.tracking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Side-agnostic holder for the active {@code mobGriefing} gamerule.
 *
 * <p>On the server, the gamerule is read directly from the {@link ServerLevel}.
 * On the client, the value received from {@code MobGriefingPayload} is returned.
 * Defaults to {@code true} (vanilla default).
 */
public final class MobGriefingState {

    private static volatile boolean mobGriefing = true;
    private static volatile Boolean testOverride = null;

    private MobGriefingState() {
    }

    public static boolean isMobGriefingEnabled() {
        if (testOverride != null) {
            return testOverride;
        }
        return mobGriefing;
    }

    public static boolean isMobGriefingEnabled(Level level) {
        if (testOverride != null) {
            return testOverride;
        }
        if (level != null && !level.isClientSide() && level instanceof ServerLevel serverLevel) {
            return serverLevel.getGameRules().get(GameRules.MOB_GRIEFING);
        }
        return mobGriefing;
    }

    public static void setMobGriefing(boolean enabled) {
        mobGriefing = enabled;
    }

    public static void setTestOverride(Boolean override) {
        testOverride = override;
    }

    public static void clear() {
        mobGriefing = true;
        testOverride = null;
    }
}
