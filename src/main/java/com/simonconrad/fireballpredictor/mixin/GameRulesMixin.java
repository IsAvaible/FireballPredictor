package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.network.MobGriefingPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRules.class)
public class GameRulesMixin {

    @Inject(method = "set", at = @At("RETURN"))
    private <T> void fireballpredictor$onGameRuleSet(GameRule<T> rule, T value, MinecraftServer server, CallbackInfo ci) {
        if (rule == GameRules.MOB_GRIEFING && value instanceof Boolean boolValue && server != null && server.getPlayerList() != null) {
            MobGriefingPayload payload = new MobGriefingPayload(boolValue);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
