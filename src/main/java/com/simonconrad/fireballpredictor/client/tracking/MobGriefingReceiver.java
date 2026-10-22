package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.network.MobGriefingPayload;
import com.simonconrad.fireballpredictor.tracking.MobGriefingState;
import net.minecraft.client.Minecraft;

//? if fabric {
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//?}

public final class MobGriefingReceiver {

    private MobGriefingReceiver() {
    }

    public static void handlePayload(MobGriefingPayload payload, Minecraft client) {
        client.execute(() -> MobGriefingState.setMobGriefing(payload.mobGriefing()));
    }

    public static void onDisconnect() {
        MobGriefingState.clear();
    }

    //? if fabric {
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MobGriefingPayload.ID, (payload, context) ->
                handlePayload(payload, context.client()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
    }
    //?}
}
