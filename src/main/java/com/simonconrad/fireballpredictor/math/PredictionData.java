package com.simonconrad.fireballpredictor.math;

import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class PredictionData {
    public final List<Vec3d> path;
    public final List<Vec3d> velocities;
    public final HitResult hitResult;
    public final List<BlockPos> brokenBlocks;
    public final Vec3d initialVelocity;
    public final PredictionRenderData renderData;
    public final int predictionAge;

    public PredictionData(List<Vec3d> path, List<Vec3d> velocities, HitResult hitResult, List<BlockPos> brokenBlocks, Vec3d initialVelocity, PredictionRenderData renderData, int predictionAge) {
        this.path = path;
        this.velocities = velocities;
        this.hitResult = hitResult;
        this.brokenBlocks = brokenBlocks;
        this.initialVelocity = initialVelocity;
        this.renderData = renderData;
        this.predictionAge = predictionAge;
    }
}
