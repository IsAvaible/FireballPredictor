package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientWorldEntityTracker {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void fireballpredictor$onEntityAdded(Entity entity, CallbackInfo ci) {
        FireballPredictorClient.trackWorldEntity(entity);
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void fireballpredictor$onEntityRemoved(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        FireballPredictorClient.untrackWorldEntity(((ClientLevel) (Object) this).getEntity(entityId));
    }
}