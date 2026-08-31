package com.simonconrad.fireballpredictor.math;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

public class TrajectoryPredictor {

    public record TrajectoryResult(
        List<Vec3d> path,
        List<Vec3d> velocities,
        HitResult hitResult,
        float explosionPower,
        BlockStateSnapshot snapshot,
        boolean isWindCharge,
        boolean isDangerous
    ) {}

    public static PredictionData predict(ExplosiveProjectileEntity fireball, World world) {
        TrajectoryResult result = simulateTrajectory(fireball, world);
        return computePrediction(result, fireball.age);
    }

    public static TrajectoryResult simulateTrajectory(ExplosiveProjectileEntity fireball, World world) {
        Vec3d fireballPos = fireball.getEntityPos();
        Vec3d currentPos = fireballPos;
        Vec3d initialVelocity = fireball.getVelocity();
        Vec3d velocity = initialVelocity;

        Box initialBoundingBox = fireball.getBoundingBox();
        double boxMinXOffset = initialBoundingBox.minX - fireballPos.x;
        double boxMinYOffset = initialBoundingBox.minY - fireballPos.y;
        double boxMinZOffset = initialBoundingBox.minZ - fireballPos.z;
        double boxMaxXOffset = initialBoundingBox.maxX - fireballPos.x;
        double boxMaxYOffset = initialBoundingBox.maxY - fireballPos.y;
        double boxMaxZOffset = initialBoundingBox.maxZ - fireballPos.z;
        
        double accelerationPower = fireball.accelerationPower;
        
        int maxTicks = 200;
        List<Vec3d> path = new ArrayList<>();
        List<Vec3d> velocities = new ArrayList<>();
        path.add(currentPos);
        velocities.add(velocity);
        
        HitResult finalHit = null;
        
        boolean isWindCharge = fireball instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity;
        boolean isDangerous = fireball instanceof WitherSkullEntity skull && skull.isCharged();

        double airDrag = 0.95;
        if (isWindCharge) {
            airDrag = 1.0;
        } else if (isDangerous) {
            airDrag = 0.73;
        }

        double waterDrag = isWindCharge ? 1.0 : 0.8;
        
        for (int i = 0; i < maxTicks; i++) {
            double minX = currentPos.x + boxMinXOffset;
            double minY = currentPos.y + boxMinYOffset;
            double minZ = currentPos.z + boxMinZOffset;
            double maxX = currentPos.x + boxMaxXOffset;
            double maxY = currentPos.y + boxMaxYOffset;
            double maxZ = currentPos.z + boxMaxZOffset;

            double drag = isTouchingWater(world, minX, minY, minZ, maxX, maxY, maxZ) ? waterDrag : airDrag;

            // Apply acceleration to velocity and apply drag BEFORE movement, matching vanilla tick phase
            Vec3d acceleration = velocity.lengthSquared() > 1e-12 ? velocity.normalize().multiply(accelerationPower) : Vec3d.ZERO;
            velocity = velocity.add(acceleration).multiply(drag);

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
            Box currentBox = new Box(minX, minY, minZ, maxX, maxY, maxZ);
            Box box = currentBox.stretch(velocity).expand(1.0);

            EntityHitResult entityHitResult = ProjectileUtil.getEntityCollision(
                world, fireball, currentPos, nextPos, box, 
                entity -> false // Completely ignore entities for trajectory prediction
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
        
        return new TrajectoryResult(path, velocities, finalHit, explosionPower, snapshot, isWindCharge, isDangerous);
    }

    public static PredictionData computePrediction(TrajectoryResult result, int predictionAge) {
        List<BlockPos> brokenBlocks = new ArrayList<>();
        if (result.hitResult != null && result.explosionPower > 0.0f && result.snapshot != null) {
            brokenBlocks = ImpactPredictor.predictBrokenBlocks(result.explosionPower, result.isWindCharge, result.isDangerous, result.hitResult.getPos(), result.snapshot);
        }
        
        PredictionRenderData renderData = createRenderData(result.path, result.explosionPower);
        Vec3d initialVelocity = result.velocities.isEmpty() ? Vec3d.ZERO : result.velocities.get(0);
        
        return new PredictionData(result.path, result.velocities, result.hitResult, brokenBlocks, initialVelocity, renderData, predictionAge);
    }

    public static PredictionData computePrediction(ExplosiveProjectileEntity fireball, TrajectoryResult result, int predictionAge) {
        return computePrediction(result, predictionAge);
    }

    private static PredictionRenderData createRenderData(List<Vec3d> path, float explosionPower) {
        if (explosionPower <= 0.0f) {
            return PredictionRenderData.EMPTY;
        }

        List<PredictionRenderData.DomeQuad> domeQuads = new ArrayList<>(24 * 24 + 128);
        float radius = explosionPower * 2.0f;
        int latitudeBands = 20;
        int longitudeBands = 24;

        for (int lat = 0; lat < latitudeBands; lat++) {
            float theta1 = (float) (lat * Math.PI / latitudeBands);
            float theta2 = (float) ((lat + 1) * Math.PI / latitudeBands);

            float sinTheta1 = (float) Math.sin(theta1);
            float cosTheta1 = (float) Math.cos(theta1);
            float sinTheta2 = (float) Math.sin(theta2);
            float cosTheta2 = (float) Math.cos(theta2);

            float h1 = (float) lat / latitudeBands;
            float h2 = (float) (lat + 1) / latitudeBands;
            // sin(pi*h): 0 at ground & apex, 1 at the equator -> bright rim, soft poles.
            float profile1 = 0.0f + 0.70f * (float) Math.sin(Math.PI * h1);
            float profile2 = 0.0f + 0.70f * (float) Math.sin(Math.PI * h2);
            int alpha1 = (int) (82 * profile1);
            int alpha2 = (int) (82 * profile2);

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

    public static boolean isTouchingWater(BlockView world, Box box) {
        return isTouchingWater(world, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static boolean isTouchingWater(BlockView world, double boxMinX, double boxMinY, double boxMinZ, double boxMaxX, double boxMaxY, double boxMaxZ) {
        int minX = MathHelper.floor(boxMinX);
        int maxX = MathHelper.ceil(boxMaxX);
        int minY = MathHelper.floor(boxMinY);
        int maxY = MathHelper.ceil(boxMaxY);
        int minZ = MathHelper.floor(boxMinZ);
        int maxZ = MathHelper.ceil(boxMaxZ);

        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState fluidState = world.getFluidState(pos);
                    if (fluidState.isIn(FluidTags.WATER)) {
                        double fluidHeight = (double) y + fluidState.getHeight(world, pos);
                        if (fluidHeight >= boxMinY) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
