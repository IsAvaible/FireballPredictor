package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

/**
 * Client-side cache of owner data delivered by {@link com.simonconrad.fireballpredictor.network.FireballOwnerPayload}.
 * Also notifies listeners so in-flight tracked projectiles can upgrade from
 * environmental inference to authoritative packet data.
 *
 * <p>Contains NO references to client-only types in method signatures or bytecodes
 * so the class can be loaded in server or headless environments. Packet receiver
 * registration is handled in {@link ClientOwnerCacheReceiver}.
 */
public final class ClientOwnerCache {

    private static final Map<Integer, InferenceResult> OWNER_CACHE = new ConcurrentHashMap<>();

    /** Optional hook invoked on the client thread when a packet updates an entity's owner. */
    private static volatile IntConsumer updateListener;

    private ClientOwnerCache() {
    }

    public static void setUpdateListener(@Nullable IntConsumer listener) {
        updateListener = listener;
    }

    @Nullable
    public static IntConsumer getUpdateListener() {
        return updateListener;
    }

    @Nullable
    public static InferenceResult get(int entityId) {
        return OWNER_CACHE.get(entityId);
    }

    public static void put(int entityId, @Nullable InferenceResult result) {
        if (result != null) {
            OWNER_CACHE.put(entityId, result);
        } else {
            OWNER_CACHE.remove(entityId);
        }
    }

    public static void remove(int entityId) {
        OWNER_CACHE.remove(entityId);
    }

    public static void clear() {
        OWNER_CACHE.clear();
    }
}
