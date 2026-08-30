package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.network.MobGriefingPayload;
import com.simonconrad.fireballpredictor.tracking.MobGriefingState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public final class MobGriefingReceiver {

    private MobGriefingReceiver() {
    }

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MobGriefingPayload.ID, (payload, context) ->
                context.client().execute(() -> MobGriefingState.setMobGriefing(payload.mobGriefing())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> MobGriefingState.clear());
    }
}
