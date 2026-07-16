package com.simonconrad.fireballpredictor.math;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TrajectoryPredictor {

    public record TrajectoryResult(
        List<Vec3> path,
        List<Vec3> velocities,
        HitResult hitResult,
        float explosionPower,
        BlockStateSnapshot snapshot
    ) {}

    public static PredictionData predict(AbstractHurtingProjectile fireball, Level world) {
        TrajectoryResult result = simulateTrajectory(fireball, world);
        return computePrediction(fireball, result, fireball.tickCount);
    }

    public static TrajectoryResult simulateTrajectory(AbstractHurtingProjectile fireball, Level world) {
        Vec3 currentPos = fireball.position();
        Vec3 initialVelocity = fireball.getDeltaMovement();
        Vec3 velocity = initialVelocity;
        
        double accelerationPower = fireball.accelerationPower;
        Vec3 acceleration = velocity.normalize().scale(accelerationPower);
        
        int maxTicks = 200;
        List<Vec3> path = new ArrayList<>();
        List<Vec3> velocities = new ArrayList<>();
        path.add(currentPos);
        velocities.add(velocity);
        
        HitResult finalHit = null;
        
        for (int i = 0; i < maxTicks; i++) {
            Vec3 nextPos = currentPos.add(velocity);
            
            // Raycast for blocks
            HitResult hitResult = world.clip(new ClipContext(
                currentPos, 
                nextPos, 
                ClipContext.Block.COLLIDER, 
                ClipContext.Fluid.NONE, 
                fireball
            ));
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                nextPos = hitResult.getLocation();
            }
            
            // Raycast for entities
            // Calculate the box at the simulated current position
            Vec3 offset = currentPos.subtract(fireball.position());
            AABB currentBox = fireball.getBoundingBox().move(offset);
            AABB box = currentBox.expandTowards(velocity).inflate(1.0);


            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                world, fireball, currentPos, nextPos, box, 
                entity -> false // Completely ignore entities for trajectory prediction
                // entity -> !entity.isSpectator() && entity.canHit()
            );
            
            if (entityHitResult != null) {
                hitResult = entityHitResult;
            }
            
            if (hitResult != null && hitResult.getType() != HitResult.Type.MISS) {
                path.add(hitResult.getLocation());
                velocities.add(velocity);
                finalHit = hitResult;
                break;
            }
            
            currentPos = nextPos;
            path.add(currentPos);
            
            // Add acceleration to velocity and apply drag (usually 0.95)
            velocity = velocity.add(acceleration).scale(0.95);
            velocities.add(velocity);
        }
        
        float explosionPower = finalHit != null ? ImpactPredictor.resolveExplosionPower(fireball) : 0.0f;
        BlockStateSnapshot snapshot = null;
        if (finalHit != null && explosionPower > 0.0f) {
            Vec3 hitPos = finalHit.getLocation();
            float radius = explosionPower * 2.0f;
            BlockPos minPos = BlockPos.containing(hitPos.x - radius - 2, hitPos.y - radius - 2, hitPos.z - radius - 2);
            BlockPos maxPos = BlockPos.containing(hitPos.x + radius + 2, hitPos.y + radius + 2, hitPos.z + radius + 2);
            snapshot = new BlockStateSnapshot(world, minPos, maxPos);
        }
        
        return new TrajectoryResult(path, velocities, finalHit, explosionPower, snapshot);
    }

    public static PredictionData computePrediction(AbstractHurtingProjectile fireball, TrajectoryResult result, int predictionAge) {
        List<BlockPos> brokenBlocks = new ArrayList<>();
        if (result.hitResult != null && result.explosionPower > 0.0f && result.snapshot != null) {
            brokenBlocks = ImpactPredictor.predictBrokenBlocks(fireball, result.hitResult.getLocation(), result.snapshot);
        }
        
        PredictionRenderData renderData = createRenderData(result.path, result.explosionPower);
        Vec3 initialVelocity = result.velocities.isEmpty() ? Vec3.ZERO : result.velocities.get(0);
        
        return new PredictionData(result.path, result.velocities, result.hitResult, brokenBlocks, initialVelocity, renderData, predictionAge);
    }

    private static PredictionRenderData createRenderData(List<Vec3> path, float explosionPower) {
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

                Vec3 p1 = new Vec3(radius * cosPhi1 * cosTheta1, radius * sinTheta1, radius * sinPhi1 * cosTheta1);
                Vec3 p2 = new Vec3(radius * cosPhi2 * cosTheta1, radius * sinTheta1, radius * sinPhi2 * cosTheta1);
                Vec3 p3 = new Vec3(radius * cosPhi2 * cosTheta2, radius * sinTheta2, radius * sinPhi2 * cosTheta2);
                Vec3 p4 = new Vec3(radius * cosPhi1 * cosTheta2, radius * sinTheta2, radius * sinPhi1 * cosTheta2);

                domeQuads.add(new PredictionRenderData.DomeQuad(p1, p2, p3, p4, alpha1, alpha2));
            }
        }

        return new PredictionRenderData(domeQuads);
    }
}
