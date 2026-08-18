package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.config.ModConfig;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

public class ClientPowerLookup {
    private static volatile Float inferredPacketRadius = null;
    private static volatile Float inferredBlockEstimation = null;

    public static float getPower(AbstractHurtingProjectile fireball) {
        Float cached = cachedPower(fireball.getId());
        if (cached != null) {
            return cached;
        }

        if (FireballInferenceTracker.isFireball(fireball)) {
            String currentServerIp = getCurrentServerIp();
            if (currentServerIp != null) {
                Float serverPreset = ModConfig.instance().getServerFallbackPower(currentServerIp);
                if (serverPreset != null && serverPreset > 0.0f) {
                    return serverPreset;
                }
            }

            if (inferredPacketRadius != null && inferredPacketRadius > 0.0f) {
                return inferredPacketRadius;
            }

            if (inferredBlockEstimation != null && inferredBlockEstimation > 0.0f) {
                return inferredBlockEstimation;
            }

            return ModConfig.instance().globalFallbackFireballPower;
        }

        return 1.0F;
    }

    public static void setInferredPacketRadius(float power) {
        inferredPacketRadius = power;
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
        Float cached = ClientPowerCache.POWER_CACHE.get(entityId);
        return (cached != null && cached > 0.0f) ? cached : null;
    }

    public static Float getInferredPacketRadius() {
        return inferredPacketRadius;
    }

    public static void updateInferredBlockEstimation(float power) {
        float minBounded = Math.max(1.0f, power);
        if (inferredBlockEstimation == null) {
            inferredBlockEstimation = minBounded;
        } else {
            inferredBlockEstimation = Math.max(inferredBlockEstimation, minBounded);
        }
    }

    public static Float getInferredBlockEstimation() {
        return inferredBlockEstimation;
    }

    public static void setInferredFireballPower(float power) {
        setInferredPacketRadius(power);
    }

    public static Float getInferredFireballPower() {
        return inferredPacketRadius != null ? inferredPacketRadius : inferredBlockEstimation;
    }

    public static void resetInferredPower() {
        inferredPacketRadius = null;
        inferredBlockEstimation = null;
    }

    public static String getCurrentServerIp() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return ClientHelper.getClientServerIp();
        }
        return null;
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
