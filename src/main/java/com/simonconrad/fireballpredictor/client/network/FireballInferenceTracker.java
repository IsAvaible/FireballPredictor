package com.simonconrad.fireballpredictor.client.network;

import net.minecraft.entity.projectile.AbstractWindChargeEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FireballInferenceTracker {

    public static boolean isFireball(ExplosiveProjectileEntity entity) {
        if (entity == null) {
            return false;
        }
        if (entity instanceof WitherSkullEntity || entity instanceof AbstractWindChargeEntity) {
            return false;
        }
        return entity instanceof FireballEntity
            || entity instanceof SmallFireballEntity
            || entity instanceof DragonFireballEntity;
    }

    public static final class FireballLocationRecord {
        public final Vec3d lastPos;
        public final Vec3d hitPos;
        public final long timestamp;

        public FireballLocationRecord(Vec3d lastPos, Vec3d hitPos, long timestamp) {
            this.lastPos = lastPos;
            this.hitPos = hitPos;
            this.timestamp = timestamp;
        }

        public boolean isNear(Vec3d pos, double maxDistance) {
            double maxDistSq = maxDistance * maxDistance;
            if (lastPos != null && lastPos.squaredDistanceTo(pos) <= maxDistSq) {
                return true;
            }
            if (hitPos != null && hitPos.squaredDistanceTo(pos) <= maxDistSq) {
                return true;
            }
            return false;
        }
    }

    private static final Map<Integer, FireballLocationRecord> activeFireballRecords = new ConcurrentHashMap<>();

    public static void registerFireballLocation(ExplosiveProjectileEntity fireball, Vec3d hitPos) {
        if (isFireball(fireball)) {
            activeFireballRecords.put(fireball.getId(), new FireballLocationRecord(
                fireball.getEntityPos(),
                hitPos,
                System.currentTimeMillis()
            ));
        }
    }

    public static void unregisterFireballLocation(ExplosiveProjectileEntity fireball) {
        if (isFireball(fireball)) {
            FireballLocationRecord rec = activeFireballRecords.get(fireball.getId());
            if (rec != null) {
                activeFireballRecords.put(fireball.getId(), new FireballLocationRecord(
                    fireball.getEntityPos(),
                    rec.hitPos,
                    System.currentTimeMillis()
                ));
            }
        }
    }

    public static boolean hasFireballNear(Vec3d pos, double maxDistance) {
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
