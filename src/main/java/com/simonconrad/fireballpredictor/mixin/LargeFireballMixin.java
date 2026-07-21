package com.simonconrad.fireballpredictor.mixin;

import com.simonconrad.fireballpredictor.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.network.FireballPowerPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LargeFireball.class)
public abstract class LargeFireballMixin implements FireballEntityAccessor {
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

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void fireballpredictor$onReadAdditionalSaveData(net.minecraft.world.level.storage.ValueInput tag, CallbackInfo ci) {
        fireballpredictor$syncPower();
    }

    @Unique
    private void fireballpredictor$syncPower() {
        LargeFireball fireball = (LargeFireball) (Object) this;
        if (fireball.level() != null && !fireball.level().isClientSide()) {
            for (ServerPlayer player : PlayerLookup.tracking(fireball)) {
                ServerPlayNetworking.send(player, new FireballPowerPayload(fireball.getId(), (float) this.explosionPower));
            }
        }
    }
}
