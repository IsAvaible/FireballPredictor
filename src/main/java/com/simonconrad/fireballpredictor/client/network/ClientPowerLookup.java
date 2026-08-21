package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.client.tracking.ClientOwnerCache;
import com.simonconrad.fireballpredictor.client.tracking.InferenceResult;
import com.simonconrad.fireballpredictor.client.tracking.OwnerInferenceEngine;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientPowerLookup {
    /** Default TTL for per-owner inferences (90 seconds). */
    public static final long DEFAULT_INFERENCE_TTL_MS = 90_000L;

    public record InferredPowerEntry(float power, long timestamp, boolean fromPacketRadius) {
        public boolean isExpired(long ttlMs) {
            return (System.currentTimeMillis() - timestamp) > ttlMs;
        }
    }

    private static final Map<ProjectileOwner, InferredPowerEntry> OWNER_INFERENCES = new ConcurrentHashMap<>();

    private static volatile Float sessionLastPacketRadius = null;
    private static volatile Float sessionMaxBlockEstimation = null;

    public static float getPower(AbstractHurtingProjectile fireball) {
        if (fireball == null) {
            return 1.0F;
        }

        // Tier 1: Authoritative server sync packet
        Float cached = cachedPower(fireball.getId());
        if (cached != null) {
            return cached;
        }

        if (FireballInferenceTracker.isFireball(fireball)) {
            // Tier 2: Server preset from config
            String currentServerIp = getCurrentServerIp();
            if (currentServerIp != null) {
                Float serverPreset = ModConfig.instance().getServerFallbackPower(currentServerIp);
                if (serverPreset != null && serverPreset > 0.0f) {
                    return serverPreset;
                }
            }

            // Tier 3: Per-owner unexpired inference
            ProjectileOwner owner = resolveOwner(fireball);
            if (owner != null && owner != ProjectileOwner.UNKNOWN) {
                InferredPowerEntry ownerEntry = OWNER_INFERENCES.get(owner);
                if (ownerEntry != null && !ownerEntry.isExpired(DEFAULT_INFERENCE_TTL_MS) && ownerEntry.power() > 0.0f) {
                    return ownerEntry.power();
                }

                // If owner is a standard vanilla source (mobs or dispensers) without custom inference,
                // return vanilla default (1.0f) to prevent cross-pollution from custom player blasts.
                if (owner.isMob() || owner == ProjectileOwner.DISPENSER) {
                    return 1.0F;
                }
            } else {
                InferredPowerEntry unknownEntry = OWNER_INFERENCES.get(ProjectileOwner.UNKNOWN);
                if (unknownEntry != null && !unknownEntry.isExpired(DEFAULT_INFERENCE_TTL_MS) && unknownEntry.power() > 0.0f) {
                    return unknownEntry.power();
                }
            }

            // Tier 4: Session fallback (latest packet radius or maximum block estimation)
            if (sessionLastPacketRadius != null && sessionLastPacketRadius > 0.0f) {
                return sessionLastPacketRadius;
            }
            if (sessionMaxBlockEstimation != null && sessionMaxBlockEstimation > 0.0f) {
                return sessionMaxBlockEstimation;
            }

            // Tier 5: Global config fallback
            return ModConfig.instance().globalFallbackFireballPower;
        }

        return 1.0F;
    }

    public static void recordInferredPacketRadius(ProjectileOwner owner, float radius) {
        long now = System.currentTimeMillis();
        InferredPowerEntry entry = new InferredPowerEntry(radius, now, true);
        if (owner != null) {
            OWNER_INFERENCES.put(owner, entry);
        } else {
            OWNER_INFERENCES.put(ProjectileOwner.UNKNOWN, entry);
        }
        sessionLastPacketRadius = radius;
    }

    public static void recordInferredBlockEstimation(ProjectileOwner owner, float power) {
        float minBounded = Math.max(1.0f, power);
        long now = System.currentTimeMillis();
        InferredPowerEntry entry = new InferredPowerEntry(minBounded, now, false);
        if (owner != null) {
            OWNER_INFERENCES.put(owner, entry);
        } else {
            OWNER_INFERENCES.put(ProjectileOwner.UNKNOWN, entry);
        }

        if (sessionMaxBlockEstimation == null) {
            sessionMaxBlockEstimation = minBounded;
        } else {
            sessionMaxBlockEstimation = Math.max(sessionMaxBlockEstimation, minBounded);
        }
    }

    public static void setInferredPacketRadius(float power) {
        recordInferredPacketRadius(ProjectileOwner.UNKNOWN, power);
    }

    public static void updateInferredBlockEstimation(float power) {
        recordInferredBlockEstimation(ProjectileOwner.UNKNOWN, power);
    }

    public static void setInferredFireballPower(float power) {
        setInferredPacketRadius(power);
    }

    public static Float getInferredPacketRadius() {
        return getInferredPacketRadius(ProjectileOwner.UNKNOWN);
    }

    public static Float getInferredPacketRadius(ProjectileOwner owner) {
        if (owner != null) {
            InferredPowerEntry entry = OWNER_INFERENCES.get(owner);
            if (entry != null && entry.fromPacketRadius() && !entry.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
                return entry.power();
            }
        }
        return sessionLastPacketRadius;
    }

    public static Float getInferredBlockEstimation() {
        return getInferredBlockEstimation(ProjectileOwner.UNKNOWN);
    }

    public static Float getInferredBlockEstimation(ProjectileOwner owner) {
        if (owner != null) {
            InferredPowerEntry entry = OWNER_INFERENCES.get(owner);
            if (entry != null && !entry.fromPacketRadius() && !entry.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
                return entry.power();
            }
        }
        return sessionMaxBlockEstimation;
    }

    public static Float getInferredFireballPower() {
        return getInferredFireballPower(ProjectileOwner.UNKNOWN);
    }

    public static Float getInferredFireballPower(ProjectileOwner owner) {
        if (owner != null) {
            InferredPowerEntry entry = OWNER_INFERENCES.get(owner);
            if (entry != null && !entry.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
                return entry.power();
            }
        }
        return sessionLastPacketRadius != null ? sessionLastPacketRadius : sessionMaxBlockEstimation;
    }

    public static InferredPowerEntry getOwnerInference(ProjectileOwner owner) {
        return owner != null ? OWNER_INFERENCES.get(owner) : null;
    }

    /**
     * Refreshes the TTL of an active owner inference if a new shot of that owner type
     * is observed within the unexpired window.
     */
    public static void touchOwnerInference(ProjectileOwner owner) {
        if (owner == null || owner == ProjectileOwner.UNKNOWN) {
            return;
        }
        InferredPowerEntry existing = OWNER_INFERENCES.get(owner);
        if (existing != null && !existing.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
            OWNER_INFERENCES.put(owner, new InferredPowerEntry(existing.power(), System.currentTimeMillis(), existing.fromPacketRadius()));
        }
    }

    /**
     * Returns the server-authoritative explosion power for an entity id, or {@code null} when the
     * server sent no usable value.
     *
     * <p>The server sends {@code -1.0f} for hurting projectiles whose power is not statically known
     * on the server side (e.g. wither skulls). Non-positive cached values are therefore treated as
     * "no value" so the lookup falls through to the inference/fallback chain below instead of
     * propagating an invalid power into the prediction pipeline (which would silently disable the
     * shockwave dome, block-destruction overlay and damage estimates for those projectiles).
     */
    public static Float cachedPower(int entityId) {
        Float cached = ClientPowerCache.get(entityId);
        return (cached != null && cached > 0.0f) ? cached : null;
    }

    public static void resetInferredPower() {
        OWNER_INFERENCES.clear();
        sessionLastPacketRadius = null;
        sessionMaxBlockEstimation = null;
    }

    public static String getCurrentServerIp() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return ClientHelper.getClientServerIp();
        }
        return null;
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

    @Environment(EnvType.CLIENT)
    private static class ClientHelper {
        private static String getClientServerIp() {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && client.getCurrentServer() != null) {
                return client.getCurrentServer().ip;
            }
            return null;
        }
    }
}
