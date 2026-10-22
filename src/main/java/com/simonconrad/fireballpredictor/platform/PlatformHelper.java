package com.simonconrad.fireballpredictor.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.nio.file.Path;

//? if fabric {
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
//?} elif neoforge {
/*import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerChunkCache;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
*///?}

/**
 * Thin multi-loader platform abstraction for config paths, mod presence queries,
 * physical side detection, and guarded server-to-client packet transmission.
 */
public final class PlatformHelper {

    private PlatformHelper() {
    }

    public static Path getConfigDir() {
        //? if fabric {
        return FabricLoader.getInstance().getConfigDir();
        //?} elif neoforge {
        /*return FMLPaths.CONFIGDIR.get();
        *///?}
    }

    public static boolean isModLoaded(String modId) {
        //? if fabric {
        return FabricLoader.getInstance().isModLoaded(modId);
        //?} elif neoforge {
        /*return ModList.get().isLoaded(modId);
        *///?}
    }

    public static boolean isClient() {
        //? if fabric {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        //?} elif neoforge {
        /*return FMLEnvironment.getDist().isClient();
        *///?}
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player == null || payload == null) {
            return;
        }
        //? if fabric {
        if (ServerPlayNetworking.canSend(player, payload.type())) {
            ServerPlayNetworking.send(player, payload);
        }
        //?} elif neoforge {
        /*if (player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
        *///?}
    }

    public static void sendToTrackingPlayers(Entity entity, CustomPacketPayload payload) {
        if (entity == null || payload == null || entity.level() == null || entity.level().isClientSide()) {
            return;
        }
        //? if fabric {
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            sendToPlayer(player, payload);
        }
        //?} elif neoforge {
        /*if (entity.level().getChunkSource() instanceof ServerChunkCache chunkCache) {
            chunkCache.sendToTrackingPlayersFiltered(
                    entity,
                    new ClientboundCustomPayloadPacket(payload),
                    player -> player.connection != null && player.connection.hasChannel(payload)
            );
        }
        *///?}
    }
}
