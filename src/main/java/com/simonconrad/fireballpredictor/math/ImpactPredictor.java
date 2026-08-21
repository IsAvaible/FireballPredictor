package com.simonconrad.fireballpredictor.math;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.simonconrad.fireballpredictor.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.projectile.ProjectileProfile;
import com.simonconrad.fireballpredictor.projectile.VanillaProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class ImpactPredictor {

    /**
     * Resolves the explosion power for a live projectile by looking up its {@link ProjectileProfile}.
     */
    public static float resolveExplosionPower(AbstractHurtingProjectile fireball) {
        return resolveExplosionPower(VanillaProfiles.from(fireball), fireball);
    }

    /**
     * Resolves the explosion power from a {@link ProjectileProfile}.
     *
     * <p>Most projectiles have a static power baked into their profile (wind charges, wither skulls,
     * and the zero-power small/dragon fireballs). Large fireballs are dynamic: their power comes from
     * the server-side {@link FireballEntityAccessor} or, on the client, from {@link ClientPowerLookup}.
     */
    public static float resolveExplosionPower(ProjectileProfile profile, AbstractHurtingProjectile fireball) {
        if (profile == null) {
            return 1.0F;
        }

        // Zero-blast projectiles (SmallFireball, DragonFireball) report 0.
        if (profile.staticExplosionPower() <= 0.0F) {
            return 0.0F;
        }

        if (profile.dynamicExplosionPower()) {
            if (!fireball.level().isClientSide()) {
                return fireball instanceof FireballEntityAccessor accessor ? accessor.getExplosionPower() : profile.staticExplosionPower();
            }
            return ClientPowerLookup.getPower(fireball);
        }

        return profile.staticExplosionPower();
    }

    public static List<BlockPos> predictBrokenBlocks(
            float power, ProjectileProfile profile, boolean isDangerous, Vec3 explosionPos, BlockGetter world) {
        if (!profile.breaksBlocks() || power <= 0.0f) {
            // Wind charges and zero-blast projectiles do not break blocks.
            return List.of();
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
                            if (isDangerous) {
                                if (WitherBoss.canDestroy(blockState)) {
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
