package com.simonconrad.fireballpredictor;

import com.mojang.brigadier.CommandDispatcher;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.network.FireballOwnerPayload;
import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import com.simonconrad.fireballpredictor.network.MobGriefingPayload;
import com.simonconrad.fireballpredictor.network.TrackingRulesPayload;
import com.simonconrad.fireballpredictor.platform.PlatformHelper;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
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
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if fabric {
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
//?} elif neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
*///?}

//? if fabric {
public class FireballPredictor implements ModInitializer {
//?} elif neoforge {
/*@Mod(FireballPredictor.MOD_ID)
public class FireballPredictor {
*///?}
    public static final String MOD_ID = "fireballpredictor";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    //? if fabric {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(FireballPowerPayload.ID, FireballPowerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FireballOwnerPayload.ID, FireballOwnerPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrackingRulesPayload.ID, TrackingRulesPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MobGriefingPayload.ID, MobGriefingPayload.CODEC);

        initCommon();

        EntityTrackingEvents.START_TRACKING.register(FireballPredictor::onStartTracking);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));

        LOGGER.info("Hello Fabric world from FireballPredictor!");
    }
    //?} elif neoforge {
    /*public FireballPredictor(IEventBus modBus) {
        initCommon();

        modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar("1").optional();
            registrar.playToClient(FireballPowerPayload.ID, FireballPowerPayload.CODEC);
            registrar.playToClient(FireballOwnerPayload.ID, FireballOwnerPayload.CODEC);
            registrar.playToClient(TrackingRulesPayload.ID, TrackingRulesPayload.CODEC);
            registrar.playToClient(MobGriefingPayload.ID, MobGriefingPayload.CODEC);
        });

        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, event -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                onStartTracking(event.getTarget(), serverPlayer);
            }
        });

        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer serverPlayer) {
                onPlayerJoin(serverPlayer);
            }
        });

        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> registerCommands(event.getDispatcher()));

        LOGGER.info("Hello NeoForge world from FireballPredictor!");
    }
    *///?}

    private static void initCommon() {
        // Server-side fair-play switches (config/fireballpredictor-server.json)
        ServerConfig.load();
    }

    private static void onStartTracking(Entity entity, ServerPlayer player) {
        if (entity instanceof AbstractHurtingProjectile fireball) {
            float power = -1.0f;
            if (fireball instanceof LargeFireball largeFireball) {
                power = ((FireballEntityAccessor) largeFireball).getExplosionPower();
            }
            PlatformHelper.sendToPlayer(player, new FireballPowerPayload(fireball.getId(), power));

            // Authoritative owner sync when the mod is also present on the server
            ProjectileOwner owner = OwnerClassifier.resolveAuthoritative(fireball);
            Entity ownerEntity = fireball.getOwner();
            int ownerId = ownerEntity != null ? ownerEntity.getId() : -1;
            PlatformHelper.sendToPlayer(player, new FireballOwnerPayload(fireball.getId(), owner.name(), ownerId));
        }
    }

    private static void onPlayerJoin(ServerPlayer player) {
        PlatformHelper.sendToPlayer(player, trackingRulesPayload());
        boolean mobGriefing = player.level().getGameRules().get(GameRules.MOB_GRIEFING);
        PlatformHelper.sendToPlayer(player, new MobGriefingPayload(mobGriefing));
    }

    private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fireballpredictor")
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> {
                            int mask = ServerConfig.reload();
                            TrackingRulesPayload payload = new TrackingRulesPayload(mask);
                            for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
                                PlatformHelper.sendToPlayer(player, payload);
                                boolean mobGriefing = player.level().getGameRules().get(GameRules.MOB_GRIEFING);
                                PlatformHelper.sendToPlayer(player, new MobGriefingPayload(mobGriefing));
                            }
                            context.getSource().sendSuccess(
                                    () -> Component.translatable("fireballpredictor.command.reload.success"),
                                    true);
                            return 1;
                        }))
                .then(Commands.literal("preview")
                        .executes(context -> {
                            Component link = Component.literal("/fppreview").withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.YELLOW)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent.RunCommand("/fppreview"))
                                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("fireballpredictor.command.preview.toggle_hover"))));

                            MutableComponent msg = Component.translatable("fireballpredictor.command.preview.server_hint", link);

                            context.getSource().sendSuccess(() -> msg, false);
                            return 1;
                        })));
    }

    private static TrackingRulesPayload trackingRulesPayload() {
        return new TrackingRulesPayload(ServerConfig.instance().disabledOwnerMask());
    }
}
