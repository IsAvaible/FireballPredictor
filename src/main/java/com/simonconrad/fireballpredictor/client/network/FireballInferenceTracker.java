package com.simonconrad.fireballpredictor.client.network;

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
        public final Vec3 lastPos;
        public final Vec3 hitPos;
        public final long timestamp;

        public FireballLocationRecord(Vec3 lastPos, Vec3 hitPos, long timestamp) {
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
    }

    private static final Map<Integer, FireballLocationRecord> activeFireballRecords = new ConcurrentHashMap<>();

    public static void registerFireballLocation(AbstractHurtingProjectile fireball, Vec3 hitPos) {
        if (isFireball(fireball)) {
            activeFireballRecords.put(fireball.getId(), new FireballLocationRecord(
                fireball.position(),
                hitPos,
                System.currentTimeMillis()
            ));
        }
    }

    public static void unregisterFireballLocation(AbstractHurtingProjectile fireball) {
        if (isFireball(fireball)) {
            FireballLocationRecord rec = activeFireballRecords.get(fireball.getId());
            if (rec != null) {
                activeFireballRecords.put(fireball.getId(), new FireballLocationRecord(
                    fireball.position(),
                    rec.hitPos,
                    System.currentTimeMillis()
                ));
            }
        }
    }

    public static boolean hasFireballNear(Vec3 pos, double maxDistance) {
        long now = System.currentTimeMillis();
        activeFireballRecords.entrySet().removeIf(e -> (now - e.getValue().timestamp) > 3000);

        for (FireballLocationRecord rec : activeFireballRecords.values()) {
            if (rec.isNear(pos, maxDistance)) {
                return true;
            }
        }

        return false;
    }

    public static void clear() {
        activeFireballRecords.clear();
    }
}
