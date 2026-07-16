package com.simonconrad.fireballpredictor.math;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class PredictionData {
    public final List<Vec3> path;
    public final List<Vec3> velocities;
    public final HitResult hitResult;
    public final List<BlockPos> brokenBlocks;
    public final Vec3 initialVelocity;
    public final PredictionRenderData renderData;
    public final int predictionAge;

    public PredictionData(List<Vec3> path, List<Vec3> velocities, HitResult hitResult, List<BlockPos> brokenBlocks, Vec3 initialVelocity, PredictionRenderData renderData, int predictionAge) {
        this.path = path;
        this.velocities = velocities;
        this.hitResult = hitResult;
        this.brokenBlocks = brokenBlocks;
        this.initialVelocity = initialVelocity;
        this.renderData = renderData;
        this.predictionAge = predictionAge;
    }
}
