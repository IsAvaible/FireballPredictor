package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;

import com.simonconrad.fireballpredictor.config.ModConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Per-projectile owner attribution state held by the client tracker.
 */
public final class TrackedProjectile {

    private final ExplosiveProjectileEntity projectile;
    private InferenceResult inference;
    private Vec3d lastVelocity;
    private boolean shouldRender;

    private TrackedProjectile(ExplosiveProjectileEntity projectile, InferenceResult inference) {
        this.projectile = projectile;
        this.inference = inference;
        this.lastVelocity = projectile.getVelocity();
        this.shouldRender = evaluateFilter(projectile, inference.owner(), inference.isDeflected());
    }

    public static TrackedProjectile of(ExplosiveProjectileEntity projectile, World world) {
        InferenceResult result = OwnerInferenceEngine.infer(projectile, world);
        return new TrackedProjectile(projectile, result);
    }

    public ExplosiveProjectileEntity projectile() {
        return projectile;
    }

    public InferenceResult inference() {
        return inference;
    }

    public ProjectileOwner owner() {
        return inference.owner();
    }

    public boolean shouldRender() {
        return shouldRender;
    }

    /**
     * Apply an authoritative packet result (higher priority than environmental inference).
     */
    public void applyPacketResult(InferenceResult packetResult) {
        if (packetResult == null) {
            return;
        }
        // Packet wins unless we already have native NBT
        if (inference.source() == InferenceResult.InferenceSource.NATIVE_NBT) {
            return;
        }
        boolean wasDeflected = inference.isDeflected();
        this.inference = wasDeflected
                ? InferenceResult.of(packetResult.owner(), packetResult.entity(), packetResult.source(), true)
                : packetResult;
        this.shouldRender = evaluateFilter(projectile, this.inference.owner(), this.inference.isDeflected());
    }

    /**
     * Tick-time maintenance: deflection re-attribution + live filter refresh.
     */
    public void tick(World world) {
        Vec3d currentVel = projectile.getVelocity();
        InferenceResult updated = OwnerInferenceEngine.reassignOnDeflection(
                projectile, world, inference, lastVelocity
        );
        if (updated != inference) {
            this.inference = updated;
        }
        this.lastVelocity = currentVel;
        this.shouldRender = evaluateFilter(projectile, inference.owner(), inference.isDeflected());
    }

    /**
     * Whether the user's config wants this owner / projectile type highlighted.
     */
    public static boolean evaluateFilter(ExplosiveProjectileEntity projectile, ProjectileOwner owner) {
        return evaluateFilter(projectile, owner, false);
    }

    /**
     * Whether the user's config wants this owner / projectile type highlighted,
     * allowing deflected projectiles to bypass the player-owner filter.
     */
    public static boolean evaluateFilter(ExplosiveProjectileEntity projectile, ProjectileOwner owner, boolean isDeflected) {
        if (ServerTrackingRules.isDisabled(owner)) {
            return false;
        }

        ModConfig config = ModConfig.instance();

        if (!config.trackProjectiles) {
            return false;
        }

        // 1. WHAT check (ProjectileEntity Type)
        boolean passType;
        if (projectile instanceof AbstractWindChargeEntity) {
            passType = config.trackWindCharges;
        } else if (projectile instanceof WitherSkullEntity) {
            passType = config.trackWitherSkulls;
        } else {
            // Fireballs (Large, Small, Dragon)
            passType = config.trackFireballs;
        }

        if (!passType) {
            return false;
        }

        if (projectile instanceof AbstractWindChargeEntity) {
            return true;
        }

        // 2. WHO check (Source / Owner)
        return switch (owner) {
            case BLAZE -> config.trackMobProjectiles && config.trackBlazeFireballs;
            case GHAST -> config.trackMobProjectiles && config.trackGhastFireballs;
            case ENDER_DRAGON -> config.trackMobProjectiles && config.trackEnderDragonFireballs;
            case WITHER -> config.trackMobProjectiles && config.trackWitherMob;
            case PLAYER -> (config.trackOtherOwnerProjectiles && config.trackPlayerProjectiles) || isDeflected;
            case DISPENSER -> config.trackOtherOwnerProjectiles && config.trackDispenserProjectiles;
            case COMMAND, UNKNOWN -> config.trackOtherOwnerProjectiles && config.trackCommandProjectiles;
        };
    }

    /**
     * Wind charges keep their dedicated toggle and are not owner-filtered.
     */
    public static boolean passesLegacyTypeGate(ExplosiveProjectileEntity projectile) {
        if (projectile instanceof AbstractWindChargeEntity) {
            return ModConfig.instance().trackWindCharges;
        }
        return true;
    }

    /**
     * True when this entity is one of the owner-filterable hostile projectiles.
     * Wind charges are still tracked for prediction but skip owner filtering
     * (they are player/breeze toys, not the fireball threat model).
     */
    public static boolean isOwnerFilterable(ExplosiveProjectileEntity projectile) {
        return projectile instanceof FireballEntity
                || projectile instanceof SmallFireballEntity
                || projectile instanceof DragonFireballEntity
                || projectile instanceof WitherSkullEntity;
    }

    @Nullable
    public Entity ownerEntity() {
        return inference.entity();
    }
}
