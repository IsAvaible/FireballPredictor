package com.simonconrad.fireballpredictor.math;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public record PredictionData(
        List<Vec3> path,
        List<Vec3> velocities,
        HitResult hitResult,
        HitResult damageHitResult,
        List<BlockPos> brokenBlocks,
        Vec3 initialVelocity,
        PredictionRenderData renderData,
        int predictionAge
) {
    public PredictionData(List<Vec3> path, List<Vec3> velocities, HitResult hitResult, List<BlockPos> brokenBlocks, Vec3 initialVelocity, PredictionRenderData renderData, int predictionAge) {
        this(path, velocities, hitResult, hitResult, brokenBlocks, initialVelocity, renderData, predictionAge);
    }
}
