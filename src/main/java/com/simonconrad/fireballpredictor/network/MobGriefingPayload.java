package com.simonconrad.fireballpredictor.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client sync of the active {@code mobGriefing} gamerule.
 *
 * @param mobGriefing whether mob griefing is currently enabled on the server
 */
public record MobGriefingPayload(boolean mobGriefing) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MobGriefingPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fireballpredictor", "sync_mob_griefing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MobGriefingPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MobGriefingPayload::mobGriefing,
            MobGriefingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
