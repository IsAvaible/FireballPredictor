package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker.FireballLocationRecord;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.platform.PlatformHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ExplosionInferenceHandler {

    public static final float SUSPICIOUS_INFLATED_RADIUS_THRESHOLD = 3.75f;

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

        if (world == null && PlatformHelper.isClient()) {
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
            // Asymmetric Sanity Check:
            // 1. Upward divergence: block destruction implies far greater power than packet radius claims.
            // 2. Downward divergence: only override radius downward if radius itself is suspiciously inflated
            //    (e.g. anti-cheat servers sending dummy radius >= 4.0), not when normal radii (<= 3.75) break few
            //    blocks due to minigame map protection, spawn protection, or bed defenses.
            boolean brokenFarMore = estimatedBlockPower != null && estimatedBlockPower > radius * 1.35f;
            boolean suspiciouslyInflated = estimatedBlockPower != null
                    && estimatedBlockPower < radius * 0.75f
                    && radius > SUSPICIOUS_INFLATED_RADIUS_THRESHOLD;

            if (brokenFarMore || suspiciouslyInflated) {
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
     * Uses a 5x5x5 neighborhood with inverse-distance weighting to penetrate multi-layer bed defenses.
     */
    public static float normalizeBlockCount(Vec3 explosionPos, int blockCount, BlockGetter world) {
        if (world == null || blockCount <= 0) {
            return (float) blockCount;
        }

        BlockPos centerPos = BlockPos.containing(explosionPos);
        float solidWeight = 0.0f;
        float totalResistance = 0.0f;
        float totalSampledWeight = 0.0f;

        // Sample 5x5x5 neighborhood around impact point with inverse-distance weighting
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float weight = (float) (1.0 / (1.0 + dist));
                    totalSampledWeight += weight;

                    BlockPos pos = centerPos.offset(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    FluidState fluid = world.getFluidState(pos);

                    float resistance = Math.max(state.getBlock().getExplosionResistance(), fluid.getExplosionResistance());
                    if (!state.isAir() || !fluid.isEmpty()) {
                        solidWeight += weight;
                        // Clamp individual block blast resistance to prevent indestructible/extreme blocks from distorting the estimate
                        totalResistance += weight * Math.min(15.0f, Math.max(0.5f, resistance));
                    }
                }
            }
        }

        if (solidWeight <= 0.0f) {
            return (float) blockCount;
        }

        float solidRatio = solidWeight / totalSampledWeight;
        // Baseline solid ratio for flat ground is ~0.50 (hemisphere)
        float geometryFactor = 0.50f / Math.max(0.15f, Math.min(1.0f, solidRatio));

        float avgResistance = totalResistance / solidWeight;
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

