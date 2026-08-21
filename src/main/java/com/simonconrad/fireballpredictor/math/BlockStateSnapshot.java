package com.simonconrad.fireballpredictor.math;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class BlockStateSnapshot implements BlockGetter {
    public static final int MAX_SNAPSHOT_BLOCKS = 65_536;
    public static final float MAX_SNAPSHOT_POWER = 9.0f;

    private final BlockPos min;
    private final BlockPos max;
    private final int xSize;
    private final int ySize;
    private final int zSize;
    private final BlockState[] states;
    private final FluidState[] fluids;
    private final int bottomY;
    private final int height;

    @Nullable
    public static BlockStateSnapshot create(Level world, Vec3 hitPos, float explosionPower) {
        if (world == null || hitPos == null || explosionPower <= 0.0f || explosionPower > MAX_SNAPSHOT_POWER) {
            return null;
        }

        float radius = explosionPower * 2.0f;

        int minY = world.getMinY();
        int maxY = world.getMaxY() - 1;

        int minX = (int) Math.floor(hitPos.x - radius - 2);
        int maxX = (int) Math.floor(hitPos.x + radius + 2);
        int minClampedY = Math.max(minY, (int) Math.floor(hitPos.y - radius - 2));
        int maxClampedY = Math.min(maxY, (int) Math.floor(hitPos.y + radius + 2));
        int minZ = (int) Math.floor(hitPos.z - radius - 2);
        int maxZ = (int) Math.floor(hitPos.z + radius + 2);

        int xSize = maxX - minX + 1;
        int ySize = maxClampedY - minClampedY + 1;
        int zSize = maxZ - minZ + 1;

        if (xSize <= 0 || ySize <= 0 || zSize <= 0) {
            return null;
        }

        long volume = (long) xSize * ySize * zSize;
        if (volume > MAX_SNAPSHOT_BLOCKS) {
            return null;
        }

        BlockPos minPos = new BlockPos(minX, minClampedY, minZ);
        BlockPos maxPos = new BlockPos(maxX, maxClampedY, maxZ);
        return new BlockStateSnapshot(world, minPos, maxPos);
    }

    public BlockStateSnapshot(Level world, BlockPos min, BlockPos max) {
        this.min = min;
        this.max = max;
        this.xSize = Math.max(0, max.getX() - min.getX() + 1);
        this.ySize = Math.max(0, max.getY() - min.getY() + 1);
        this.zSize = Math.max(0, max.getZ() - min.getZ() + 1);
        this.bottomY = world.getMinY();
        this.height = world.getHeight();
        
        long volume = (long) xSize * ySize * zSize;
        if (volume > MAX_SNAPSHOT_BLOCKS || volume <= 0) {
            this.states = new BlockState[0];
            this.fluids = new FluidState[0];
            return;
        }

        int size = (int) volume;
        this.states = new BlockState[size];
        this.fluids = new FluidState[size];

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = 0; x < xSize; x++) {
            for (int y = 0; y < ySize; y++) {
                for (int z = 0; z < zSize; z++) {
                    mutable.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    int index = getIndex(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (!state.isAir()) {
                        states[index] = state;
                    }
                    FluidState fluid = world.getFluidState(mutable);
                    if (!fluid.isEmpty()) {
                        fluids[index] = fluid;
                    }
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
        if (x < 0 || x >= xSize || y < 0 || y >= ySize || z < 0 || z >= zSize || states.length == 0) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = states[getIndex(x, y, z)];
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        int x = pos.getX() - min.getX();
        int y = pos.getY() - min.getY();
        int z = pos.getZ() - min.getZ();
        if (x < 0 || x >= xSize || y < 0 || y >= ySize || z < 0 || z >= zSize || fluids.length == 0) {
            return Fluids.EMPTY.defaultFluidState();
        }
        FluidState fluid = fluids[getIndex(x, y, z)];
        return fluid != null ? fluid : Fluids.EMPTY.defaultFluidState();
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
