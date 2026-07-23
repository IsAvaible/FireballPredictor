package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.client.network.ExplosionInferenceHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void fireballpredictor$onExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) {
            return;
        }

        Vec3 pos = packet.center();
        float radius = packet.radius();
        int blockCount = packet.blockCount();

        // if (client.player != null) {
        //     client.player.sendSystemMessage(
        //         net.minecraft.network.chat.Component.literal(
        //             String.format("§e[Debug] §fExplosion at (%.1f, %.1f, %.1f) | Radius: %.2f", 
        //             pos.x, pos.y, pos.z, radius)
        //         )
        //     );
        // }

        ExplosionInferenceHandler.onExplosion(pos, radius, blockCount, null);
    }
}
