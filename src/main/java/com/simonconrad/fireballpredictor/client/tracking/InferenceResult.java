package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of the owner inference pipeline for a single projectile.
 *
 * @param owner   classified origin used by config filters
 * @param entity  resolved owner entity when available (may be {@code null} for dispenser/command)
 * @param source  which tier of the fallback chain produced this result
 */
public record InferenceResult(
        ProjectileOwner owner,
        @Nullable Entity entity,
        InferenceSource source,
        boolean isDeflected
) {
    public enum InferenceSource {
        /** Singleplayer / integrated server: vanilla NBT owner reference resolved. */
        NATIVE_NBT,
        /** Dedicated server with this mod installed: custom sync packet. */
        SERVER_PACKET,
        /** Client-side radius sweep + look-vector match. */
        ENVIRONMENTAL_SWEEP,
        /** No entity match; adjacent facing dispenser found. */
        DISPENSER_FALLBACK,
        /** Nothing matched — treated as command/unknown spawn. */
        UNKNOWN
    }

    public static InferenceResult unknown() {
        return new InferenceResult(ProjectileOwner.COMMAND, null, InferenceSource.UNKNOWN, false);
    }

    public static InferenceResult of(ProjectileOwner owner, @Nullable Entity entity, InferenceSource source) {
        return new InferenceResult(owner, entity, source, false);
    }

    public static InferenceResult of(ProjectileOwner owner, @Nullable Entity entity, InferenceSource source, boolean isDeflected) {
        return new InferenceResult(owner, entity, source, isDeflected);
    }
}
