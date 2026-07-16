package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class FireballPredictorGameTest {

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testFireballPredictionAndExplosion(TestContext context) {
        // 1. Build a target wall of dirt blocks at relative x = 2, y=1..5, z=1..5
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlockState(new BlockPos(2, y, z), Blocks.DIRT);
            }
        }

        // 2. Spawn a fireball at relative pos (1.5, 3.0, 3.5) with rotated velocity (0.5, 0.0, 0.0)
        FireballEntity fireball = (FireballEntity) context.spawnEntity(EntityType.FIREBALL, 1, 3, 3);
        fireball.setPosition(context.getAbsolute(new Vec3d(1.5, 3.0, 3.5)));
        Vec3d rotatedVelocity = context.getAbsolute(new Vec3d(0.5, 0.0, 0.0)).subtract(context.getAbsolute(Vec3d.ZERO));
        fireball.setVelocity(rotatedVelocity);
        fireball.accelerationPower = 0.05;

        // 3. Compute predicted broken blocks before letting the fireball tick
        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(fireball, context.getWorld());
        PredictionData prediction = TrajectoryPredictor.computePrediction(fireball, trajResult, fireball.age);
        List<BlockPos> predictedAbsoluteBroken = prediction.brokenBlocks;

        if (predictedAbsoluteBroken.isEmpty()) {
            throw new RuntimeException("Predicted 0 broken blocks, but it should hit the wall and break blocks.");
        }

        // 4. Wait for 20 ticks (generous time for the fireball to travel to the wall and detonate)
        context.waitAndRun(20L, () -> {
            // 5. Gather actually broken blocks (the blocks in the wall that are no longer DIRT)
            List<BlockPos> actualAbsoluteBroken = new ArrayList<>();
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    BlockPos relPos = new BlockPos(2, y, z);
                    BlockPos absPos = context.getAbsolutePos(relPos);
                    BlockState state = context.getWorld().getBlockState(absPos);
                    if (state.isAir() || state.isOf(Blocks.FIRE)) {
                        actualAbsoluteBroken.add(absPos);
                    }
                }
            }

            if (actualAbsoluteBroken.isEmpty()) {
                throw new RuntimeException("Actual explosion did not break any dirt blocks.");
            }

            // 6. Assert that every actually broken block is in the predicted blocks
            for (BlockPos actualPos : actualAbsoluteBroken) {
                if (!predictedAbsoluteBroken.contains(actualPos)) {
                    throw new RuntimeException("Block at " + actualPos + " was actually broken, but was not predicted to break.");
                }
            }

            // 7. Assert that the number of actually broken blocks is a reasonable portion of the predicted ones
            int actualCount = actualAbsoluteBroken.size();
            int predictedCount = predictedAbsoluteBroken.size();
            double minRatio = 0.4;
            if (actualCount < predictedCount * minRatio) {
                throw new RuntimeException("Actual broken blocks count (" + actualCount + ") is too low compared to predicted (" + predictedCount + "). Min expected: " + (int)(predictedCount * minRatio));
            }

            // Mark the test as successful
            context.complete();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWitherSkullPredictionAndExplosion(TestContext context) {
        // 1. Build a target wall of dirt blocks at relative x = 2, y=1..5, z=1..5
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlockState(new BlockPos(2, y, z), Blocks.DIRT);
            }
        }

        // 2. Spawn a wither skull at relative pos (1.5, 3.0, 3.5) with rotated velocity (0.5, 0.0, 0.0)
        WitherSkullEntity skull = (WitherSkullEntity) context.spawnEntity(EntityType.WITHER_SKULL, 1, 3, 3);
        skull.setPosition(context.getAbsolute(new Vec3d(1.5, 3.0, 3.5)));
        Vec3d rotatedVelocity = context.getAbsolute(new Vec3d(0.5, 0.0, 0.0)).subtract(context.getAbsolute(Vec3d.ZERO));
        skull.setVelocity(rotatedVelocity);
        skull.accelerationPower = 0.0;
        skull.setCharged(false);

        // 3. Compute predicted broken blocks before letting the wither skull tick
        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(skull, context.getWorld());
        PredictionData prediction = TrajectoryPredictor.computePrediction(skull, trajResult, skull.age);
        List<BlockPos> predictedAbsoluteBroken = prediction.brokenBlocks;

        if (predictedAbsoluteBroken.isEmpty()) {
            throw new RuntimeException("Predicted 0 broken blocks for wither skull.");
        }

        // 4. Wait for 20 ticks and verify
        context.waitAndRun(20L, () -> {
            List<BlockPos> actualAbsoluteBroken = new ArrayList<>();
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    BlockPos relPos = new BlockPos(2, y, z);
                    BlockPos absPos = context.getAbsolutePos(relPos);
                    BlockState state = context.getWorld().getBlockState(absPos);
                    if (state.isAir() || state.isOf(Blocks.FIRE)) {
                        actualAbsoluteBroken.add(absPos);
                    }
                }
            }

            if (actualAbsoluteBroken.isEmpty()) {
                throw new RuntimeException("Actual wither skull explosion did not break any blocks.");
            }

            for (BlockPos actualPos : actualAbsoluteBroken) {
                if (!predictedAbsoluteBroken.contains(actualPos)) {
                    throw new RuntimeException("Wither skull broke block at " + actualPos + " which was not predicted.");
                }
            }

            int actualCount = actualAbsoluteBroken.size();
            int predictedCount = predictedAbsoluteBroken.size();
            double minRatio = 0.4;
            if (actualCount < predictedCount * minRatio) {
                throw new RuntimeException("Wither skull actual broken blocks count (" + actualCount + ") is too low compared to predicted (" + predictedCount + ").");
            }

            context.complete();
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullPredictionAndExplosion(TestContext context) {
        // 1. Build a target wall of dirt blocks at relative x = 2, y=1..5, z=1..5
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlockState(new BlockPos(2, y, z), Blocks.DIRT);
            }
        }

        // 2. Spawn a wither skull at relative pos (1.5, 3.0, 3.5) with rotated velocity (0.5, 0.0, 0.0) and set charged
        WitherSkullEntity skull = (WitherSkullEntity) context.spawnEntity(EntityType.WITHER_SKULL, 1, 3, 3);
        skull.setPosition(context.getAbsolute(new Vec3d(1.5, 3.0, 3.5)));
        Vec3d rotatedVelocity = context.getAbsolute(new Vec3d(0.5, 0.0, 0.0)).subtract(context.getAbsolute(Vec3d.ZERO));
        skull.setVelocity(rotatedVelocity);
        skull.accelerationPower = 0.0;
        skull.setCharged(true);

        // 3. Compute predicted broken blocks before letting the wither skull tick
        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(skull, context.getWorld());
        PredictionData prediction = TrajectoryPredictor.computePrediction(skull, trajResult, skull.age);
        List<BlockPos> predictedAbsoluteBroken = prediction.brokenBlocks;

        if (predictedAbsoluteBroken.isEmpty()) {
            throw new RuntimeException("Predicted 0 broken blocks for charged wither skull.");
        }

        // 4. Wait for 20 ticks and verify
        context.waitAndRun(20L, () -> {
            List<BlockPos> actualAbsoluteBroken = new ArrayList<>();
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    BlockPos relPos = new BlockPos(2, y, z);
                    BlockPos absPos = context.getAbsolutePos(relPos);
                    BlockState state = context.getWorld().getBlockState(absPos);
                    if (state.isAir() || state.isOf(Blocks.FIRE)) {
                        actualAbsoluteBroken.add(absPos);
                    }
                }
            }

            if (actualAbsoluteBroken.isEmpty()) {
                throw new RuntimeException("Actual charged wither skull explosion did not break any blocks.");
            }

            for (BlockPos actualPos : actualAbsoluteBroken) {
                if (!predictedAbsoluteBroken.contains(actualPos)) {
                    throw new RuntimeException("Charged wither skull broke block at " + actualPos + " which was not predicted.");
                }
            }

            int actualCount = actualAbsoluteBroken.size();
            int predictedCount = predictedAbsoluteBroken.size();
            double minRatio = 0.4;
            if (actualCount < predictedCount * minRatio) {
                throw new RuntimeException("Charged wither skull actual broken blocks count (" + actualCount + ") is too low compared to predicted (" + predictedCount + ").");
            }

            context.complete();
        });
    }
}
