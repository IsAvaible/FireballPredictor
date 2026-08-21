package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker.FireballLocationRecord;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
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
        FireballLocationRecord matched = FireballInferenceTracker.findNearbyFireball(explosionPos, 3.0);
        if (matched == null) {
            return;
        }

        Float estimatedBlockPower = null;
        if (affectedBlocks != null && !affectedBlocks.isEmpty()) {
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
            estimatedBlockPower = (float) (dMax / 1.3);
        } else if (blockCount > 0) {
            // Approx power estimate from cubic volume of destroyed block count
            estimatedBlockPower = (float) Math.max(1.0, Math.cbrt(blockCount * 1.5));
        }

        ProjectileOwner owner = matched.owner != null ? matched.owner : ProjectileOwner.UNKNOWN;

        if (radius > 0.0f) {
            // Sanity check: If packet radius claims a large power (e.g. 4.0) but actual block destruction indicates much smaller power, treat packet radius as inflated.
            if (estimatedBlockPower != null && estimatedBlockPower < radius * 0.75f) {
                ClientPowerLookup.recordInferredBlockEstimation(owner, estimatedBlockPower);
            } else {
                ClientPowerLookup.recordInferredPacketRadius(owner, radius);
            }
        } else if (estimatedBlockPower != null) {
            ClientPowerLookup.recordInferredBlockEstimation(owner, estimatedBlockPower);
        }
    }
}

