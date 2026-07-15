package com.simonconrad.fireballpredictor.math;

import net.minecraft.util.math.Vec3d;

import java.util.List;

public record PredictionRenderData(List<DomeQuad> domeQuads) {
    public static final PredictionRenderData EMPTY = new PredictionRenderData(List.of());

    public record DomeQuad(Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4, int alpha1, int alpha2) {
    }
}