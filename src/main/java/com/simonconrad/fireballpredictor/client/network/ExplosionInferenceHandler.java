package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker.FireballLocationRecord;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ExplosionInferenceHandler {

    /**
     * Called when an explosion packet is received on the client.
     * Matches the explosion location against recently active fireballs and updates the inferred power.
     */
    public static void onExplosion(Vec3 explosionPos, float radius) {
        onExplosion(explosionPos, radius, 0, null, null);
    }

    public static void onExplosion(Vec3 explosionPos, float radius, int blockCount) {
        onExplosion(explosionPos, radius, blockCount, null, null);
    }

    /**
     * Overload for test harnesses / compatibility where affected block positions are explicitly provided.
     * In Minecraft 1.21+, vanilla ClientboundExplodePacket only carries blockCount, so affectedBlocks is null in production.
     */
    public static void onExplosion(Vec3 explosionPos, float radius, List<BlockPos> affectedBlocks) {
        onExplosion(explosionPos, radius, affectedBlocks != null ? affectedBlocks.size() : 0, affectedBlocks, null);
    }

    public static void onExplosion(Vec3 explosionPos, float radius, int blockCount, List<BlockPos> affectedBlocks) {
        onExplosion(explosionPos, radius, blockCount, affectedBlocks, null);
    }

    public static void onExplosion(Vec3 explosionPos, float radius, int blockCount, List<BlockPos> affectedBlocks, BlockGetter world) {
        FireballLocationRecord matched = FireballInferenceTracker.consumeNearbyFireball(explosionPos, 3.0);
        if (matched == null) {
            return;
        }

        if (world == null && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            world = getClientLevel();
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
            float normalizedBlockCount = normalizeBlockCount(explosionPos, blockCount, world);
            // Approx power estimate from cubic volume of destroyed block count
            estimatedBlockPower = (float) Math.max(1.0, Math.cbrt(normalizedBlockCount * 1.5));
        }

        ProjectileOwner owner = matched.owner != null ? matched.owner : ProjectileOwner.UNKNOWN;

        if (radius > 0.0f) {
            // Check if packet radius diverges significantly from observable block destruction
            // (e.g. anti-cheat servers sending inflated 4.0 or deflated 1.0/0.5 dummy radii).
            boolean diverges = estimatedBlockPower != null
                    && (estimatedBlockPower < radius * 0.75f || estimatedBlockPower > radius * 1.35f);
            if (diverges) {
                ClientPowerLookup.recordInferredBlockEstimation(owner, estimatedBlockPower);
            } else {
                ClientPowerLookup.recordInferredPacketRadius(owner, radius);
            }
        } else if (estimatedBlockPower != null && estimatedBlockPower > 0.0f) {
            ClientPowerLookup.recordInferredBlockEstimation(owner, estimatedBlockPower);
        }
    }

    /**
     * Normalizes the destroyed block count based on surrounding solid terrain geometry and blast resistance.
     */
    public static float normalizeBlockCount(Vec3 explosionPos, int blockCount, BlockGetter world) {
        if (world == null || blockCount <= 0) {
            return (float) blockCount;
        }

        BlockPos centerPos = BlockPos.containing(explosionPos);
        int solidCount = 0;
        float totalResistance = 0.0f;
        int totalSampled = 0;

        // Sample 3x3x3 neighborhood around impact point
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    totalSampled++;
                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    FluidState fluid = world.getFluidState(pos);

                    float resistance = Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance());
                    if (!state.isAir() || !fluid.isEmpty()) {
                        solidCount++;
                        // Clamp individual block blast resistance to prevent indestructible/extreme blocks from distorting the estimate
                        totalResistance += Math.min(15.0f, Math.max(0.5f, resistance));
                    }
                }
            }
        }

        if (solidCount == 0) {
            return (float) blockCount;
        }

        float solidRatio = (float) solidCount / (float) totalSampled;
        // Baseline solid ratio for flat ground is ~0.50 (hemisphere)
        float geometryFactor = 0.50f / Math.max(0.15f, Math.min(1.0f, solidRatio));

        float avgResistance = totalResistance / (float) solidCount;
        // Baseline blast resistance is ~0.8 (wool standard in Bedwars / minigames)
        float resistanceFactor = (float) Math.pow((avgResistance + 0.3f) / (0.8f + 0.3f), 0.6);

        return blockCount * geometryFactor * resistanceFactor;
    }

    private static BlockGetter getClientLevel() {
        try {
            return ClientLevelGetter.getLevel();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static class ClientLevelGetter {
        private static BlockGetter getLevel() {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            return client != null ? client.level : null;
        }
    }
}

