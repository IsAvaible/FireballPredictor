package com.simonconrad.fireballpredictor.mixin;

import net.minecraft.entity.projectile.FireballEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireballEntity.class)
public interface FireballEntityAccessor {
    @Accessor("explosionPower")
    int getExplosionPower();

    @Accessor("explosionPower")
    void setExplosionPower(int explosionPower);
}
