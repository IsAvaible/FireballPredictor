package com.simonconrad.fireballpredictor.client.network;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import com.simonconrad.fireballpredictor.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

public class ClientPowerLookup {
    private static volatile Float inferredPacketRadius = null;
    private static volatile Float inferredBlockEstimation = null;

    public static float getPower(ExplosiveProjectileEntity fireball) {
        // Tier 1: Server Sync Payload
        if (ClientPowerCache.POWER_CACHE.containsKey(fireball.getId())) {
            return ClientPowerCache.POWER_CACHE.get(fireball.getId());
        }

        if (FireballInferenceTracker.isFireball(fireball)) {
            // Tier 2: Server-Specific Config Preset
            String currentServerIp = getCurrentServerIp();
            if (currentServerIp != null) {
                Float serverPreset = ModConfig.instance().getServerFallbackPower(currentServerIp);
                if (serverPreset != null && serverPreset > 0.0f) {
                    return serverPreset;
                }
            }

            // Tier 3: Dynamic Packet Radius Inference (explicit packet radius > 0)
            if (inferredPacketRadius != null && inferredPacketRadius > 0.0f) {
                return inferredPacketRadius;
            }

            // Tier 4: Dynamic Affected Block Estimation (radius <= 0 & affected blocks > 0)
            if (inferredBlockEstimation != null && inferredBlockEstimation > 0.0f) {
                return inferredBlockEstimation;
            }

            // Tier 5: Global Default Fallback
            return ModConfig.instance().globalFallbackFireballPower;
        }

        return 1.0F;
    }

    public static void setInferredPacketRadius(float power) {
        inferredPacketRadius = power;
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

    // Retain backward compatibility for existing code / tests
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
            return getClientServerIp();
        }
        return null;
    }

    private static String getClientServerIp() {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.getCurrentServerEntry() != null) {
            return client.getCurrentServerEntry().address;
        }
        return null;
    }
}
