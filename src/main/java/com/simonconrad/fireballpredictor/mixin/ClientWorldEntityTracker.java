package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import net.minecraft.entity.Entity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldEntityTracker {

    @Inject(method = "addEntity", at = @At("TAIL"))
    private void fireballpredictor$onEntityAdded(Entity entity, CallbackInfo ci) {
        FireballPredictorClient.trackWorldEntity(entity);
    }

    @Inject(method = "removeEntity", at = @At("HEAD"))
    private void fireballpredictor$onEntityRemoved(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        FireballPredictorClient.untrackWorldEntity(((ClientWorld) (Object) this).getEntityById(entityId));
    }
}