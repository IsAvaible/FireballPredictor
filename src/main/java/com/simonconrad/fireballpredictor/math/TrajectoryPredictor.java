package com.simonconrad.fireballpredictor.math;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.entity.projectile.ProjectileUtil;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class TrajectoryPredictor {

    public record TrajectoryResult(
        List<Vec3d> path,
        List<Vec3d> velocities,
        HitResult hitResult,
        float explosionPower,
        BlockStateSnapshot snapshot
    ) {}

    public static PredictionData predict(ExplosiveProjectileEntity fireball, World world) {
        TrajectoryResult result = simulateTrajectory(fireball, world);
        return computePrediction(fireball, result, fireball.age);
    }

    public static TrajectoryResult simulateTrajectory(ExplosiveProjectileEntity fireball, World world) {
        Vec3d currentPos = fireball.getEntityPos();
        Vec3d initialVelocity = fireball.getVelocity();
        Vec3d velocity = initialVelocity;
        
        double accelerationPower = fireball.accelerationPower;
        Vec3d acceleration = velocity.normalize().multiply(accelerationPower);
        
        int maxTicks = 200;
        List<Vec3d> path = new ArrayList<>();
        List<Vec3d> velocities = new ArrayList<>();
        path.add(currentPos);
        velocities.add(velocity);
        
        HitResult finalHit = null;
        
        double drag = 0.95;
        if (fireball instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity) {
            drag = 1.0;
        } else if (fireball instanceof net.minecraft.entity.projectile.WitherSkullEntity skull && skull.isCharged()) {
            drag = 0.73;
        }
        
        for (int i = 0; i < maxTicks; i++) {
            Vec3d nextPos = currentPos.add(velocity);
            
            // Raycast for blocks
            HitResult hitResult = world.raycast(new RaycastContext(
                currentPos, 
                nextPos, 
                RaycastContext.ShapeType.COLLIDER, 
                RaycastContext.FluidHandling.NONE, 
                fireball
            ));
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                nextPos = hitResult.getPos();
            }
            
            // Raycast for entities
            // Calculate the box at the simulated current position
            Vec3d offset = currentPos.subtract(fireball.getEntityPos());
            Box currentBox = fireball.getBoundingBox().offset(offset);
            Box box = currentBox.stretch(velocity).expand(1.0);


            EntityHitResult entityHitResult = ProjectileUtil.getEntityCollision(
                world, fireball, currentPos, nextPos, box, 
                entity -> false // Completely ignore entities for trajectory prediction
                // entity -> !entity.isSpectator() && entity.canHit()
            );
            
            if (entityHitResult != null) {
                hitResult = entityHitResult;
            }
            
            if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
                path.add(hitResult.getPos());
                velocities.add(velocity);
                finalHit = hitResult;
                break;
            }
            
            currentPos = nextPos;
            path.add(currentPos);
            
            // Add acceleration to velocity and apply drag
            velocity = velocity.add(acceleration).multiply(drag);
            velocities.add(velocity);
        }
        
        float explosionPower = finalHit != null ? ImpactPredictor.resolveExplosionPower(fireball) : 0.0f;
        BlockStateSnapshot snapshot = null;
        if (finalHit != null && explosionPower > 0.0f) {
            Vec3d hitPos = finalHit.getPos();
            float radius = explosionPower * 2.0f;
            BlockPos minPos = BlockPos.ofFloored(hitPos.x - radius - 2, hitPos.y - radius - 2, hitPos.z - radius - 2);
            BlockPos maxPos = BlockPos.ofFloored(hitPos.x + radius + 2, hitPos.y + radius + 2, hitPos.z + radius + 2);
            snapshot = new BlockStateSnapshot(world, minPos, maxPos);
        }
        
        return new TrajectoryResult(path, velocities, finalHit, explosionPower, snapshot);
    }

    public static PredictionData computePrediction(ExplosiveProjectileEntity fireball, TrajectoryResult result, int predictionAge) {
        List<BlockPos> brokenBlocks = new ArrayList<>();
        if (result.hitResult != null && result.explosionPower > 0.0f && result.snapshot != null) {
            brokenBlocks = ImpactPredictor.predictBrokenBlocks(fireball, result.hitResult.getPos(), result.snapshot);
        }
        
        PredictionRenderData renderData = createRenderData(result.path, result.explosionPower);
        Vec3d initialVelocity = result.velocities.isEmpty() ? Vec3d.ZERO : result.velocities.get(0);
        
        return new PredictionData(result.path, result.velocities, result.hitResult, brokenBlocks, initialVelocity, renderData, predictionAge);
    }

    private static PredictionRenderData createRenderData(List<Vec3d> path, float explosionPower) {
        if (explosionPower <= 0.0f) {
            return PredictionRenderData.EMPTY;
        }

        List<PredictionRenderData.DomeQuad> domeQuads = new ArrayList<>(16 * 16);
        float radius = explosionPower * 2.0f;
        int latitudeBands = 16;
        int longitudeBands = 16;

        for (int lat = 0; lat < latitudeBands; lat++) {
            float theta1 = (float) (lat * Math.PI / latitudeBands);
            float theta2 = (float) ((lat + 1) * Math.PI / latitudeBands);

            float sinTheta1 = (float) Math.sin(theta1);
            float cosTheta1 = (float) Math.cos(theta1);
            float sinTheta2 = (float) Math.sin(theta2);
            float cosTheta2 = (float) Math.cos(theta2);

            int alpha1 = (int) (60 * (1.0f - Math.abs((float) lat / latitudeBands - 0.5f) * 2));
            int alpha2 = (int) (60 * (1.0f - Math.abs((float) (lat + 1) / latitudeBands - 0.5f) * 2));

            for (int lon = 0; lon < longitudeBands; lon++) {
                float phi1 = (float) (lon * 2 * Math.PI / longitudeBands);
                float phi2 = (float) ((lon + 1) * 2 * Math.PI / longitudeBands);

                float sinPhi1 = (float) Math.sin(phi1);
                float cosPhi1 = (float) Math.cos(phi1);
                float sinPhi2 = (float) Math.sin(phi2);
                float cosPhi2 = (float) Math.cos(phi2);

                Vec3d p1 = new Vec3d(radius * cosPhi1 * cosTheta1, radius * sinTheta1, radius * sinPhi1 * cosTheta1);
                Vec3d p2 = new Vec3d(radius * cosPhi2 * cosTheta1, radius * sinTheta1, radius * sinPhi2 * cosTheta1);
                Vec3d p3 = new Vec3d(radius * cosPhi2 * cosTheta2, radius * sinTheta2, radius * sinPhi2 * cosTheta2);
                Vec3d p4 = new Vec3d(radius * cosPhi1 * cosTheta2, radius * sinTheta2, radius * sinPhi1 * cosTheta2);

                domeQuads.add(new PredictionRenderData.DomeQuad(p1, p2, p3, p4, alpha1, alpha2));
            }
        }

        return new PredictionRenderData(domeQuads);
    }
}
