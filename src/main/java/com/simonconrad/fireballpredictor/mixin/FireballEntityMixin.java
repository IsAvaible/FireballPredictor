package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireballEntity.class)
public abstract class FireballEntityMixin implements FireballEntityAccessor {
    @Shadow
    private int explosionPower;

    @Override
    public int getExplosionPower() {
        return this.explosionPower;
    }

    @Override
    public void setExplosionPower(int explosionPower) {
        this.explosionPower = explosionPower;
        fireballpredictor$syncPower();
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void fireballpredictor$onReadCustomData(net.minecraft.storage.ReadView nbt, CallbackInfo ci) {
        fireballpredictor$syncPower();
    }

    @Unique
    private void fireballpredictor$syncPower() {
        FireballEntity fireball = (FireballEntity) (Object) this;
        if (fireball.getEntityWorld() != null && !fireball.getEntityWorld().isClient()) {
            for (ServerPlayerEntity player : PlayerLookup.tracking(fireball)) {
                ServerPlayNetworking.send(player, new FireballPowerPayload(fireball.getId(), (float) this.explosionPower));
            }
        }
    }
}
