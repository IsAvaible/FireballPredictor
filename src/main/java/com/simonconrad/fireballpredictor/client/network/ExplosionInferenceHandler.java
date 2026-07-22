package com.simonconrad.fireballpredictor.client.network;

import net.minecraft.util.math.Vec3d;

public class ExplosionInferenceHandler {

    /**
     * Called when an explosion packet is received on the client.
     * Matches the explosion location against recently active fireballs and updates the inferred power.
     */
    public static void onExplosion(Vec3d explosionPos, float radius) {
        if (radius <= 0.0f) {
            return;
        }

        if (FireballInferenceTracker.hasFireballNear(explosionPos, 3.0)) {
            ClientPowerLookup.setInferredFireballPower(radius);
        }
    }
}
