package com.simonconrad.fireballpredictor.client.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ExplosionInferenceHandler {

    /**
     * Called when an explosion packet is received on the client.
     * Matches the explosion location against recently active fireballs and updates the inferred power.
     */
    public static void onExplosion(Vec3 explosionPos, float radius) {
        onExplosion(explosionPos, radius, 0, null);
    }

    public static void onExplosion(Vec3 explosionPos, float radius, List<BlockPos> affectedBlocks) {
        onExplosion(explosionPos, radius, affectedBlocks != null ? affectedBlocks.size() : 0, affectedBlocks);
    }

    public static void onExplosion(Vec3 explosionPos, float radius, int blockCount, List<BlockPos> affectedBlocks) {
        if (!FireballInferenceTracker.hasFireballNear(explosionPos, 3.0)) {
            return;
        }

        if (radius > 0.0f) {
            ClientPowerLookup.setInferredPacketRadius(radius);
        } else if (affectedBlocks != null && !affectedBlocks.isEmpty()) {
            double maxDistSq = 0.0;
            for (BlockPos pos : affectedBlocks) {
                Vec3 blockCenter = Vec3.atCenterOf(pos);
                double distSq = explosionPos.distanceToSqr(blockCenter);
                if (distSq > maxDistSq) {
                    maxDistSq = distSq;
                }
            }
            double dMax = Math.sqrt(maxDistSq);
            // Ray power attenuation scaling factor is ~1.3 in open air
            float estimatedPower = (float) (dMax / 1.3);
            ClientPowerLookup.updateInferredBlockEstimation(estimatedPower);
        } else if (blockCount > 0) {
            // Approx power estimate from cubic volume of destroyed block count
            float estimatedPower = (float) Math.max(1.0, Math.cbrt(blockCount * 1.5));
            ClientPowerLookup.updateInferredBlockEstimation(estimatedPower);
        }
    }
}

