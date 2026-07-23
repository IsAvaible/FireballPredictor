package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;

public class ClientPowerLookup {
    private static volatile Float inferredPacketRadius = null;
    private static volatile Float inferredBlockEstimation = null;

    public static float getPower(AbstractHurtingProjectile fireball) {
        if (ClientPowerCache.POWER_CACHE.containsKey(fireball.getId())) {
            return ClientPowerCache.POWER_CACHE.get(fireball.getId());
        }

        if (FireballInferenceTracker.isFireball(fireball)) {
            if (inferredPacketRadius != null && inferredPacketRadius > 0.0f) {
                return inferredPacketRadius;
            }

            String currentServerIp = getCurrentServerIp();
            if (currentServerIp != null) {
                Float serverPreset = ModConfig.instance().getServerFallbackPower(currentServerIp);
                if (serverPreset != null && serverPreset > 0.0f) {
                    return serverPreset;
                }
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
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.getCurrentServer() != null) {
            return client.getCurrentServer().ip;
        }
        return null;
    }
}
