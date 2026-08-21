package com.simonconrad.fireballpredictor.math;

import java.util.ArrayList;
import java.util.List;
import com.simonconrad.fireballpredictor.mixin.ProjectileAccessor;
import com.simonconrad.fireballpredictor.projectile.ProjectileProfile;
import com.simonconrad.fireballpredictor.projectile.VanillaProfiles;
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
        ProjectileProfile profile,
        boolean isDangerous
    ) {
        public TrajectoryResult(
            List<Vec3> path,
            List<Vec3> velocities,
            HitResult hitResult,
            float explosionPower,
            BlockStateSnapshot snapshot,
            ProjectileProfile profile,
            boolean isDangerous
        ) {
            this(path, velocities, hitResult, hitResult, explosionPower, snapshot, profile, isDangerous);
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
        
        ProjectileProfile profile = VanillaProfiles.from(fireball);
        if (profile == null) {
            profile = VanillaProfiles.of(com.simonconrad.fireballpredictor.projectile.ProjectileKind.LARGE_FIREBALL);
        }
        boolean isDangerous = fireball instanceof WitherSkull skull && skull.isDangerous();

        double waterDrag = profile.dragWater();
        
        for (int i = 0; i < maxTicks; i++) {
            AABB currentBox = boundingBox.move(currentPos.subtract(fireballPos));
            double drag = isTouchingWater(world, currentBox) ? waterDrag : profile.airDrag(fireball);

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

        float explosionPower = (blockHit != null || firstEntityHit != null) ? ImpactPredictor.resolveExplosionPower(profile, fireball) : 0.0f;
        BlockStateSnapshot snapshot = null;
        HitResult snapshotHit = blockHit != null ? blockHit : firstEntityHit;
        if (snapshotHit != null && explosionPower > 0.0f) {
            Vec3 hitPos = snapshotHit.getLocation();
            float radius = explosionPower * 2.0f;
            BlockPos minPos = BlockPos.containing(hitPos.x - radius - 2, hitPos.y - radius - 2, hitPos.z - radius - 2);
            BlockPos maxPos = BlockPos.containing(hitPos.x + radius + 2, hitPos.y + radius + 2, hitPos.z + radius + 2);
            snapshot = new BlockStateSnapshot(world, minPos, maxPos);
        }
        
        return new TrajectoryResult(path, velocities, hitResult, damageHitResult, explosionPower, snapshot, profile, isDangerous);
    }

    public static PredictionData computePrediction(TrajectoryResult result, int predictionAge) {
        List<BlockPos> brokenBlocks = new ArrayList<>();
        if (result.hitResult != null && result.explosionPower > 0.0f && result.snapshot != null) {
            brokenBlocks = ImpactPredictor.predictBrokenBlocks(result.explosionPower, result.profile, result.isDangerous, result.hitResult.getLocation(), result.snapshot);
        }
        
        PredictionRenderData renderData = createRenderData(result.explosionPower);
        Vec3 initialVelocity = result.velocities.isEmpty() ? Vec3.ZERO : result.velocities.get(0);
        
        return new PredictionData(result.path, result.velocities, result.hitResult, result.damageHitResult, brokenBlocks, initialVelocity, renderData, predictionAge);
    }

    public static PredictionRenderData createRenderData(List<Vec3> path, float explosionPower) {
        return createRenderData(explosionPower);
    }

    public static PredictionRenderData createRenderData(float explosionPower) {
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
        if (data == null || data.path() == null || data.path().size() < 2) {
            return data != null ? data.hitResult() : null;
        }

        int elapsedTicks = Math.max(0, fireball.tickCount - data.predictionAge());
        if (elapsedTicks >= data.path().size() - 1) {
            return data.hitResult();
        }

        for (int i = elapsedTicks; i < data.path().size() - 1; i++) {
            Vec3 p1 = data.path().get(i);
            Vec3 p2 = data.path().get(i + 1);
            AABB segBox = new AABB(p1, p2).inflate(1.0);

            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                world, fireball, p1, p2, segBox, 
                entity -> canHitEntity(fireball, entity)
            );

            if (entityHitResult != null) {
                return entityHitResult;
            }
        }

        return data.hitResult();
    }

    /**
     * Computes the normalized direction in dome-local coordinates from the dome center (hitPos)
     * to the point where the incoming trajectory enters / intercepts the shockwave dome sphere of the given radius.
     *
     * @param path       trajectory flight path in world coordinates
     * @param hitPos     impact location / dome center in world coordinates
     * @param domeRadius shockwave dome sphere radius
     * @return normalized unit vector pointing to the entry intercept on the dome shell, or (0, 1, 0) as fallback.
     */
    public static Vec3 computeTrajectoryDomeIntercept(List<Vec3> path, Vec3 hitPos, float domeRadius) {
        if (path == null || path.size() < 2 || hitPos == null || domeRadius <= 1e-4f) {
            return new Vec3(0, 1, 0);
        }

        // Iterate backwards from the end of the path to find where the incoming trajectory enters the sphere
        for (int i = path.size() - 2; i >= 0; i--) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);

            Vec3 r1 = p1.subtract(hitPos);
            Vec3 r2 = p2.subtract(hitPos);

            double d1Sq = r1.lengthSqr();
            double d2Sq = r2.lengthSqr();
            double radiusSq = (double) domeRadius * domeRadius;

            // Check if this segment crosses the radius sphere boundary (p1 outside/on, p2 inside/on)
            if (d1Sq >= radiusSq && d2Sq <= radiusSq) {
                Vec3 seg = p2.subtract(p1);
                double segLenSq = seg.lengthSqr();
                if (segLenSq > 1e-7) {
                    // Exact line-sphere intersection for segment p1 + t*(p2-p1) relative to hitPos
                    // ||r1 + t*seg||^2 = radiusSq  =>  t^2 * |seg|^2 + 2*t*(r1.seg) + |r1|^2 - radiusSq = 0
                    double a = segLenSq;
                    double b = 2.0 * r1.dot(seg);
                    double c = d1Sq - radiusSq;
                    double disc = b * b - 4.0 * a * c;
                    if (disc >= 0.0) {
                        double sqrtDisc = Math.sqrt(disc);
                        double t = (-b - sqrtDisc) / (2.0 * a);
                        if (t >= 0.0 && t <= 1.0) {
                            Vec3 interceptPos = r1.add(seg.scale(t));
                            double lenSq = interceptPos.lengthSqr();
                            if (lenSq > 1e-7) {
                                return interceptPos.scale(1.0 / Math.sqrt(lenSq));
                            }
                        }
                    }
                }
                // Fallback: segment start direction relative to hitPos
                if (d1Sq > 1e-7) {
                    return r1.scale(1.0 / Math.sqrt(d1Sq));
                }
            }
        }

        // Fallback: reverse direction of the incoming segment leading into the hit
        Vec3 lastSeg = path.get(path.size() - 1).subtract(path.get(path.size() - 2));
        double lenSq = lastSeg.lengthSqr();
        if (lenSq > 1e-7) {
            return lastSeg.scale(-1.0 / Math.sqrt(lenSq));
        }

        return new Vec3(0, 1, 0);
    }
}
