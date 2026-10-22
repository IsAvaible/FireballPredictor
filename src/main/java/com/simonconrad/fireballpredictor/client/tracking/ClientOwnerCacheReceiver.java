package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.network.FireballOwnerPayload;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.function.IntConsumer;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?}

public final class ClientOwnerCacheReceiver {

    private ClientOwnerCacheReceiver() {
    }

    public static void init() {
        OwnerInferenceEngine.setPacketLookup(ClientOwnerCache::get);
    }

    public static void handlePayload(FireballOwnerPayload payload, Minecraft client) {
        client.execute(() -> {
            Level level = client.level;
            if (level == null) {
                return;
            }

            ProjectileOwner owner = ProjectileOwner.fromName(payload.ownerName());
            InferenceResult result = OwnerInferenceEngine.fromPacket(level, owner, payload.ownerEntityId());
            if (result != null) {
                ClientOwnerCache.put(payload.entityId(), result);

                IntConsumer listener = ClientOwnerCache.getUpdateListener();
                if (listener != null) {
                    listener.accept(payload.entityId());
                }
            }
        });
    }

    public static void onDisconnect() {
        ClientOwnerCache.clear();
    }

    //? if fabric {
    public static void registerReceivers() {
        init();

        ClientPlayNetworking.registerGlobalReceiver(FireballOwnerPayload.ID, (payload, context) ->
                handlePayload(payload, context.client()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
    }
    //?}
}
