package com.simonconrad.fireballpredictor.math;

import net.minecraft.block.BlockState;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import com.simonconrad.fireballpredictor.mixin.FireballEntityAccessor;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ImpactPredictor {

    public static List<BlockPos> predictBrokenBlocks(ExplosiveProjectileEntity fireball, Vec3d explosionPos) {
        World world = fireball.getWorld();
        
        float power;
        
        if (com.simonconrad.fireballpredictor.client.network.ClientPowerCache.POWER_CACHE.containsKey(fireball.getId())) {
            power = com.simonconrad.fireballpredictor.client.network.ClientPowerCache.POWER_CACHE.get(fireball.getId());
        } else {
            if (fireball instanceof net.minecraft.entity.projectile.FireballEntity) {
                power = com.simonconrad.fireballpredictor.config.ModConfig.instance().clientFallbackFireballPower;
            } else if (fireball instanceof net.minecraft.entity.projectile.WitherSkullEntity) {
                power = 1.0F; 
            } else {
                power = 1.0F; 
            }
        }
        
        Set<BlockPos> affectedBlocks = new HashSet<>();

        // Vanilla explosion algorithm creates 16 rays per side of a 16x16x16 cube
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d = (float)j / 15.0F * 2.0F - 1.0F;
                        double e = (float)k / 15.0F * 2.0F - 1.0F;
                        double f = (float)l / 15.0F * 2.0F - 1.0F;
                        double g = Math.sqrt(d * d + e * e + f * f);
                        d /= g;
                        e /= g;
                        f /= g;

                        // Vanilla uses random: power * (0.7F + world.random.nextFloat() * 0.6F)
                        // We use the exact middle (1.0F) for deterministic, stable prediction.
                        // You can adjust this to 1.3F to show the "maximum possible" destruction instead.
                        float rayPower = power * 1.15F;
                        
                        double x = explosionPos.x;
                        double y = explosionPos.y;
                        double z = explosionPos.z;

                        for (float step = 0.3F; rayPower > 0.0F; rayPower -= 0.225F) {
                            BlockPos blockPos = BlockPos.ofFloored(x, y, z);

                            if (!world.isInBuildLimit(blockPos)) {
                                break;
                            }

                            BlockState blockState = world.getBlockState(blockPos);
                            FluidState fluidState = world.getFluidState(blockPos);

                            // Use block's base resistance directly
                            float blastResistance = blockState.getBlock().getBlastResistance();
                            
                            if (!blockState.isAir() || !fluidState.isEmpty()) {
                                rayPower -= (blastResistance + 0.3F) * 0.3F;
                            }

                            if (rayPower > 0.0F && !blockState.isAir()) {
                                affectedBlocks.add(blockPos);
                            }

                            x += d * 0.3F;
                            y += e * 0.3F;
                            z += f * 0.3F;
                        }
                    }
                }
            }
        }
        
        return List.copyOf(affectedBlocks);
    }
}
