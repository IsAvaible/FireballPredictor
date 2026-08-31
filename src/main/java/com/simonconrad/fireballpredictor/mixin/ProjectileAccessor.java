package com.simonconrad.fireballpredictor.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ProjectileEntity.class)
public interface ProjectileAccessor {
    @Invoker("canHit")
    boolean fireballpredictor$canHitEntity(Entity entity);
}
