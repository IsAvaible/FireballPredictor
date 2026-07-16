package com.simonconrad.fireballpredictor.client.network;

import com.simonconrad.fireballpredictor.config.ModConfig;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;

public class ClientPowerLookup {
    public static float getPower(AbstractHurtingProjectile fireball) {
        if (ClientPowerCache.POWER_CACHE.containsKey(fireball.getId())) {
            return ClientPowerCache.POWER_CACHE.get(fireball.getId());
        }

        if (fireball instanceof LargeFireball) {
            return ModConfig.instance().clientFallbackFireballPower;
        }

        return 1.0F;
    }
}
