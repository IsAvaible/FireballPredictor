package com.simonconrad.fireballpredictor.math;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class BlockStateSnapshot implements BlockGetter {
    private final BlockPos min;
    private final BlockPos max;
    private final int xSize;
    private final int ySize;
    private final int zSize;
    private final BlockState[] states;
    private final FluidState[] fluids;
    private final int bottomY;
    private final int height;

    public BlockStateSnapshot(Level world, BlockPos min, BlockPos max) {
        this.min = min;
        this.max = max;
        this.xSize = max.getX() - min.getX() + 1;
        this.ySize = max.getY() - min.getY() + 1;
        this.zSize = max.getZ() - min.getZ() + 1;
        this.bottomY = world.getMinY();
        this.height = world.getHeight();
        
        int size = xSize * ySize * zSize;
        this.states = new BlockState[size];
        this.fluids = new FluidState[size];

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < xSize; x++) {
            for (int y = 0; y < ySize; y++) {
                for (int z = 0; z < zSize; z++) {
                    mutable.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    int index = getIndex(x, y, z);
                    states[index] = world.getBlockState(mutable);
                    fluids[index] = world.getFluidState(mutable);
                }
            }
        }
    }

    private int getIndex(int x, int y, int z) {
        return x + y * xSize + z * xSize * ySize;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null; // Explosion math doesn't use block entities
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX() - min.getX();
        int y = pos.getY() - min.getY();
        int z = pos.getZ() - min.getZ();
        if (x < 0 || x >= xSize || y < 0 || y >= ySize || z < 0 || z >= zSize) {
            return Blocks.AIR.defaultBlockState();
        }
        return states[getIndex(x, y, z)];
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        int x = pos.getX() - min.getX();
        int y = pos.getY() - min.getY();
        int z = pos.getZ() - min.getZ();
        if (x < 0 || x >= xSize || y < 0 || y >= ySize || z < 0 || z >= zSize) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return fluids[getIndex(x, y, z)];
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public int getMinY() {
        return this.bottomY;
    }
}
