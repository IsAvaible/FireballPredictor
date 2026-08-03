package com.simonconrad.fireballpredictor.client.tracking;

import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * Outcome of the owner inference pipeline for a single projectile.
 *
 * @param owner     classified origin used by config filters
 * @param entityRef weak reference to resolved owner entity when available (may be {@code null} for dispenser/command)
 * @param source    which tier of the fallback chain produced this result
 */
public record InferenceResult(
        ProjectileOwner owner,
        @Nullable WeakReference<Entity> entityRef,
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

    /**
     * Resolves the owner entity from the weak reference if still reachable.
     */
    public @Nullable Entity entity() {
        return entityRef != null ? entityRef.get() : null;
    }

    public static InferenceResult unknown() {
        return new InferenceResult(ProjectileOwner.COMMAND, null, InferenceSource.UNKNOWN, false);
    }

    public static InferenceResult of(ProjectileOwner owner, @Nullable Entity entity, InferenceSource source) {
        return new InferenceResult(owner, entity != null ? new WeakReference<>(entity) : null, source, false);
    }

    public static InferenceResult of(ProjectileOwner owner, @Nullable Entity entity, InferenceSource source, boolean isDeflected) {
        return new InferenceResult(owner, entity != null ? new WeakReference<>(entity) : null, source, isDeflected);
    }
}
