package com.simonconrad.fireballpredictor;

import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.network.FireballOwnerPayload;
import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import com.simonconrad.fireballpredictor.network.TrackingRulesPayload;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.FireballEntity;
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
		// This code runs as soon as MinecraftClient is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

        PayloadTypeRegistry.playS2C().register(FireballPowerPayload.ID, FireballPowerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FireballOwnerPayload.ID, FireballOwnerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TrackingRulesPayload.ID, TrackingRulesPayload.CODEC);

        // Server-side fair-play switches (config/fireballpredictor-server.json)
        ServerConfig.load();

        EntityTrackingEvents.START_TRACKING.register((trackedEntity, player) -> {
            if (trackedEntity instanceof ExplosiveProjectileEntity fireball) {
                float power = 1.0F;
                if (fireball instanceof FireballEntity fe) {
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

        // Push the server's tracking restrictions (disabled "other" owner options) to joining clients
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerPlayNetworking.send(handler.player, trackingRulesPayload()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("fireballpredictor")
                        .requires(source -> source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.GAMEMASTERS)))
                        .then(CommandManager.literal("reload").executes(context -> {
                            int mask = ServerConfig.reload();
                            TrackingRulesPayload payload = new TrackingRulesPayload(mask);
                            for (ServerPlayerEntity player : context.getSource().getServer().getPlayerManager().getPlayerList()) {
                                ServerPlayNetworking.send(player, payload);
                            }
                            context.getSource().sendFeedback(
                                    () -> Text.literal("Reloaded Fireball Predictor server config and re-synced tracking restrictions to all players."),
                                    true);
                            return 1;
                        }))));

		LOGGER.info("Hello Fabric world from FireballPredictor!");
	}

    private static TrackingRulesPayload trackingRulesPayload() {
        return new TrackingRulesPayload(ServerConfig.instance().disabledOwnerMask());
    }
}
