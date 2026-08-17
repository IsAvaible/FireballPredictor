package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;

import com.simonconrad.fireballpredictor.config.ModConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Per-projectile owner attribution state held by the client tracker.
 */
public final class TrackedProjectile {

    private final AbstractHurtingProjectile projectile;
    private InferenceResult inference;
    private Vec3 lastVelocity;
    private boolean shouldRender;

    private TrackedProjectile(AbstractHurtingProjectile projectile, InferenceResult inference) {
        this.projectile = projectile;
        this.inference = inference;
        this.lastVelocity = projectile.getDeltaMovement();
        this.shouldRender = evaluateFilter(projectile, inference.owner(), inference.isDeflected());
    }

    public static TrackedProjectile of(AbstractHurtingProjectile projectile, Level world) {
        InferenceResult result = OwnerInferenceEngine.infer(projectile, world);
        return new TrackedProjectile(projectile, result);
    }

    public AbstractHurtingProjectile projectile() {
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
    public void tick(Level world) {
        Vec3 currentVel = projectile.getDeltaMovement();
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
    public static boolean evaluateFilter(AbstractHurtingProjectile projectile, ProjectileOwner owner) {
        return evaluateFilter(projectile, owner, false);
    }

    /**
     * Whether the user's config wants this owner / projectile type highlighted,
     * allowing deflected projectiles to bypass the player-owner filter.
     */
    public static boolean evaluateFilter(AbstractHurtingProjectile projectile, ProjectileOwner owner, boolean isDeflected) {
        if (ServerTrackingRules.isDisabled(owner)) {
            return false;
        }

        ModConfig config = ModConfig.instance();

        if (!config.trackProjectiles) {
            return false;
        }

        // 1. WHO check (Source / Owner) - Owner tracking takes priority
        boolean passOwner = switch (owner) {
            case BLAZE -> config.trackMobProjectiles && config.trackBlazeFireballs;
            case GHAST -> config.trackMobProjectiles && config.trackGhastFireballs;
            case ENDER_DRAGON -> config.trackMobProjectiles && config.trackEnderDragonFireballs;
            case WITHER -> config.trackMobProjectiles && config.trackWitherMob;
            case PLAYER -> (config.trackOtherOwnerProjectiles && config.trackPlayerProjectiles) || isDeflected;
            case DISPENSER -> config.trackOtherOwnerProjectiles && config.trackDispenserProjectiles;
            case COMMAND, UNKNOWN -> config.trackOtherOwnerProjectiles && config.trackCommandProjectiles;
        };

        if (!passOwner) {
            return false;
        }

        // 2. WHAT check (Projectile Type)
        boolean passType;
        if (projectile instanceof AbstractWindCharge) {
            passType = config.trackWindCharges;
        } else if (projectile instanceof WitherSkull) {
            passType = config.trackWitherSkulls;
        } else {
            // Fireballs (Large, Small, Dragon)
            passType = config.trackFireballs;
        }

        return passType;
    }

    /**
     * True when this entity is one of the filterable projectiles.
     */
    public static boolean isOwnerFilterable(AbstractHurtingProjectile projectile) {
        return projectile instanceof LargeFireball
                || projectile instanceof SmallFireball
                || projectile instanceof DragonFireball
                || projectile instanceof WitherSkull
                || projectile instanceof AbstractWindCharge;
    }

    /**
     * True for projectiles that deal no blast power (SmallFireball, DragonFireball).
     */
    public static boolean isZeroExplosionPower(AbstractHurtingProjectile projectile) {
        return projectile instanceof SmallFireball
                || projectile instanceof DragonFireball;
    }

    @Nullable
    public Entity ownerEntity() {
        return inference.entity();
    }
}
