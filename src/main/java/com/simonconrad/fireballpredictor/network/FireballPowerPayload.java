package com.simonconrad.fireballpredictor.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record FireballPowerPayload(int entityId, float power) implements CustomPayload {
    public static final CustomPayload.Id<FireballPowerPayload> ID = new CustomPayload.Id<>(Identifier.of("fireballpredictor", "sync_power"));

    public static final PacketCodec<RegistryByteBuf, FireballPowerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, FireballPowerPayload::entityId,
            PacketCodecs.FLOAT, FireballPowerPayload::power,
            FireballPowerPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
