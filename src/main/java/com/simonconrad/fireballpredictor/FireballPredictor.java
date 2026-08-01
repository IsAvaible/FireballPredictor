package com.simonconrad.fireballpredictor;

import com.simonconrad.fireballpredictor.network.FireballOwnerPayload;
import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
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
        PayloadTypeRegistry.clientboundPlay().register(FireballOwnerPayload.ID, FireballOwnerPayload.CODEC);

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof AbstractHurtingProjectile fireball) {
                float power = 1.0F;
                if (fireball instanceof LargeFireball fe) {
                    power = (float) ((FireballEntityAccessor) fe).getExplosionPower();
                }
                ServerPlayNetworking.send(player, new FireballPowerPayload(fireball.getId(), power));

                // Authoritative owner sync when the mod is also present on the server
                ProjectileOwner owner = OwnerClassifier.resolveAuthoritative(fireball);
                Entity ownerEntity = fireball.getOwner();
                int ownerId = ownerEntity != null ? ownerEntity.getId() : -1;
                ServerPlayNetworking.send(player, new FireballOwnerPayload(fireball.getId(), owner.ordinal(), ownerId));
            }
        });

		LOGGER.info("Hello Fabric world from FireballPredictor!");
	}
}
