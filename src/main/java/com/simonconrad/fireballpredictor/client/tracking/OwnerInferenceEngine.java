package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;

import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

/**
 * Client-side owner inference for explosive projectiles.
 *
 * <p>Fallback chain (highest priority first):
 * <ol>
 *   <li>Native NBT / {@link ExplosiveProjectileEntity#getOwner()} (singleplayer &amp; integrated)</li>
 *   <li>Special server packet ({@link ClientOwnerCache})</li>
 *   <li>Environmental sweep (nearby capable shooters + look-vector match)</li>
 *   <li>Dispenser adjacency fallback</li>
 *   <li>Unknown → {@link ProjectileOwner#COMMAND}</li>
 * </ol>
 */
public final class OwnerInferenceEngine {

    /** Search radius (blocks) around the projectile spawn for candidate shooters. */
    public static final double SWEEP_RADIUS = 6.0;

    /** Minimum look-vector / to-projectile alignment (cos θ). ~25° cone. */
    public static final double MIN_LOOK_DOT = 0.90;

    /** Velocity deflection threshold for owner re-attribution (cos θ <= 0.2, angle change >= ~78.5°). */
    public static final double DEFLECTION_REVERSE_DOT = 0.2;

    /** PlayerEntity reach used when attributing a deflection. */
    public static final double DEFLECTION_PLAYER_REACH = 4.5;

    /**
     * Optional packet-tier lookup. Wired by {@code ClientOwnerCache} on the client;
     * left null in server or headless environments so this class stays side-safe.
     */
    @FunctionalInterface
    public interface PacketLookup {
        @org.jetbrains.annotations.Nullable
        InferenceResult get(int entityId);
    }

    private static volatile PacketLookup packetLookup;

    private OwnerInferenceEngine() {
    }

    public static void setPacketLookup(@org.jetbrains.annotations.Nullable PacketLookup lookup) {
        packetLookup = lookup;
    }

    /**
     * Full inference for a newly observed projectile.
     * Packet cache is consulted before the environmental sweep.
     */
    public static InferenceResult infer(ExplosiveProjectileEntity projectile, World world) {
        if (projectile == null || world == null) {
            return InferenceResult.unknown();
        }

        // Tier 1 — native owner (works in singleplayer when NBT owner is present / resolved)
        Entity nativeOwner = projectile.getOwner();
        if (nativeOwner != null) {
            ProjectileOwner classified = classifyEntity(nativeOwner);
            if (classified != ProjectileOwner.UNKNOWN) {
                return InferenceResult.of(classified, nativeOwner, InferenceResult.InferenceSource.NATIVE_NBT);
            }
        }

        // Tier 2 — special packet from a server that also has this mod
        PacketLookup lookup = packetLookup;
        if (lookup != null) {
            InferenceResult packet = lookup.get(projectile.getId());
            if (packet != null && packet.owner() != ProjectileOwner.UNKNOWN) {
                return packet;
            }
        }

        // Tier 3 — environmental sweep
        InferenceResult sweep = sweepEnvironment(projectile, world);
        if (sweep != null) {
            return sweep;
        }

        // Tier 4 — dispenser fallback
        InferenceResult dispenser = matchDispenser(projectile, world);
        if (dispenser != null) {
            return dispenser;
        }

        // Tier 5 — command / unmatched
        return InferenceResult.unknown();
    }

    /**
     * Re-run only the environmental + dispenser tiers (used when a packet arrives late
     * or the caller already ruled out native/packet).
     */
    public static InferenceResult inferEnvironmentOnly(ExplosiveProjectileEntity projectile, World world) {
        InferenceResult sweep = sweepEnvironment(projectile, world);
        if (sweep != null) {
            return sweep;
        }
        InferenceResult dispenser = matchDispenser(projectile, world);
        if (dispenser != null) {
            return dispenser;
        }
        return InferenceResult.unknown();
    }

    /**
     * Classify a known entity into a {@link ProjectileOwner} bucket.
     */
    public static ProjectileOwner classifyEntity(Entity entity) {
        return OwnerClassifier.classifyEntity(entity);
    }

    /**
     * Build a packet-sourced result, resolving the owner entity by id when possible.
     */
    public static InferenceResult fromPacket(World world, ProjectileOwner owner, int ownerEntityId) {
        Entity entity = null;
        if (world != null && ownerEntityId >= 0) {
            entity = world.getEntityById(ownerEntityId);
            if (entity != null) {
                ProjectileOwner refined = classifyEntity(entity);
                if (refined != ProjectileOwner.UNKNOWN) {
                    owner = refined;
                }
            }
        }
        if (owner == null || owner == ProjectileOwner.UNKNOWN) {
            return InferenceResult.unknown();
        }
        return InferenceResult.of(owner, entity, InferenceResult.InferenceSource.SERVER_PACKET);
    }

