package com.simonconrad.fireballpredictor;

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
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
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
        PayloadTypeRegistry.clientboundPlay().register(TrackingRulesPayload.ID, TrackingRulesPayload.CODEC);

        // Server-side fair-play switches (config/fireballpredictor-server.json)
        ServerConfig.load();

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof AbstractHurtingProjectile fireball) {
                float power = -1.0f;
                if (fireball instanceof LargeFireball largeFireball) {
                    power = ((FireballEntityAccessor) largeFireball).getExplosionPower();
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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("fireballpredictor")
                    .then(Commands.literal("reload")
                            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                            .executes(context -> {
                                int mask = ServerConfig.reload();
                                TrackingRulesPayload payload = new TrackingRulesPayload(mask);
                                for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                    ServerPlayNetworking.send(player, payload);
                                }
                                context.getSource().sendSuccess(
                                        () -> Component.literal("Reloaded Fireball Predictor server config and re-synced tracking restrictions to all players."),
                                        true);
                                return 1;
                            }))
                    .then(Commands.literal("preview")
                            .executes(context -> {
                                Component link = Component.literal("/fppreview").withStyle(Style.EMPTY
                                        .withColor(ChatFormatting.YELLOW)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent.RunCommand("/fppreview"))
                                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to toggle Theme Preview Gallery"))));

                                MutableComponent msg = Component.literal("§6[Fireball Predictor]§a Run ")
                                        .append(link)
                                        .append(" or use the config menu button to toggle the 3D circular theme preview gallery.");

                                context.getSource().sendSuccess(() -> msg, false);
                                return 1;
                            })));
        });

		LOGGER.info("Hello Fabric world from FireballPredictor!");
	}

    private static TrackingRulesPayload trackingRulesPayload() {
        return new TrackingRulesPayload(ServerConfig.instance().disabledOwnerMask());
    }
}
