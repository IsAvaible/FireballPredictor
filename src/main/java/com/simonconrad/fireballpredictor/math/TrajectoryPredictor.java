package com.simonconrad.fireballpredictor.math;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic client-side replica of the 1.8.9 {@code EntityFireball.onUpdate}
 * movement code: same acceleration, drag, water drag, block raycast and entity
 * raycast rules, so the predicted path matches what the server will do.
 */
public final class TrajectoryPredictor {

    private TrajectoryPredictor() {
    }

    public static final double AIR_DRAG = 0.95;
    public static final double CHARGED_SKULL_DRAG = 0.73;
    public static final double WATER_DRAG = 0.8;

    /** Result of one trajectory simulation. */
    public static final class Prediction {
        /** One position per tick, index 0 = position at simulation time. */
        public final List<Vec3> path = new ArrayList<Vec3>();
        /** First collision (block or entity), or null if the path ran out of ticks. */
        public MovingObjectPosition impact;
        /** True when the first collision is an entity (fires the explosion there too). */
        public boolean entityImpact;

        public int ticksToImpact() {
            return Math.max(0, path.size() - 1);
        }
    }

    /**
     * Simulates the fireball's future path. Reads nothing but position, velocity,
     * acceleration and class - everything a client can see.
     */
    public static Prediction simulate(EntityFireball fireball, World world, int maxTicks) {
        return simulate(fireball, world, maxTicks, fireball.motionX, fireball.motionY, fireball.motionZ);
    }

    /**
     * Same simulation with an explicit starting velocity. The 1.8.9 client does not
     * receive per-tick velocity updates for projectiles, so the tracker estimates the
     * current velocity from consecutive synced positions and passes it in here.
     */
    public static Prediction simulate(EntityFireball fireball, World world, int maxTicks,
                                      double velX, double velY, double velZ) {
        return simulate(fireball, world, maxTicks, fireball.posX, fireball.posY, fireball.posZ,
                velX, velY, velZ);
    }

    /**
     * Same simulation with an explicit, finite starting position (see
     * {@code FireballPredictorClient.resolveAnchor}) and velocity. Non-finite acceleration
     * components are treated as zero: a fireball summoned without shooter and without a
     * {@code power} tag gives the vanilla client a NaN acceleration vector
     * ({@code EntityFireball(World, x, y, z, 0, 0, 0)} normalizes 0/0), which must not
     * poison the predicted path.
     */
    public static Prediction simulate(EntityFireball fireball, World world, int maxTicks,
                                      double startX, double startY, double startZ,
                                      double velX, double velY, double velZ) {
        return simulate(fireball, world, maxTicks, startX, startY, startZ, velX, velY, velZ,
                finiteOrZero(fireball.accelerationX), finiteOrZero(fireball.accelerationY),
                finiteOrZero(fireball.accelerationZ));
    }

    /**
     * Full simulation with explicit position, velocity AND acceleration overrides.
     * The acceleration is constant for a fireball, so callers that know the true
     * server-side acceleration (custom sync packets) can correct the client-side value,
     * which the spawn packet normalizes to a length of 0.1 regardless of the summon NBT.
     */
    public static Prediction simulate(EntityFireball fireball, World world, int maxTicks,
                                      double startX, double startY, double startZ,
                                      double velX, double velY, double velZ,
                                      double accX, double accY, double accZ) {
        Prediction prediction = new Prediction();

        // Reject non-finite inputs outright: comparisons with NaN are always false, so an
        // unguarded simulation would happily walk a NaN path through every range check.
        if (!isFinite(startX) || !isFinite(startY) || !isFinite(startZ)
                || !isFinite(velX) || !isFinite(velY) || !isFinite(velZ)
                || !isFinite(accX) || !isFinite(accY) || !isFinite(accZ)) {
            return prediction; // empty path, no impact -> callers render nothing
        }

        double posX = startX, posY = startY, posZ = startZ;

        final double airDrag = getAirDrag(fireball);
        final double halfSize = fireball.width / 2.0;

        prediction.path.add(new Vec3(posX, posY, posZ));

        // Stationary projectile (no motion, no acceleration): it only detonates when
        // something collides with it; predicting maxTicks of identical points is pointless.
        if (velX * velX + velY * velY + velZ * velZ < 1.0E-12
                && accX * accX + accY * accY + accZ * accZ < 1.0E-12) {
            return prediction;
        }

        for (int tick = 1; tick <= maxTicks; tick++) {
            double nextX = posX + velX;
            double nextY = posY + velY;
            double nextZ = posZ + velZ;

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end = new Vec3(nextX, nextY, nextZ);

            // Block raycast, exactly like vanilla: rayTraceBlocks(vec3, vec31)
            MovingObjectPosition blockHit = world.rayTraceBlocks(start, end);

            // Vanilla clamps the entity raycast to the block hit point (if any).
            Vec3 rayEnd = blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                    ? blockHit.hitVec : end;

            MovingObjectPosition entityHit = raycastEntities(fireball, world, start, rayEnd, halfSize, velX, velY, velZ);

            if (entityHit != null) {
                prediction.path.add(entityHit.hitVec);
                prediction.impact = entityHit;
                prediction.entityImpact = true;
                return prediction;
            }

            if (blockHit != null && blockHit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                prediction.path.add(blockHit.hitVec);
                prediction.impact = blockHit;
                prediction.entityImpact = false;
                return prediction;
            }

            // Move, then apply drag at the new position (vanilla does the water
            // check after setPosition, before the velocity update).
            posX = nextX;
            posY = nextY;
            posZ = nextZ;
            prediction.path.add(new Vec3(posX, posY, posZ));

            double drag = isTouchingWater(world, posX, posY, posZ, halfSize) ? WATER_DRAG : airDrag;
            velX = (velX + accX) * drag;
            velY = (velY + accY) * drag;
            velZ = (velZ + accZ) * drag;
        }

        return prediction;
    }

