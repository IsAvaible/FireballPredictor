package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.network.FireballOwnerPayload;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.level.Level;

import java.util.function.IntConsumer;

@Environment(EnvType.CLIENT)
public final class ClientOwnerCacheReceiver {

    private ClientOwnerCacheReceiver() {
    }

    public static void registerReceivers() {
        OwnerInferenceEngine.setPacketLookup(ClientOwnerCache::get);

        ClientPlayNetworking.registerGlobalReceiver(FireballOwnerPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                Level level = context.client().level;
                if (level == null) {
                    return;
                }

                ProjectileOwner owner = ProjectileOwner.fromOrdinalClamped(payload.ownerType());
                InferenceResult result = OwnerInferenceEngine.fromPacket(level, owner, payload.ownerEntityId());
                if (result != null) {
                    ClientOwnerCache.put(payload.entityId(), result);

                    IntConsumer listener = ClientOwnerCache.getUpdateListener();
                    if (listener != null) {
                        listener.accept(payload.entityId());
                    }
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientOwnerCache.clear());
    }
}
