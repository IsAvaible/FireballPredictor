package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPowerCache {
    public static final Map<Integer, Float> POWER_CACHE = new ConcurrentHashMap<>();

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(FireballPowerPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                POWER_CACHE.put(payload.entityId(), payload.power());
            });
        });
    }
}
