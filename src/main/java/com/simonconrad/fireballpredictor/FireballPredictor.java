package com.simonconrad.fireballpredictor;

import com.simonconrad.fireballpredictor.mixin.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FireballPredictor implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MOD_ID = "fireballpredictor";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        PayloadTypeRegistry.clientboundPlay().register(FireballPowerPayload.ID, FireballPowerPayload.CODEC);

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof AbstractHurtingProjectile fireball) {
                float power = 1.0F;
                if (fireball instanceof LargeFireball fe) {
                    power = (float) ((FireballEntityAccessor) fe).getExplosionPower();
                }
                ServerPlayNetworking.send(player, new FireballPowerPayload(fireball.getId(), power));
            }
        });

		LOGGER.info("Hello Fabric world from FireballPredictor!");
	}
}
