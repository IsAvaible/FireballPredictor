package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.network.TrackingRulesPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class ServerTrackingRulesReceiver {

    private ServerTrackingRulesReceiver() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(TrackingRulesPayload.ID, (payload, context) ->
                context.client().execute(() -> ServerTrackingRules.applyMask(payload.disabledOwnerMask())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ServerTrackingRules.clear());
    }
}
