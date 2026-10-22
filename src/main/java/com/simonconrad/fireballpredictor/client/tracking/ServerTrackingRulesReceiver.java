package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.network.TrackingRulesPayload;
import net.minecraft.client.Minecraft;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?}

public final class ServerTrackingRulesReceiver {

    private ServerTrackingRulesReceiver() {
    }

    public static void handlePayload(TrackingRulesPayload payload, Minecraft client) {
        client.execute(() -> ServerTrackingRules.applyMask(payload.disabledOwnerMask()));
    }

    public static void onDisconnect() {
        ServerTrackingRules.clear();
    }

    //? if fabric {
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(TrackingRulesPayload.ID, (payload, context) ->
                handlePayload(payload, context.client()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
    }
    //?}
}