    /** Air drag per 1.8.9: charged (blue) wither skulls use 0.73, everything else 0.95. */
    public static double getAirDrag(EntityFireball fireball) {
        if (fireball instanceof EntityWitherSkull && ((EntityWitherSkull) fireball).isInvulnerable()) {
            return CHARGED_SKULL_DRAG;
        }
        return AIR_DRAG;
    }

    /**
     * Whether the fireball box (1.8.9: centered on x/z, from posY to posY+height)
     * at the given position touches water.
     */
    private static boolean isTouchingWater(World world, double x, double y, double z, double halfSize) {
        int minX = MathHelper.floor_double(x - halfSize);
        int maxX = MathHelper.floor_double(x + halfSize);
        int minY = MathHelper.floor_double(y);
        int maxY = MathHelper.floor_double(y + halfSize * 2.0);
        int minZ = MathHelper.floor_double(z - halfSize);
        int maxZ = MathHelper.floor_double(z + halfSize);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    pos.set(bx, by, bz);
                    if (world.getBlockState(pos).getBlock().getMaterial() == Material.water) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Vanilla entity collision: box = own box moved by the velocity, expanded by 1.0;
     * candidate entities must be collidable and (on servers) not be the shooter for the
     * first 25 ticks. Client-side the shooter is usually unknown (null) so everything
     * collidable is considered - matching what the server will mostly do.
     */
    private static MovingObjectPosition raycastEntities(EntityFireball fireball, World world, Vec3 start, Vec3 end,
                                                        double halfSize, double velX, double velY, double velZ) {
        AxisAlignedBB box = new AxisAlignedBB(
                start.xCoord - halfSize, start.yCoord, start.zCoord - halfSize,
                start.xCoord + halfSize, start.yCoord + halfSize * 2.0, start.zCoord + halfSize)
                .addCoord(velX, velY, velZ).expand(1.0D, 1.0D, 1.0D);

        @SuppressWarnings("unchecked")
        List<Entity> list = world.getEntitiesWithinAABBExcludingEntity(fireball, box);

        Entity hitEntity = null;
        Vec3 hitVec = null;
        double closestSq = 0.0D;

        for (Entity entity : list) {
            if (entity.canBeCollidedWith()
                    && (!entity.isEntityEqual(fireball.shootingEntity) || fireball.ticksExisted >= 25)) {
                AxisAlignedBB aabb = entity.getEntityBoundingBox()
                        .expand(0.30000001192092896D, 0.30000001192092896D, 0.30000001192092896D);
                MovingObjectPosition mop = aabb.calculateIntercept(start, end);
                if (mop != null) {
                    double distSq = start.squareDistanceTo(mop.hitVec);
                    if (distSq < closestSq || closestSq == 0.0D) {
                        hitEntity = entity;
                        hitVec = mop.hitVec;
                        closestSq = distSq;
                    }
                }
            }
        }

        return hitEntity != null ? new MovingObjectPosition(hitEntity, hitVec) : null;
    }

    /**
     * Explosion power as the server would use it:
     * ghast (large) fireball = its explosionPower field (default 1, not synced to clients),
     * wither skull = 1, small (blaze) fireball = 0 (does not explode).
     * Note: 1.8.9 has no dragon fireballs (added in 1.9).
     */
    public static float resolvePower(EntityFireball fireball) {
        if (fireball instanceof EntityLargeFireball) {
            return (float) ((EntityLargeFireball) fireball).explosionPower;
        }
        if (fireball instanceof EntityWitherSkull) {
            return 1.0F;
        }
        if (fireball instanceof EntitySmallFireball) {
            return 0.0F;
        }
        return 1.0F;
    }

    /** True for charged (blue) wither skulls - the "dangerous" flag. */
    public static boolean isDangerous(EntityFireball fireball) {
        return fireball instanceof EntityWitherSkull && ((EntityWitherSkull) fireball).isInvulnerable();
    }

    /**
     * Scans the not-yet-flown part of the predicted path for an intercept with the
     * given entity (used to detect a direct hit on the player). Only tests the
     * entity itself, so it is cheap enough to run every tick.
     *
     * @return intercept point, or null
     */
    public static Vec3 findEntityIntercept(Prediction prediction, int elapsedTicks, Entity target) {
        List<Vec3> path = prediction.path;
        if (path.size() < 2 || elapsedTicks >= path.size() - 1) {
            return null;
        }
        AxisAlignedBB aabb = target.getEntityBoundingBox()
                .expand(0.30000001192092896D, 0.30000001192092896D, 0.30000001192092896D);
        for (int i = Math.max(0, elapsedTicks); i < path.size() - 1; i++) {
            Vec3 from = path.get(i);
            Vec3 to = path.get(i + 1);
            // Never feed a degenerate (NaN) segment into the AABB intercept math.
            if (!DamageCalculator.isFinite(from) || !DamageCalculator.isFinite(to)) {
                continue;
            }
            MovingObjectPosition mop = aabb.calculateIntercept(from, to);
            if (mop != null) {
                return mop.hitVec;
            }
        }
        return null;
    }

    /** NaN/infinity -> 0.0; command-summoned fireballs can carry NaN acceleration/motion. */
    static double finiteOrZero(double value) {
        return DamageCalculator.isFinite(value) ? value : 0.0;
    }

    private static boolean isFinite(double value) {
        return DamageCalculator.isFinite(value);
    }
}
