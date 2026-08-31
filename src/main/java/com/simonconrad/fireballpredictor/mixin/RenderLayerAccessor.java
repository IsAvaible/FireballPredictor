package com.simonconrad.fireballpredictor.mixin;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for the package-private {@code RenderLayer.of(String, RenderSetup)} factory.
 *
 * <p>1.21.11 exposes no public way to create a custom {@link RenderLayer} from a mod-owned
 * {@code RenderPipeline}; this invoker is the supported escape hatch and is the 1.21.11
 * counterpart of 26.2's {@code RenderType.create(...)}.
 */
@Mixin(RenderLayer.class)
public interface RenderLayerAccessor {

    @Invoker("of")
    static RenderLayer fireballpredictor$create(String name, RenderSetup setup) {
        throw new AssertionError("mixin");
    }
}
