package com.simonconrad.fireballpredictor.client.network;

import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.FireballEntity;
import com.simonconrad.fireballpredictor.config.ModConfig;

public class ClientPowerLookup {
    public static float getPower(ExplosiveProjectileEntity fireball) {
        if (ClientPowerCache.POWER_CACHE.containsKey(fireball.getId())) {
            return ClientPowerCache.POWER_CACHE.get(fireball.getId());
        }

        if (fireball instanceof FireballEntity) {
            return ModConfig.instance().clientFallbackFireballPower;
        }

        return 1.0F;
    }
}
