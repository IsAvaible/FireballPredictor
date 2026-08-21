package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.client.tracking.ClientOwnerCache;
import com.simonconrad.fireballpredictor.client.tracking.InferenceResult;
import com.simonconrad.fireballpredictor.client.tracking.OwnerInferenceEngine;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FireballInferenceTracker {

    public static boolean isFireball(AbstractHurtingProjectile entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof WitherSkull || entity instanceof AbstractWindCharge) {
            return false;
        }
        return entity instanceof LargeFireball
            || entity instanceof SmallFireball
            || entity instanceof DragonFireball;
    }

    public static final class FireballLocationRecord {
        public final int entityId;
        public final ProjectileOwner owner;
        public final Vec3 lastPos;
        public final Vec3 hitPos;
        public final long timestamp;

        public FireballLocationRecord(int entityId, ProjectileOwner owner, Vec3 lastPos, Vec3 hitPos, long timestamp) {
            this.entityId = entityId;
            this.owner = owner != null ? owner : ProjectileOwner.UNKNOWN;
            this.lastPos = lastPos;
            this.hitPos = hitPos;
            this.timestamp = timestamp;
        }

        public boolean isNear(Vec3 pos, double maxDistance) {
            double maxDistSq = maxDistance * maxDistance;
            if (lastPos != null && lastPos.distanceToSqr(pos) <= maxDistSq) {
                return true;
            }
            if (hitPos != null && hitPos.distanceToSqr(pos) <= maxDistSq) {
                return true;
            }
            return false;
        }

        public double distanceToSqr(Vec3 pos) {
            double d1 = lastPos != null ? lastPos.distanceToSqr(pos) : Double.MAX_VALUE;
            double d2 = hitPos != null ? hitPos.distanceToSqr(pos) : Double.MAX_VALUE;
            return Math.min(d1, d2);
        }
    }

    private static final Map<Integer, FireballLocationRecord> activeFireballRecords = new ConcurrentHashMap<>();

    public static void registerFireballLocation(AbstractHurtingProjectile fireball, Vec3 hitPos) {
        if (!isFireball(fireball)) {
            return;
        }
        ProjectileOwner owner = resolveOwner(fireball);
        registerFireballLocation(fireball, hitPos, owner);
    }

    public static void registerFireballLocation(AbstractHurtingProjectile fireball, Vec3 hitPos, ProjectileOwner owner) {
        if (isFireball(fireball)) {
            pruneExpiredRecords();
            activeFireballRecords.put(fireball.getId(), new FireballLocationRecord(
                fireball.getId(),
                owner,
                fireball.position(),
                hitPos,
                System.currentTimeMillis()
            ));
            if (owner != null && owner != ProjectileOwner.UNKNOWN) {
                ClientPowerLookup.touchOwnerInference(owner);
            }
        }
    }

    /**
     * Records the final position of a fireball just as it is discarded, refreshing its
     * timestamp so that an incoming explosion packet within the next few seconds can match it.
     */
    public static void recordFinalFireballLocation(AbstractHurtingProjectile fireball) {
        if (isFireball(fireball)) {
            FireballLocationRecord rec = activeFireballRecords.get(fireball.getId());
            if (rec != null) {
                activeFireballRecords.put(fireball.getId(), new FireballLocationRecord(
                    rec.entityId,
                    rec.owner,
                    fireball.position(),
                    rec.hitPos,
                    System.currentTimeMillis()
                ));
            }
        }
    }

    @Deprecated
    public static void unregisterFireballLocation(AbstractHurtingProjectile fireball) {
        recordFinalFireballLocation(fireball);
    }

    public static void pruneExpiredRecords() {
        long now = System.currentTimeMillis();
        activeFireballRecords.entrySet().removeIf(e -> (now - e.getValue().timestamp) > 3000);
    }

    public static FireballLocationRecord findNearbyFireball(Vec3 pos, double maxDistance) {
        pruneExpiredRecords();

        FireballLocationRecord best = null;
        double bestDistSq = maxDistance * maxDistance;

        for (FireballLocationRecord rec : activeFireballRecords.values()) {
            if (rec.isNear(pos, maxDistance)) {
                double distSq = rec.distanceToSqr(pos);
                if (distSq <= bestDistSq) {
                    bestDistSq = distSq;
                    best = rec;
                }
            }
        }

        return best;
    }

    public static boolean hasFireballNear(Vec3 pos, double maxDistance) {
        return findNearbyFireball(pos, maxDistance) != null;
    }

    public static void clear() {
        activeFireballRecords.clear();
    }

    private static ProjectileOwner resolveOwner(AbstractHurtingProjectile fireball) {
        if (fireball == null) {
            return ProjectileOwner.UNKNOWN;
        }
        InferenceResult cached = ClientOwnerCache.get(fireball.getId());
        if (cached != null && cached.owner() != ProjectileOwner.UNKNOWN) {
            return cached.owner();
        }
        if (fireball.level() != null) {
            InferenceResult inferred = OwnerInferenceEngine.infer(fireball, fireball.level());
            if (inferred != null && inferred.owner() != ProjectileOwner.UNKNOWN) {
                return inferred.owner();
            }
        }
        return ProjectileOwner.UNKNOWN;
    }
}