    /**
     * If the projectile abruptly reverses direction near a player attack hitbox,
     * re-assign ownership to that player (ghast fireball deflection).
     *
     * @return updated result, or {@code current} when no deflection is detected
     */
    public static InferenceResult reassignOnDeflection(
            ExplosiveProjectileEntity projectile,
            World world,
            InferenceResult current,
            Vec3d previousVelocity
    ) {
        if (projectile == null || world == null || previousVelocity == null) {
            return current;
        }

        Vec3d currentVel = projectile.getVelocity();
        if (currentVel.lengthSquared() < 1.0e-6 || previousVelocity.lengthSquared() < 1.0e-6) {
            return current;
        }

        Vec3d prevDir = previousVelocity.normalize();
        Vec3d curDir = currentVel.normalize();
        if (prevDir.dotProduct(curDir) > DEFLECTION_REVERSE_DOT) {
            return current;
        }

        // Direction reversed — attribute to the nearest player within melee reach.
        // Query living entities (not only world.players()) so mock / test players count.
        Vec3d pos = projectile.getEntityPos();
        Box reachBox = new Box(pos, pos).expand(DEFLECTION_PLAYER_REACH);
        List<PlayerEntity> nearbyPlayers = world.getEntitiesByClass(
                PlayerEntity.class,
                reachBox,
                player -> player != null && player.isAlive() && !player.isSpectator()
        );

        PlayerEntity best = null;
        double bestDistSq = DEFLECTION_PLAYER_REACH * DEFLECTION_PLAYER_REACH;
        for (PlayerEntity player : nearbyPlayers) {
            double distSq = player.squaredDistanceTo(pos);
            if (distSq <= bestDistSq) {
                bestDistSq = distSq;
                best = player;
            }
        }

        if (best == null) {
            return current;
        }

        return InferenceResult.of(ProjectileOwner.PLAYER, best, InferenceResult.InferenceSource.ENVIRONMENTAL_SWEEP, true);
    }

    // ---- Internal heuristics ------------------------------------------------

    private static InferenceResult sweepEnvironment(ExplosiveProjectileEntity projectile, World world) {
        Vec3d spawn = projectile.getEntityPos();
        Box box = new Box(spawn, spawn).expand(SWEEP_RADIUS);

        List<LivingEntity> candidates = world.getEntitiesByClass(
                LivingEntity.class,
                box,
                OwnerInferenceEngine::isCapableShooter
        );

        if (candidates.isEmpty()) {
            return null;
        }

        Vec3d flight = projectile.getVelocity();
        boolean hasFlight = flight.lengthSquared() > 1.0e-6;
        Vec3d flightDir = hasFlight ? flight.normalize() : null;

        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (LivingEntity entity : candidates) {
            Vec3d toProjectile = spawn.subtract(entity.getEyePos());
            if (toProjectile.lengthSquared() < 1.0e-4) {
                continue;
            }
            toProjectile = toProjectile.normalize();

            Vec3d look = entity.getRotationVec(1.0F);
            double lookDot = look.dotProduct(toProjectile);
            if (lookDot < MIN_LOOK_DOT) {
                continue;
            }

            double flightAlign = 0.0;
            if (hasFlight) {
                // Shooter look should also roughly match the fireball's initial trajectory
                flightAlign = look.dotProduct(flightDir);
                if (flightAlign < 0.0) {
                    continue; // A shooter does not fire projectiles backwards
                }
            }   
            

            double distance = Math.sqrt(entity.squaredDistanceTo(spawn));
            // Prefer high look alignment, then flight alignment, then closer entities
            double score = lookDot * 10.0 + flightAlign * 3.0 - distance * 0.15;

            if (score > bestScore) {
                bestScore = score;
                best = entity;
            }
        }

        if (best == null) {
            return null;
        }

        ProjectileOwner owner = classifyEntity(best);
        if (owner == ProjectileOwner.UNKNOWN) {
            return null;
        }
        return InferenceResult.of(owner, best, InferenceResult.InferenceSource.ENVIRONMENTAL_SWEEP);
    }

    private static boolean isCapableShooter(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        return entity instanceof BlazeEntity
                || entity instanceof GhastEntity
                || entity instanceof EnderDragonEntity
                || entity instanceof WitherEntity
                || entity instanceof PlayerEntity;
    }

    private static InferenceResult matchDispenser(ExplosiveProjectileEntity projectile, World world) {
        BlockPos matched = OwnerClassifier.matchDispenserAt(
                world, projectile.getEntityPos(), projectile.getVelocity());
        if (matched == null) {
            return null;
        }
        return InferenceResult.of(ProjectileOwner.DISPENSER, null, InferenceResult.InferenceSource.DISPENSER_FALLBACK);
    }

    /**
     * Resolve an owner UUID against known players (utility for tests / packet paths).
     * Non-player entities are not globally indexable from the public World API.
     */
    public static Entity findEntityByUuid(World world, UUID uuid) {
        if (world == null || uuid == null) {
            return null;
        }
        return world.getPlayerByUuid(uuid);
    }
}
