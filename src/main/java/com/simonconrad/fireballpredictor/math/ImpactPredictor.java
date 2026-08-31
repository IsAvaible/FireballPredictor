package com.simonconrad.fireballpredictor.math;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side replica of the 1.8.9 {@code Explosion.doExplosionA} block destruction
 * raycasting. 1352 rays over the surface of a 16x16x16 cube, 0.3 step size, 0.225
 * power decay. Like the original mod we use the upper bound of the vanilla random
 * factor (power * rayPowerMultiplier) to show the maximum possible destruction.
 */
public final class ImpactPredictor {

    private ImpactPredictor() {
    }

    private static final float[] RAY_DX = new float[1352];
    private static final float[] RAY_DY = new float[1352];
    private static final float[] RAY_DZ = new float[1352];

    static {
        int idx = 0;
        for (int j = 0; j < 16; ++j) {
            for (int k = 0; k < 16; ++k) {
                for (int l = 0; l < 16; ++l) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d = (float) j / 15.0F * 2.0F - 1.0F;
                        double e = (float) k / 15.0F * 2.0F - 1.0F;
                        double f = (float) l / 15.0F * 2.0F - 1.0F;
                        double g = Math.sqrt(d * d + e * e + f * f);
                        RAY_DX[idx] = (float) (d / g);
                        RAY_DY[idx] = (float) (e / g);
                        RAY_DZ[idx] = (float) (f / g);
                        idx++;
                    }
                }
            }
        }
    }

    /**
     * Predicts the block positions that would be removed by an explosion of the given
     * power at the given position.
     *
     * @param dangerous     charged wither skull (blast resistance of destructible blocks capped at 0.8)
     * @param rayMultiplier power multiplier for the rays (config, default 1.3 = vanilla upper bound)
     */
    public static List<BlockPos> predictBrokenBlocks(World world, Vec3 center, float power,
                                                     boolean dangerous, float rayMultiplier) {
        List<BlockPos> affected = new ArrayList<BlockPos>();
        if (power <= 0.0F) {
            return affected;
        }

        Set<BlockPos> set = new HashSet<BlockPos>();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (int i = 0; i < 1352; i++) {
            float rayPower = power * rayMultiplier;

            double x = center.xCoord;
            double y = center.yCoord;
            double z = center.zCoord;

            for (float step = 0.3F; rayPower > 0.0F; rayPower -= 0.225F) {
                mutable.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                IBlockState state = world.getBlockState(mutable);
                Block block = state.getBlock();

                if (block.getMaterial() != Material.air) {
                    float resistance = block.getExplosionResistance(world, mutable, null, null);

                    // Charged wither skulls cap the resistance of destructible blocks,
                    // replicating 1.8.9 EntityWitherSkull.getExplosionResistance.
                    if (dangerous && canWitherSkullBreak(block)) {
                        resistance = Math.min(0.8F, resistance);
                    }

                    rayPower -= (resistance + 0.3F) * 0.3F;
                }

                if (rayPower > 0.0F && block.getMaterial() != Material.air) {
                    set.add(new BlockPos(mutable));
                }

                x += RAY_DX[i] * 0.30000001192092896D;
                y += RAY_DY[i] * 0.30000001192092896D;
                z += RAY_DZ[i] * 0.30000001192092896D;
            }
        }

        affected.addAll(set);
        return affected;
    }

    /** 1.8.9 EntityWither.canDestroyBlock: wither skulls cannot destroy these blocks even when charged. */
    private static boolean canWitherSkullBreak(Block block) {
        return block != Blocks.bedrock
                && block != Blocks.end_portal
                && block != Blocks.end_portal_frame
                && block != Blocks.command_block
                && block != Blocks.barrier;
    }
}
