package com.simonconrad.fireballpredictor.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FireballPowerPayload(int entityId, float power) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FireballPowerPayload> ID = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fireballpredictor", "sync_power"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FireballPowerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FireballPowerPayload::entityId,
            ByteBufCodecs.FLOAT, FireballPowerPayload::power,
            FireballPowerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
