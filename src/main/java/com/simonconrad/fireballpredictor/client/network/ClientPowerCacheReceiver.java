package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import net.minecraft.client.Minecraft;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?}

public class ClientPowerCacheReceiver {
    public static void handlePayload(FireballPowerPayload payload, Minecraft client) {
        client.execute(() -> ClientPowerCache.put(payload.entityId(), payload.power()));
    }

    public static void onDisconnect() {
        ClientPowerCache.clear();
    }

    //? if fabric {
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(FireballPowerPayload.ID, (payload, context) ->
                handlePayload(payload, context.client()));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
    }
    //?}
}
