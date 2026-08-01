package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;

import com.simonconrad.fireballpredictor.network.FireballOwnerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;

/**
 * Client-side cache of owner data delivered by {@link FireballOwnerPayload}.
 * Also notifies listeners so in-flight tracked projectiles can upgrade from
 * environmental inference to authoritative packet data.
 *
 * <p>Avoids hard references to client-only types in field signatures so the
 * class can be loaded from GameTest (server) environments when only the
 * cache map is touched.
 */
public final class ClientOwnerCache {

    private static final Map<Integer, InferenceResult> OWNER_CACHE = new ConcurrentHashMap<>();

    /** Optional hook invoked on the client thread when a packet updates an entity's owner. */
    private static volatile IntConsumer updateListener;

    private ClientOwnerCache() {
    }

    public static void registerReceivers() {
        // Wire packet tier without a hard class-init dependency the other way
        OwnerInferenceEngine.setPacketLookup(ClientOwnerCache::get);

        ClientPlayNetworking.registerGlobalReceiver(FireballOwnerPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Level level = context.client().level;
                ProjectileOwner owner = ProjectileOwner.fromOrdinalClamped(payload.ownerType());
                InferenceResult result = OwnerInferenceEngine.fromPacket(level, owner, payload.ownerEntityId());
                OWNER_CACHE.put(payload.entityId(), result);

                IntConsumer listener = updateListener;
                if (listener != null) {
                    listener.accept(payload.entityId());
                }
            });
        });
    }

    public static void setUpdateListener(@Nullable IntConsumer listener) {
        updateListener = listener;
    }

    @Nullable
    public static InferenceResult get(int entityId) {
        return OWNER_CACHE.get(entityId);
    }

    public static void put(int entityId, InferenceResult result) {
        if (result != null) {
            OWNER_CACHE.put(entityId, result);
        }
    }

    public static void remove(int entityId) {
        OWNER_CACHE.remove(entityId);
    }

    public static void clear() {
        OWNER_CACHE.clear();
    }
}
