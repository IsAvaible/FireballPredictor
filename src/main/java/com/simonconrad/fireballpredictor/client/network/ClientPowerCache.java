package com.simonconrad.fireballpredictor.client.network;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of power data delivered by {@link com.simonconrad.fireballpredictor.network.FireballPowerPayload}.
 *
 * <p>Pure cache storage kept side-safe from client networking types; cleared on disconnect.
 */
public final class ClientPowerCache {

    private static final Map<Integer, Float> POWER_CACHE = new ConcurrentHashMap<>();

    private ClientPowerCache() {
    }

    @Nullable
    public static Float get(int entityId) {
        return POWER_CACHE.get(entityId);
    }

    public static void put(int entityId, float power) {
        POWER_CACHE.put(entityId, power);
    }

    public static void remove(int entityId) {
        POWER_CACHE.remove(entityId);
    }

    public static void clear() {
        POWER_CACHE.clear();
    }

    public static boolean containsKey(int entityId) {
        return POWER_CACHE.containsKey(entityId);
    }
}

