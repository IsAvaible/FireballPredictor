package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.simonconrad.fireballpredictor.FireballEntityAccessor;

import java.util.ArrayList;
import java.util.List;

public class FireballPredictorGameTest {

    private void buildWall(GameTestHelper context, BlockState state) {
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlock(new BlockPos(2, y, z), state);
            }
        }
    }

    private void buildWall(GameTestHelper context, Block block) {
        buildWall(context, block.defaultBlockState());
    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractHurtingProjectile> T spawnProjectile(
            GameTestHelper context, EntityType<T> type, double accelerationPower, boolean isCharged) {
        T projectile = (T) context.spawn(type, 1, 3, 3);
        projectile.setPos(context.absoluteVec(new Vec3(1.5, 3.0, 3.5)));
        Vec3 rotatedVelocity = context.absoluteVec(new Vec3(0.5, 0.0, 0.0)).subtract(context.absoluteVec(Vec3.ZERO));
        projectile.setDeltaMovement(rotatedVelocity);
        projectile.accelerationPower = accelerationPower;
        if (projectile instanceof WitherSkull skull) {
            skull.setDangerous(isCharged);
        }
        return projectile;
    }

    private List<BlockPos> getBrokenBlocks(GameTestHelper context, Block originalBlock) {
        List<BlockPos> actualAbsoluteBroken = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                BlockPos relPos = new BlockPos(2, y, z);
                BlockPos absPos = context.absolutePos(relPos);
                BlockState state = context.getLevel().getBlockState(absPos);
                if (!state.is(originalBlock)) {
                    actualAbsoluteBroken.add(absPos);
                }
            }
        }
        return actualAbsoluteBroken;
    }

    private List<BlockPos> getPredictedBrokenBlocks(AbstractHurtingProjectile projectile, GameTestHelper context) {
        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(projectile, context.getLevel());
        PredictionData prediction = TrajectoryPredictor.computePrediction(projectile, trajResult, projectile.tickCount);
        return prediction.brokenBlocks;
    }

    private void assertExplosionDestruction(
            GameTestHelper context,
            AbstractHurtingProjectile projectile,
            Block wallBlock,
            int minExpectedActualCount
    ) {
        List<BlockPos> predictedAbsoluteBroken = getPredictedBrokenBlocks(projectile, context);
        if (predictedAbsoluteBroken.isEmpty()) {
            throw new RuntimeException("Predicted 0 broken blocks, but it should hit the wall and break blocks.");
        }

        context.runAfterDelay(20L, () -> {
            List<BlockPos> actualAbsoluteBroken = getBrokenBlocks(context, wallBlock);
            if (actualAbsoluteBroken.isEmpty()) {
                throw new RuntimeException("Actual explosion did not break any blocks.");
            }

            for (BlockPos actualPos : actualAbsoluteBroken) {
                if (!predictedAbsoluteBroken.contains(actualPos)) {
                    throw new RuntimeException("Block at " + actualPos + " was actually broken, but was not predicted to break.");
                }
            }

            int actualCount = actualAbsoluteBroken.size();
            int predictedCount = predictedAbsoluteBroken.size();
            
            if (actualCount < minExpectedActualCount) {
                throw new RuntimeException("Explosion only broke " + actualCount + " blocks, expected at least " + minExpectedActualCount);
            }

            double minRatio = 0.4;
            if (actualCount < predictedCount * minRatio) {
                throw new RuntimeException("Actual broken blocks count (" + actualCount + ") is too low compared to predicted (" + predictedCount + "). Min expected: " + (int)(predictedCount * minRatio));
            }

            context.succeed();
        });
    }

    private void assertNoDestruction(
            GameTestHelper context,
            AbstractHurtingProjectile projectile,
            Block wallBlock
    ) {
        List<BlockPos> predictedAbsoluteBroken = getPredictedBrokenBlocks(projectile, context);
        if (!predictedAbsoluteBroken.isEmpty()) {
            throw new RuntimeException("Predicted " + predictedAbsoluteBroken.size() + " broken blocks, but it should not break any.");
        }

        context.runAfterDelay(20L, () -> {
            List<BlockPos> actualAbsoluteBroken = getBrokenBlocks(context, wallBlock);
            if (!actualAbsoluteBroken.isEmpty()) {
                throw new RuntimeException("Explosion actually broke " + actualAbsoluteBroken.size() + " blocks, but was expected to break 0.");
            }
            context.succeed();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testFireballPredictionAndExplosion(GameTestHelper context) {
        buildWall(context, Blocks.DIRT);
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.05, false);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWitherSkullPredictionAndExplosion(GameTestHelper context) {
        buildWall(context, Blocks.DIRT);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, false);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullPredictionAndExplosion(GameTestHelper context) {
        buildWall(context, Blocks.DIRT);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstObsidian(GameTestHelper context) {
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OBSIDIAN, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalWitherSkullAgainstObsidian(GameTestHelper context) {
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, false);
        assertNoDestruction(context, skull, Blocks.OBSIDIAN);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalFireballAgainstWaterloggedSlab(GameTestHelper context) {
        BlockState waterloggedSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.05, false);
        assertNoDestruction(context, fireball, Blocks.OAK_SLAB);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstWaterloggedSlab(GameTestHelper context) {
        BlockState waterloggedSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OAK_SLAB, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testHighPowerFireballPredictionAndExplosion(GameTestHelper context) {
        buildWall(context, Blocks.DIRT);
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.05, false);
        ((FireballEntityAccessor) fireball).setExplosionPower(3);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 10);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWindChargePredictionAndExplosion(GameTestHelper context) {
        buildWall(context, Blocks.DIRT);
        net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge windCharge = spawnProjectile(context, EntityTypes.WIND_CHARGE, 0.0, false);
        assertNoDestruction(context, windCharge, Blocks.DIRT);
    }
}
