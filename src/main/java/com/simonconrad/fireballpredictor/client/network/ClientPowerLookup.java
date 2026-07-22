package com.simonconrad.fireballpredictor.client.network;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import com.simonconrad.fireballpredictor.config.ModConfig;

public class ClientPowerLookup {
    private static volatile Float inferredFireballPower = null;

    public static float getPower(ExplosiveProjectileEntity fireball) {
        if (ClientPowerCache.POWER_CACHE.containsKey(fireball.getId())) {
            return ClientPowerCache.POWER_CACHE.get(fireball.getId());
        }

        if (FireballInferenceTracker.isFireball(fireball)) {
            if (inferredFireballPower != null && inferredFireballPower > 0.0f) {
                return inferredFireballPower;
            }
            return ModConfig.instance().clientFallbackFireballPower;
        }

        return 1.0F;
    }

    public static void setInferredFireballPower(float power) {
        inferredFireballPower = power;
    }

    public static Float getInferredFireballPower() {
        return inferredFireballPower;
    }

    public static void resetInferredPower() {
        inferredFireballPower = null;
    }
}
