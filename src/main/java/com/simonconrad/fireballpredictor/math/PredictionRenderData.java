package com.simonconrad.fireballpredictor.math;

import java.util.List;
import net.minecraft.world.phys.Vec3;

public record PredictionRenderData(List<DomeQuad> domeQuads) {
    public static final PredictionRenderData EMPTY = new PredictionRenderData(List.of());

    public record DomeQuad(Vec3 p1, Vec3 p2, Vec3 p3, Vec3 p4, int alpha1, int alpha2) {
    }
}