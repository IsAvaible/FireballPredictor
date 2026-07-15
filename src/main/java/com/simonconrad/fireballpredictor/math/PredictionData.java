package com.simonconrad.fireballpredictor.math;

import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class PredictionData {
    public final List<Vec3d> path;
    public final HitResult hitResult;
    public final List<BlockPos> brokenBlocks;
    public final Vec3d initialVelocity;

    public PredictionData(List<Vec3d> path, HitResult hitResult, List<BlockPos> brokenBlocks, Vec3d initialVelocity) {
        this.path = path;
        this.hitResult = hitResult;
        this.brokenBlocks = brokenBlocks;
        this.initialVelocity = initialVelocity;
    }
}
