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

                if (owner == ProjectileOwner.COMMAND) {
                    InferredPowerEntry unknownEntry = OWNER_INFERENCES.get(ProjectileOwner.UNKNOWN);
                    if (unknownEntry != null && !unknownEntry.isExpired(DEFAULT_INFERENCE_TTL_MS) && unknownEntry.power() > 0.0f) {
                        return unknownEntry.power();
                    }
                }
            } else {
                InferredPowerEntry unknownEntry = OWNER_INFERENCES.get(ProjectileOwner.UNKNOWN);
                if (unknownEntry != null && !unknownEntry.isExpired(DEFAULT_INFERENCE_TTL_MS) && unknownEntry.power() > 0.0f) {
                    return unknownEntry.power();
                }
                InferredPowerEntry cmdEntry = OWNER_INFERENCES.get(ProjectileOwner.COMMAND);
                if (cmdEntry != null && !cmdEntry.isExpired(DEFAULT_INFERENCE_TTL_MS) && cmdEntry.power() > 0.0f) {
                    return cmdEntry.power();
                }
            }

            // Tier 4: Global config fallback
            return ModConfig.instance().globalFallbackFireballPower;
        }

        return 1.0F;
    }

    public static void recordInferredPacketRadius(ProjectileOwner owner, float radius) {
        long now = System.currentTimeMillis();
        InferredPowerEntry entry = new InferredPowerEntry(radius, now, true);
        OWNER_INFERENCES.put(owner != null ? owner : ProjectileOwner.UNKNOWN, entry);
    }

    public static void recordInferredBlockEstimation(ProjectileOwner owner, float power) {
        float minBounded = Math.max(1.0f, power);
        long now = System.currentTimeMillis();
        InferredPowerEntry entry = new InferredPowerEntry(minBounded, now, false);
        OWNER_INFERENCES.put(owner != null ? owner : ProjectileOwner.UNKNOWN, entry);
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
        return getInferredPacketRadius(null);
    }

    public static Float getInferredPacketRadius(ProjectileOwner owner) {
        if (owner != null) {
            InferredPowerEntry entry = OWNER_INFERENCES.get(owner);
            if (entry != null && entry.fromPacketRadius() && !entry.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
                return entry.power();
            }
            return null;
        }
        InferredPowerEntry unk = OWNER_INFERENCES.get(ProjectileOwner.UNKNOWN);
        if (unk != null && unk.fromPacketRadius() && !unk.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
            return unk.power();
        }
        InferredPowerEntry cmd = OWNER_INFERENCES.get(ProjectileOwner.COMMAND);
        if (cmd != null && cmd.fromPacketRadius() && !cmd.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
            return cmd.power();
        }
        return null;
    }

    public static Float getInferredBlockEstimation() {
        return getInferredBlockEstimation(null);
    }

    public static Float getInferredBlockEstimation(ProjectileOwner owner) {
        if (owner != null) {
            InferredPowerEntry entry = OWNER_INFERENCES.get(owner);
            if (entry != null && !entry.fromPacketRadius() && !entry.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
                return entry.power();
            }
            return null;
        }
        InferredPowerEntry unk = OWNER_INFERENCES.get(ProjectileOwner.UNKNOWN);
        if (unk != null && !unk.fromPacketRadius() && !unk.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
            return unk.power();
        }
        InferredPowerEntry cmd = OWNER_INFERENCES.get(ProjectileOwner.COMMAND);
        if (cmd != null && !cmd.fromPacketRadius() && !cmd.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
            return cmd.power();
        }
        return null;
    }

    public static Float getInferredFireballPower() {
        return getInferredFireballPower(null);
    }

    public static Float getInferredFireballPower(ProjectileOwner owner) {
        if (owner != null) {
            InferredPowerEntry entry = OWNER_INFERENCES.get(owner);
            if (entry != null && !entry.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
                return entry.power();
            }
            return null;
        }
        InferredPowerEntry unk = OWNER_INFERENCES.get(ProjectileOwner.UNKNOWN);
        if (unk != null && !unk.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
            return unk.power();
        }
        InferredPowerEntry cmd = OWNER_INFERENCES.get(ProjectileOwner.COMMAND);
        if (cmd != null && !cmd.isExpired(DEFAULT_INFERENCE_TTL_MS)) {
            return cmd.power();
        }
        return null;
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
