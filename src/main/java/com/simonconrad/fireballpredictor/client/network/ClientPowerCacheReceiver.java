package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class ClientPowerCacheReceiver {
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(FireballPowerPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                ClientPowerCache.put(payload.entityId(), payload.power());
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientPowerCache.clear());
    }
}

