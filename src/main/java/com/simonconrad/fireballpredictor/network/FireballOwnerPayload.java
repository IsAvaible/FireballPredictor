package com.simonconrad.fireballpredictor.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → client sync of a projectile's owner classification.
 *
 * @param entityId      projectile entity id on the client
 * @param ownerType     ordinal of {@code ProjectileOwner} (byte-sized)
 * @param ownerEntityId owner entity id, or {@code -1} when none / not tracked
 */
public record FireballOwnerPayload(int entityId, int ownerType, int ownerEntityId) implements CustomPayload {
    public static final CustomPayload.Id<FireballOwnerPayload> ID =
            new CustomPayload.Id<>(Identifier.of("fireballpredictor", "sync_owner"));

    public static final PacketCodec<RegistryByteBuf, FireballOwnerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, FireballOwnerPayload::entityId,
            PacketCodecs.VAR_INT, FireballOwnerPayload::ownerType,
            PacketCodecs.INTEGER, FireballOwnerPayload::ownerEntityId,
            FireballOwnerPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
