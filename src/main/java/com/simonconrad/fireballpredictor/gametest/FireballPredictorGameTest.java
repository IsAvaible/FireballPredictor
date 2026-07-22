package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.simonconrad.fireballpredictor.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.client.network.ExplosionInferenceHandler;
import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker;

import java.util.ArrayList;
import java.util.List;

public class FireballPredictorGameTest {

    private void buildWall(TestContext context, BlockState state) {
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlockState(new BlockPos(2, y, z), state);
            }
        }
    }

    private void buildWall(TestContext context, Block block) {
        buildWall(context, block.getDefaultState());
    }

    @SuppressWarnings("unchecked")
    private <T extends ExplosiveProjectileEntity> T spawnProjectile(
            TestContext context, EntityType<T> type, double accelerationPower, boolean isCharged) {
        T projectile = (T) context.spawnEntity(type, 1, 3, 3);
        projectile.setPosition(context.getAbsolute(new Vec3d(1.5, 3.0, 3.5)));
        Vec3d rotatedVelocity = context.getAbsolute(new Vec3d(0.5, 0.0, 0.0)).subtract(context.getAbsolute(Vec3d.ZERO));
        projectile.setVelocity(rotatedVelocity);
        projectile.accelerationPower = accelerationPower;
        if (projectile instanceof WitherSkullEntity skull) {
            skull.setCharged(isCharged);
        }
        return projectile;
    }

    private List<BlockPos> getBrokenBlocks(TestContext context, Block originalBlock) {
        List<BlockPos> actualAbsoluteBroken = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                BlockPos relPos = new BlockPos(2, y, z);
                BlockPos absPos = context.getAbsolutePos(relPos);
                BlockState state = context.getWorld().getBlockState(absPos);
                if (!state.isOf(originalBlock)) {
                    actualAbsoluteBroken.add(absPos);
                }
            }
        }
        return actualAbsoluteBroken;
    }

    private List<BlockPos> getPredictedBrokenBlocks(ExplosiveProjectileEntity projectile, TestContext context) {
        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(projectile, context.getWorld());
        PredictionData prediction = TrajectoryPredictor.computePrediction(projectile, trajResult, projectile.age);
        return prediction.brokenBlocks;
    }

    private void assertExplosionDestruction(
            TestContext context,
            ExplosiveProjectileEntity projectile,
            Block wallBlock,
            int minExpectedActualCount
    ) {
        List<BlockPos> predictedAbsoluteBroken = getPredictedBrokenBlocks(projectile, context);
        if (predictedAbsoluteBroken.isEmpty()) {
            throw new RuntimeException("Predicted 0 broken blocks, but it should hit the wall and break blocks.");
        }

        context.waitAndRun(20L, () -> {
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

            context.complete();
        });
    }

    private void assertNoDestruction(
            TestContext context,
            ExplosiveProjectileEntity projectile,
            Block wallBlock
    ) {
        List<BlockPos> predictedAbsoluteBroken = getPredictedBrokenBlocks(projectile, context);
        if (!predictedAbsoluteBroken.isEmpty()) {
            throw new RuntimeException("Predicted " + predictedAbsoluteBroken.size() + " broken blocks, but it should not break any.");
        }

        context.waitAndRun(20L, () -> {
            List<BlockPos> actualAbsoluteBroken = getBrokenBlocks(context, wallBlock);
            if (!actualAbsoluteBroken.isEmpty()) {
                throw new RuntimeException("Explosion actually broke " + actualAbsoluteBroken.size() + " blocks, but was expected to break 0.");
            }
            context.complete();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testFireballPredictionAndExplosion(TestContext context) {
        buildWall(context, Blocks.DIRT);
        FireballEntity fireball = spawnProjectile(context, EntityType.FIREBALL, 0.05, false);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWitherSkullPredictionAndExplosion(TestContext context) {
        buildWall(context, Blocks.DIRT);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, false);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullPredictionAndExplosion(TestContext context) {
        buildWall(context, Blocks.DIRT);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstObsidian(TestContext context) {
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OBSIDIAN, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalWitherSkullAgainstObsidian(TestContext context) {
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, false);
        assertNoDestruction(context, skull, Blocks.OBSIDIAN);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalFireballAgainstWaterloggedSlab(TestContext context) {
        BlockState waterloggedSlab = Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        FireballEntity fireball = spawnProjectile(context, EntityType.FIREBALL, 0.05, false);
        assertNoDestruction(context, fireball, Blocks.OAK_SLAB);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstWaterloggedSlab(TestContext context) {
        BlockState waterloggedSlab = Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OAK_SLAB, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testHighPowerFireballPredictionAndExplosion(TestContext context) {
        buildWall(context, Blocks.DIRT);
        FireballEntity fireball = spawnProjectile(context, EntityType.FIREBALL, 0.05, false);
        ((FireballEntityAccessor) fireball).setExplosionPower(3);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 10);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWindChargePredictionAndExplosion(TestContext context) {
        buildWall(context, Blocks.DIRT);
        net.minecraft.entity.projectile.WindChargeEntity windCharge = spawnProjectile(context, EntityType.WIND_CHARGE, 0.0, false);
        assertNoDestruction(context, windCharge, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testInferredExplosionPowerFallback(TestContext context) {
        buildWall(context, Blocks.DIRT);
        ClientPowerCache.POWER_CACHE.clear();
        ClientPowerLookup.resetInferredPower();

        // Spawn fireball 1 and simulate its trajectory
        FireballEntity fireball1 = spawnProjectile(context, EntityType.FIREBALL, 0.05, false);
        TrajectoryPredictor.TrajectoryResult traj = TrajectoryPredictor.simulateTrajectory(fireball1, context.getWorld());
        PredictionData pred = TrajectoryPredictor.computePrediction(fireball1, traj, fireball1.age);
        Vec3d hitPos = pred.hitResult != null ? pred.hitResult.getPos() : fireball1.getEntityPos();

        // Register fireball location (lastPos and hitPos) in inference cache
        FireballInferenceTracker.registerFireballLocation(fireball1, hitPos);

        // Execute the actual explosion inference handler at the hit position with power 3.0
        ExplosionInferenceHandler.onExplosion(hitPos, 3.0f);
        fireball1.discard();

        // Assert that ExplosionInferenceHandler successfully inferred power 3.0f
        Float inferred = ClientPowerLookup.getInferredFireballPower();
        if (inferred == null || inferred != 3.0f) {
            throw new RuntimeException("ExplosionInferenceHandler failed to infer power! Expected 3.0f, but got: " + inferred);
        }

        // Spawn second unsynced fireball and verify ClientPowerLookup falls back to inferred 3.0f
        FireballEntity fireball2 = spawnProjectile(context, EntityType.FIREBALL, 0.05, false);
        float resolvedPower = ClientPowerLookup.getPower(fireball2);
        if (resolvedPower != 3.0f) {
            throw new RuntimeException("Expected resolved power for unsynced fireball to be inferred 3.0f, but got: " + resolvedPower);
        }

        ((FireballEntityAccessor) fireball2).setExplosionPower(3);
        assertExplosionDestruction(context, fireball2, Blocks.DIRT, 10);
    }
}
