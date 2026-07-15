package com.simonconrad.fireballpredictor.math;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;

import java.util.List;

public class ImpactPredictor {

    public static List<BlockPos> predictBrokenBlocks(ExplosiveProjectileEntity fireball, HitResult hitResult) {
        World world = fireball.getWorld();
        Vec3d pos = hitResult.getPos();
        
        float power = 1.0F; // Default Ghast fireball power
        if (fireball instanceof FireballEntity fireballEntity) {
            try {
                java.lang.reflect.Field field = FireballEntity.class.getDeclaredField("explosionPower");
                field.setAccessible(true);
                power = (int) field.get(fireballEntity);
            } catch (Exception e) {
                // Ignore and use default
            }
        }
        
        // Custom behavior to prevent any entity damage or knockback during our prediction
        ExplosionBehavior safeBehavior = new ExplosionBehavior() {
            @Override
            public boolean shouldDamage(Explosion explosion, Entity entity) {
                return false;
            }
            
            @Override
            public float getKnockbackModifier(Entity entity) {
                return 0.0F;
            }
        };
        
        // Create a dummy explosion to calculate the affected blocks.
        // We pass null for particles and sounds since we won't call affectWorld().
        Explosion explosion = new Explosion(
            world,
            fireball,
            null, // damageSource
            safeBehavior,
            pos.x, pos.y, pos.z,
            power,
            false, // createFire
            Explosion.DestructionType.DESTROY,
            null, // particle
            null, // emitterParticle
            null  // soundEvent
        );
        
        // This calculates raycasts and populates the affected blocks list.
        // Because we use safeBehavior, no entities will be damaged.
        explosion.collectBlocksAndDamageEntities();
        
        return explosion.getAffectedBlocks();
    }
}
