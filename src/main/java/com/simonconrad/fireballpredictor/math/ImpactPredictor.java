package com.simonconrad.fireballpredictor.math;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class ImpactPredictor {

    public static float resolveExplosionPower(AbstractHurtingProjectile fireball) {
        if (!fireball.level().isClientSide()) {
            if (fireball instanceof net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball f) {
                return ((com.simonconrad.fireballpredictor.FireballEntityAccessor) f).getExplosionPower();
            }
            return 1.0F;
        }

        return com.simonconrad.fireballpredictor.client.network.ClientPowerLookup.getPower(fireball);
    }



    public static List<BlockPos> predictBrokenBlocks(AbstractHurtingProjectile fireball, Vec3 explosionPos, BlockGetter world) {
        float power = resolveExplosionPower(fireball);
        
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
                        // We use the upper bound (1.3F) to show the "maximum possible" destruction.
                        float rayPowerMultiplier = com.simonconrad.fireballpredictor.config.ModConfig.instance().rayPowerMultiplier;
                        float rayPower = power * rayPowerMultiplier;
                        
                        double x = explosionPos.x;
                        double y = explosionPos.y;
                        double z = explosionPos.z;

                        for (float step = 0.3F; rayPower > 0.0F; rayPower -= 0.225F) {
                            BlockPos blockPos = BlockPos.containing(x, y, z);

                            if (world.isOutsideBuildHeight(blockPos.getY())) {
                                break;
                            }

                            BlockState blockState = world.getBlockState(blockPos);
                            FluidState fluidState = world.getFluidState(blockPos);

                            // Combined blast resistance of block and fluid (e.g. waterlogging)
                            float blastResistance = Math.max(blockState.getBlock().getExplosionResistance(), fluidState.getExplosionResistance());
                            
                            // Charged wither skulls cap the blast resistance of destructible blocks at 0.8F
                            if (fireball instanceof WitherSkull witherSkull && witherSkull.isDangerous()) {
                                if (blockState.getDestroySpeed(world, blockPos) >= 0.0F) {
                                    blastResistance = Math.min(0.8F, blastResistance);
                                }
                            }
                            
                            if (!blockState.isAir() || !fluidState.isEmpty()) {
                                rayPower -= (blastResistance + 0.3F) * 0.3F;
                            }

                            if (rayPower > 0.0F && !blockState.isAir()) {
                                affectedBlocks.add(blockPos);
                            }

                            x += d * step;
                            y += e * step;
                            z += f * step;
                        }
                    }
                }
            }
        }
        
        return List.copyOf(affectedBlocks);
    }
}
