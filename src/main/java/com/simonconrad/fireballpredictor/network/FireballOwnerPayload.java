package com.simonconrad.fireballpredictor.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client sync of a projectile's owner classification.
 *
 * @param entityId      projectile entity id on the client
 * @param ownerName     stable enum name of {@code ProjectileOwner}
 * @param ownerEntityId owner entity id, or {@code -1} when none / not tracked
 */
public record FireballOwnerPayload(int entityId, String ownerName, int ownerEntityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FireballOwnerPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fireballpredictor", "sync_owner"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FireballOwnerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FireballOwnerPayload::entityId,
            ByteBufCodecs.STRING_UTF8, FireballOwnerPayload::ownerName,
            ByteBufCodecs.INT, FireballOwnerPayload::ownerEntityId,
            FireballOwnerPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
