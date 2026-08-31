package com.simonconrad.fireballpredictor.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

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
public record TrackingRulesPayload(int disabledOwnerMask) implements CustomPayload {
    public static final CustomPayload.Id<TrackingRulesPayload> ID =
            new CustomPayload.Id<>(Identifier.of("fireballpredictor", "tracking_rules"));

    public static final PacketCodec<RegistryByteBuf, TrackingRulesPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, TrackingRulesPayload::disabledOwnerMask,
            TrackingRulesPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
