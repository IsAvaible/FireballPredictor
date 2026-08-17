package com.simonconrad.fireballpredictor.math;

import java.util.ArrayList;
import java.util.List;
import com.simonconrad.fireballpredictor.mixin.ProjectileAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TrajectoryPredictor {

    public record TrajectoryResult(
        List<Vec3> path,
        List<Vec3> velocities,
        HitResult hitResult,
        HitResult damageHitResult,
        float explosionPower,
        BlockStateSnapshot snapshot,
        boolean isWindCharge,
        boolean isDangerous
    ) {
        public TrajectoryResult(
            List<Vec3> path,
            List<Vec3> velocities,
            HitResult hitResult,
            float explosionPower,
            BlockStateSnapshot snapshot,
            boolean isWindCharge,
            boolean isDangerous
        ) {
            this(path, velocities, hitResult, hitResult, explosionPower, snapshot, isWindCharge, isDangerous);
        }
    }

    public static PredictionData predict(AbstractHurtingProjectile fireball, Level world) {
        TrajectoryResult result = simulateTrajectory(fireball, world);
        return computePrediction(result, fireball.tickCount);
    }

    public static TrajectoryResult simulateTrajectory(AbstractHurtingProjectile fireball, Level world) {
        Vec3 fireballPos = fireball.position();
        Vec3 currentPos = fireballPos;
        Vec3 initialVelocity = fireball.getDeltaMovement();
        Vec3 velocity = initialVelocity;

        AABB boundingBox = fireball.getBoundingBox();
        double accelerationPower = fireball.accelerationPower;
        
        int maxTicks = 200;
        List<Vec3> path = new ArrayList<>();
        List<Vec3> velocities = new ArrayList<>();
        path.add(currentPos);
        velocities.add(velocity);
        
        HitResult blockHit = null;
        HitResult firstEntityHit = null;
        
        boolean isWindCharge = fireball instanceof net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
        boolean isDangerous = fireball instanceof WitherSkull skull && skull.isDangerous();

        double airDrag = 0.95;
        if (isWindCharge) {
            airDrag = 1.0;
        } else if (isDangerous) {
            airDrag = 0.73;
        }

        double waterDrag = isWindCharge ? 1.0 : 0.8;
        
        for (int i = 0; i < maxTicks; i++) {
            AABB currentBox = boundingBox.move(currentPos.subtract(fireballPos));
            double drag = isTouchingWater(world, currentBox) ? waterDrag : airDrag;

            // Apply acceleration to velocity and apply drag BEFORE movement, matching vanilla tick phase
            Vec3 acceleration = velocity.lengthSqr() > 1e-12 ? velocity.normalize().scale(accelerationPower) : Vec3.ZERO;
            velocity = velocity.add(acceleration).scale(drag);

            Vec3 nextPos = currentPos.add(velocity);
            
            // Raycast for blocks
            HitResult hitResult = world.clip(new ClipContext(
                currentPos, 
                nextPos, 
                ClipContext.Block.COLLIDER, 
                ClipContext.Fluid.NONE, 
                fireball
            ));
            
            Vec3 entityRayEnd = hitResult.getType() != HitResult.Type.MISS ? hitResult.getLocation() : nextPos;

            // Raycast for entities along this step (only before any block collision)
            if (firstEntityHit == null) {
                AABB box = currentBox.expandTowards(velocity).inflate(1.0);

                EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                    world, fireball, currentPos, entityRayEnd, box, 
                    entity -> canHitEntity(fireball, entity)
                );
                
                if (entityHitResult != null) {
                    firstEntityHit = entityHitResult;
                }
            }
            
            if (hitResult.getType() != HitResult.Type.MISS) {
                path.add(hitResult.getLocation());
                velocities.add(velocity);
                blockHit = hitResult;
                break;
            }
            
            currentPos = nextPos;
            path.add(currentPos);
            velocities.add(velocity);
        }
        
        HitResult hitResult = blockHit != null ? blockHit : firstEntityHit;
        HitResult damageHitResult = firstEntityHit != null ? firstEntityHit : blockHit;

        float explosionPower = (blockHit != null || firstEntityHit != null) ? ImpactPredictor.resolveExplosionPower(fireball) : 0.0f;
        BlockStateSnapshot snapshot = null;
        HitResult snapshotHit = blockHit != null ? blockHit : firstEntityHit;
        if (snapshotHit != null && explosionPower > 0.0f) {
            Vec3 hitPos = snapshotHit.getLocation();
            float radius = explosionPower * 2.0f;
            BlockPos minPos = BlockPos.containing(hitPos.x - radius - 2, hitPos.y - radius - 2, hitPos.z - radius - 2);
            BlockPos maxPos = BlockPos.containing(hitPos.x + radius + 2, hitPos.y + radius + 2, hitPos.z + radius + 2);
            snapshot = new BlockStateSnapshot(world, minPos, maxPos);
        }
        
        return new TrajectoryResult(path, velocities, hitResult, damageHitResult, explosionPower, snapshot, isWindCharge, isDangerous);
    }

    public static PredictionData computePrediction(TrajectoryResult result, int predictionAge) {
        List<BlockPos> brokenBlocks = new ArrayList<>();
        if (result.hitResult != null && result.explosionPower > 0.0f && result.snapshot != null) {
            brokenBlocks = ImpactPredictor.predictBrokenBlocks(result.explosionPower, result.isWindCharge, result.isDangerous, result.hitResult.getLocation(), result.snapshot);
        }
        
        PredictionRenderData renderData = createRenderData(result.path, result.explosionPower);
        Vec3 initialVelocity = result.velocities.isEmpty() ? Vec3.ZERO : result.velocities.get(0);
        
        return new PredictionData(result.path, result.velocities, result.hitResult, result.damageHitResult, brokenBlocks, initialVelocity, renderData, predictionAge);
    }

    private static PredictionRenderData createRenderData(List<Vec3> path, float explosionPower) {
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

                Vec3 p1 = new Vec3(radius * cosPhi1 * cosTheta1, radius * sinTheta1, radius * sinPhi1 * cosTheta1);
                Vec3 p2 = new Vec3(radius * cosPhi2 * cosTheta1, radius * sinTheta1, radius * sinPhi2 * cosTheta1);
                Vec3 p3 = new Vec3(radius * cosPhi2 * cosTheta2, radius * sinTheta2, radius * sinPhi2 * cosTheta2);
                Vec3 p4 = new Vec3(radius * cosPhi1 * cosTheta2, radius * sinTheta2, radius * sinPhi1 * cosTheta2);

                domeQuads.add(new PredictionRenderData.DomeQuad(p1, p2, p3, p4, alpha1, alpha2));
            }
        }

        return new PredictionRenderData(domeQuads);
    }

    public static boolean isTouchingWater(BlockGetter world, AABB box) {
        return isTouchingWater(world, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static boolean isTouchingWater(BlockGetter world, double boxMinX, double boxMinY, double boxMinZ, double boxMaxX, double boxMaxY, double boxMaxZ) {
        int minX = Mth.floor(boxMinX);
        int maxX = Mth.ceil(boxMaxX);
        int minY = Mth.floor(boxMinY);
        int maxY = Mth.ceil(boxMaxY);
        int minZ = Mth.floor(boxMinZ);
        int maxZ = Mth.ceil(boxMaxZ);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState fluidState = world.getFluidState(pos);
                    if (fluidState.is(FluidTags.WATER)) {
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

    public static boolean canHitEntity(AbstractHurtingProjectile fireball, Entity entity) {
        if (fireball instanceof ProjectileAccessor accessor) {
            try {
                return accessor.fireballpredictor$canHitEntity(entity);
            } catch (Throwable ignored) {
                return true;
            }
        }
        return true;
    }

    /**
     * Dynamically finds the first entity intercepted along the remaining flight path of the projectile
     * based on current entity positions in the level. If no entity is intercepted, returns the predicted
     * block hit result at the end of the path.
     */
    public static HitResult findDamageHitResult(Level world, AbstractHurtingProjectile fireball, PredictionData data) {
        if (data == null || data.path == null || data.path.size() < 2) {
            return data != null ? data.hitResult : null;
        }

        int elapsedTicks = Math.max(0, fireball.tickCount - data.predictionAge);
        if (elapsedTicks >= data.path.size() - 1) {
            return data.hitResult;
        }

        for (int i = elapsedTicks; i < data.path.size() - 1; i++) {
            Vec3 p1 = data.path.get(i);
            Vec3 p2 = data.path.get(i + 1);
            AABB segBox = new AABB(
                Math.min(p1.x, p2.x), Math.min(p1.y, p2.y), Math.min(p1.z, p2.z),
                Math.max(p1.x, p2.x), Math.max(p1.y, p2.y), Math.max(p1.z, p2.z)
            ).inflate(1.0);

            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                world, fireball, p1, p2, segBox, 
                entity -> canHitEntity(fireball, entity)
            );

            if (entityHitResult != null) {
                return entityHitResult;
            }
        }

        return data.hitResult;
    }
}
