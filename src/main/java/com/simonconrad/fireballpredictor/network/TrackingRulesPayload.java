package com.simonconrad.fireballpredictor.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server → client push of server-wide tracking restrictions for the
 * "other" owner category (player / dispenser / command projectiles).
 *
 * <p>Sent to each player on join and re-broadcast after
 * {@code /fireballpredictor reload}. A mask of {@code 0} lifts all
 * restrictions. See {@code TrackingRules} for the bit layout.
 *
 * @param disabledOwnerMask bitmask of {@code TrackingRules} bits disabled by the server
 */
public record TrackingRulesPayload(int disabledOwnerMask) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TrackingRulesPayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("fireballpredictor", "tracking_rules"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrackingRulesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrackingRulesPayload::disabledOwnerMask,
            TrackingRulesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
