package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.client.network.ExplosionInferenceHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onExplosion", at = @At("HEAD"))
    private void fireballpredictor$onExplosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        // NetworkThreadUtils.forceMainThread in vanilla onExplosion re-invokes this method on the main render thread.
        // We filter out the initial off-thread Netty invocation to prevent duplicate execution.
        if (!client.isOnThread()) {
            return;
        }

        Vec3d pos = packet.center();
        float radius = packet.radius();
        int blockCount = packet.blockCount();

        // if (client.player != null) {
        //     client.player.sendMessage(
        //         net.minecraft.text.Text.literal(
        //             String.format("§e[Debug] §fExplosion at (%.1f, %.1f, %.1f) | Radius: %.2f", 
        //             pos.x, pos.y, pos.z, radius)
        //         ), false
        //     );
        // }
        
        ExplosionInferenceHandler.onExplosion(pos, radius, blockCount, null);
    }
}


