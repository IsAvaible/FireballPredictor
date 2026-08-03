package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;

import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Client-side owner inference for explosive projectiles.
 *
 * <p>Fallback chain (highest priority first):
 * <ol>
 *   <li>Native NBT / {@link AbstractHurtingProjectile#getOwner()} (singleplayer &amp; integrated)</li>
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

    /** Player reach used when attributing a deflection. */
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
    public static InferenceResult infer(AbstractHurtingProjectile projectile, Level world) {
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
    public static InferenceResult inferEnvironmentOnly(AbstractHurtingProjectile projectile, Level world) {
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
    public static InferenceResult fromPacket(Level world, ProjectileOwner owner, int ownerEntityId) {
        Entity entity = null;
        if (world != null && ownerEntityId >= 0) {
            entity = world.getEntity(ownerEntityId);
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
            AbstractHurtingProjectile projectile,
            Level world,
            InferenceResult current,
            Vec3 previousVelocity
    ) {
        if (projectile == null || world == null || previousVelocity == null) {
            return current;
        }

        Vec3 currentVel = projectile.getDeltaMovement();
        if (currentVel.lengthSqr() < 1.0e-6 || previousVelocity.lengthSqr() < 1.0e-6) {
            return current;
        }

        Vec3 prevDir = previousVelocity.normalize();
        Vec3 curDir = currentVel.normalize();
        if (prevDir.dot(curDir) > DEFLECTION_REVERSE_DOT) {
            return current;
        }

        // Direction reversed — attribute to the nearest player within melee reach.
        // Query living entities (not only world.players()) so mock / test players count.
        Vec3 pos = projectile.position();
        AABB reachBox = new AABB(pos, pos).inflate(DEFLECTION_PLAYER_REACH);
        List<Player> nearbyPlayers = world.getEntitiesOfClass(
                Player.class,
                reachBox,
                player -> player != null && player.isAlive() && !player.isSpectator()
        );

        Player best = null;
        double bestDistSq = DEFLECTION_PLAYER_REACH * DEFLECTION_PLAYER_REACH;
        for (Player player : nearbyPlayers) {
            double distSq = player.distanceToSqr(pos);
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

    private static InferenceResult sweepEnvironment(AbstractHurtingProjectile projectile, Level world) {
        Vec3 spawn = projectile.position();
        AABB box = new AABB(spawn, spawn).inflate(SWEEP_RADIUS);

        List<LivingEntity> candidates = world.getEntitiesOfClass(
                LivingEntity.class,
                box,
                OwnerInferenceEngine::isCapableShooter
        );

        if (candidates.isEmpty()) {
            return null;
        }

        Vec3 flight = projectile.getDeltaMovement();
        boolean hasFlight = flight.lengthSqr() > 1.0e-6;
        Vec3 flightDir = hasFlight ? flight.normalize() : null;

        LivingEntity best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (LivingEntity entity : candidates) {
            Vec3 toProjectile = spawn.subtract(entity.getEyePosition());
            if (toProjectile.lengthSqr() < 1.0e-4) {
                continue;
            }
            toProjectile = toProjectile.normalize();

            Vec3 look = entity.getLookAngle();
            double lookDot = look.dot(toProjectile);
            if (lookDot < MIN_LOOK_DOT) {
                continue;
            }

            double flightAlign = 0.0;
            if (hasFlight) {
                // Shooter look should also roughly match the fireball's initial trajectory
                flightAlign = look.dot(flightDir);
                if (flightAlign < 0.0) {
                    continue; // A shooter does not fire projectiles backwards
                }
            }   
            

            double distance = Math.sqrt(entity.distanceToSqr(spawn));
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
        return entity instanceof Blaze
                || entity instanceof Ghast
                || entity instanceof EnderDragon
                || entity instanceof WitherBoss
                || entity instanceof Player;
    }

    private static InferenceResult matchDispenser(AbstractHurtingProjectile projectile, Level world) {
        BlockPos matched = OwnerClassifier.matchDispenserAt(
                world, projectile.position(), projectile.getDeltaMovement());
        if (matched == null) {
            return null;
        }
        return InferenceResult.of(ProjectileOwner.DISPENSER, null, InferenceResult.InferenceSource.DISPENSER_FALLBACK);
    }

    /**
     * Resolve an owner UUID against known players (utility for tests / packet paths).
     * Non-player entities are not globally indexable from the public Level API.
     */
    public static Entity findEntityByUuid(Level world, UUID uuid) {
        if (world == null || uuid == null) {
            return null;
        }
        return world.getPlayerByUUID(uuid);
    }
}
