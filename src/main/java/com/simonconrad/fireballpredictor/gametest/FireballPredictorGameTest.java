package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.client.network.ExplosionInferenceHandler;
import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker;
import com.simonconrad.fireballpredictor.client.tracking.ClientOwnerCache;
import com.simonconrad.fireballpredictor.client.tracking.InferenceResult;
import com.simonconrad.fireballpredictor.client.tracking.OwnerInferenceEngine;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRules;
import com.simonconrad.fireballpredictor.client.tracking.TrackedProjectile;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.DragonFireballEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.projectile.WindChargeEntity;
import net.minecraft.world.GameMode;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FireballPredictorGameTest {

    // Constants for wall structure and projectile spawning
    private static final int WALL_X = 2;
    private static final int WALL_MIN_Y = 1;
    private static final int WALL_MAX_Y = 5;
    private static final int WALL_MIN_Z = 1;
    private static final int WALL_MAX_Z = 5;

    private static final Vec3d SPAWN_POS = new Vec3d(1.5, 3.0, 3.5);
    private static final Vec3d INITIAL_VELOCITY = new Vec3d(0.5, 0.0, 0.0);

    /**
     * Helper to construct a framework-native GameTestException with a Text message.
     */
    private static GameTestException fail(String message) {
        return new GameTestException(Text.literal(message), 0);
    }

    /** Yarn has no Direction#toYRot(); convert a horizontal direction to an entity yaw. */
    private static float directionYaw(Direction direction) {
        return switch (direction) {
            case EAST -> 270.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            default -> 0.0f;
        };
    }

    /**
     * 1.21.11 counterpart of the 26.2 fabric-gametest {@code succeedWhen}: polls the
     * assertion once per tick via {@link TestContext#waitAndRun} and completes the test
     * as soon as it passes without throwing. Keeps polling on {@link GameTestException}
     * (the test's own "not yet" signal) and re-throws it after the tick budget.
     */
    private static void waitUntilSuccess(TestContext context, Runnable assertion, long timeoutTicks) {
        context.waitAndRun(1, () -> {
            try {
                assertion.run();
                context.complete();
            } catch (GameTestException e) {
                if (timeoutTicks <= 0) {
                    throw e;
                }
                waitUntilSuccess(context, assertion, timeoutTicks - 1);
            }
        });
    }


    /**
     * Centralized reset method to be called before stateful tests
     * to guarantee a clean environment and prevent test leakage.
     */
    private static void resetGlobalState() {
        ClientPowerCache.POWER_CACHE.clear();
        ClientPowerLookup.resetInferredPower();
        FireballInferenceTracker.clear();
        ClientOwnerCache.clear();
        ServerTrackingRules.clear();

        ServerConfig serverConfig = ServerConfig.instance();
        serverConfig.disableOtherOwnerTracking = false;
        serverConfig.disablePlayerTracking = false;
        serverConfig.disableDispenserTracking = false;
        serverConfig.disableCommandTracking = false;

        ModConfig config = ModConfig.instance();
        config.globalFallbackFireballPower = 1.0F;
        config.serverFallbackPowers.clear();
        config.trackProjectiles = true;
        config.trackFireballs = true;
        config.trackWitherSkulls = true;
        config.trackWindCharges = true;
        config.trackMobProjectiles = true;
        config.trackBlazeFireballs = true;
        config.trackGhastFireballs = true;
        config.trackEnderDragonFireballs = true;
        config.trackWitherMob = true;
        config.trackOtherOwnerProjectiles = true;
        config.trackPlayerProjectiles = true;
        config.trackDispenserProjectiles = true;
        config.trackCommandProjectiles = true;
    }

    private void buildWall(TestContext context, BlockState state) {
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                context.setBlockState(new BlockPos(WALL_X, y, z), state);
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
        projectile.setPosition(context.getAbsolute(SPAWN_POS));
        Vec3d rotatedVelocity = context.getAbsolute(INITIAL_VELOCITY).subtract(context.getAbsolute(Vec3d.ZERO));
        projectile.setVelocity(rotatedVelocity);
        projectile.accelerationPower = accelerationPower;
        if (projectile instanceof WitherSkullEntity skull) {
            skull.setCharged(isCharged);
        }
        return projectile;
    }

    private List<BlockPos> getBrokenBlocks(TestContext context, Block originalBlock) {
        List<BlockPos> actualAbsoluteBroken = new ArrayList<>();
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                BlockPos relPos = new BlockPos(WALL_X, y, z);
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
            throw fail("Predicted 0 broken blocks, but it should hit the wall and break blocks.");
        }

        Set<BlockPos> predictedSet = new HashSet<>(predictedAbsoluteBroken);

        waitUntilSuccess(context, () -> {
            List<BlockPos> actualAbsoluteBroken = getBrokenBlocks(context, wallBlock);
            if (actualAbsoluteBroken.isEmpty()) {
                throw fail("Waiting for explosion to break blocks...");
            }

            for (BlockPos actualPos : actualAbsoluteBroken) {
                if (!predictedSet.contains(actualPos)) {
                    throw fail("Block at " + actualPos + " was actually broken, but was not predicted to break.");
                }
            }

            int actualCount = actualAbsoluteBroken.size();
            int predictedCount = predictedAbsoluteBroken.size();

            if (actualCount < minExpectedActualCount) {
                throw fail("Explosion only broke " + actualCount + " blocks, expected at least " + minExpectedActualCount);
            }

            // Assert coverage (no false negatives threshold - actual broken blocks must be at least 50% of predicted)
            double minRatio = 0.5;
            if (actualCount < predictedCount * minRatio) {
                throw fail("Actual broken blocks count (" + actualCount + ") is too low compared to predicted (" + predictedCount + "). Min expected: " + (int) (predictedCount * minRatio));
            }

            // Assert over-prediction cap: predicted block count must not exceed 2.0x actual broken count (catching over-prediction regressions)
            double maxOverPredictionRatio = 2.0;
            if (predictedCount > actualCount * maxOverPredictionRatio) {
                throw fail("Over-prediction detected! Predicted " + predictedCount + " blocks, but actual broken was only " + actualCount + " (exceeds max over-prediction factor of " + maxOverPredictionRatio + "x).");
            }
        }, 40);
    }

    private void assertNoDestruction(
            TestContext context,
            ExplosiveProjectileEntity projectile,
            Block wallBlock
    ) {
        List<BlockPos> predictedAbsoluteBroken = getPredictedBrokenBlocks(projectile, context);
        if (!predictedAbsoluteBroken.isEmpty()) {
            throw fail("Predicted " + predictedAbsoluteBroken.size() + " broken blocks, but it should not break any.");
        }

        waitUntilSuccess(context, () -> {
            if (projectile.isAlive()) {
                throw fail("Waiting for projectile to collide/explode...");
            }
            List<BlockPos> actualAbsoluteBroken = getBrokenBlocks(context, wallBlock);
            if (!actualAbsoluteBroken.isEmpty()) {
                throw fail("Explosion actually broke " + actualAbsoluteBroken.size() + " blocks, but was expected to break 0.");
            }
        }, 40);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testFireballPredictionAndExplosion(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        FireballEntity fireball = spawnProjectile(context, EntityType.FIREBALL, 0.1, false);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWitherSkullPredictionAndExplosion(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, false);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullPredictionAndExplosion(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstObsidian(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OBSIDIAN, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalWitherSkullAgainstObsidian(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, false);
        assertNoDestruction(context, skull, Blocks.OBSIDIAN);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstReinforcedDeepslate(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.REINFORCED_DEEPSLATE);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, true);
        assertNoDestruction(context, skull, Blocks.REINFORCED_DEEPSLATE);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalFireballAgainstWaterloggedSlab(TestContext context) {
        resetGlobalState();
        BlockState waterloggedSlab = Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        FireballEntity fireball = spawnProjectile(context, EntityType.FIREBALL, 0.1, false);
        assertNoDestruction(context, fireball, Blocks.OAK_SLAB);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstWaterloggedSlab(TestContext context) {
        resetGlobalState();
        BlockState waterloggedSlab = Blocks.OAK_SLAB.getDefaultState().with(net.minecraft.state.property.Properties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        WitherSkullEntity skull = spawnProjectile(context, EntityType.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OAK_SLAB, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testHighPowerFireballPredictionAndExplosion(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        FireballEntity fireball = spawnProjectile(context, EntityType.FIREBALL, 0.1, false);
        ((FireballEntityAccessor) fireball).setExplosionPower(3);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 10);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWindChargePredictionAndExplosion(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        WindChargeEntity windCharge = spawnProjectile(context, EntityType.WIND_CHARGE, 0.0, false);
        assertNoDestruction(context, windCharge, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWaterDragPrediction(TestContext context) {
        resetGlobalState();
        for (int x = 0; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    context.setBlockState(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        FireballEntity fireball = spawnProjectile(context, EntityType.FIREBALL, 0.0, false);

        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(fireball, context.getWorld());

        Vec3d v0 = trajResult.velocities().get(0);
        Vec3d v1 = trajResult.velocities().get(1);
        double expectedSpeed1 = v0.length() * 0.8;
        if (Math.abs(v1.length() - expectedSpeed1) > 1e-4) {
            throw fail("Expected water drag 0.8 to reduce speed from " + v0.length() + " to " + expectedSpeed1 + ", but got " + v1.length());
        }

        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWindChargeWaterDragPrediction(TestContext context) {
        resetGlobalState();
        for (int x = 0; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    context.setBlockState(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        WindChargeEntity windCharge = spawnProjectile(context, EntityType.WIND_CHARGE, 0.0, false);

        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(windCharge, context.getWorld());

        Vec3d v0 = trajResult.velocities().get(0);
        Vec3d v1 = trajResult.velocities().get(1);
        if (Math.abs(v1.length() - v0.length()) > 1e-4) {
            throw fail("Expected wind charge in water to maintain 1.0 drag (speed " + v0.length() + "), but got " + v1.length());
        }

        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testBlockGetterWaterDetection(TestContext context) {
        resetGlobalState();
        BlockPos waterPos = context.getAbsolutePos(new BlockPos(2, 2, 2));
        context.setBlockState(new BlockPos(2, 2, 2), Blocks.WATER);

        com.simonconrad.fireballpredictor.math.BlockStateSnapshot snapshot =
            new com.simonconrad.fireballpredictor.math.BlockStateSnapshot(
                context.getWorld(), waterPos.add(-1, -1, -1), waterPos.add(1, 1, 1)
            );

        boolean touching = TrajectoryPredictor.isTouchingWater(
            snapshot, waterPos.getX() + 0.1, waterPos.getY() + 0.1, waterPos.getZ() + 0.1,
            waterPos.getX() + 0.9, waterPos.getY() + 0.9, waterPos.getZ() + 0.9
        );

        if (!touching) {
            throw fail("Expected snapshot BlockView isTouchingWater to return true for water block");
        }

        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testSmallFireballPowerAndNoDestruction(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        SmallFireballEntity fireball = spawnProjectile(context, EntityType.SMALL_FIREBALL, 0.0, false);
        if (com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball) != 0.0f) {
            throw fail("SmallFireballEntity explosion power expected to be 0.0f, got: " + com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball));
        }
        assertNoDestruction(context, fireball, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDragonFireballPowerAndNoDestruction(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        DragonFireballEntity fireball = spawnProjectile(context, EntityType.DRAGON_FIREBALL, 0.0, false);
        if (com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball) != 0.0f) {
            throw fail("DragonFireballEntity explosion power expected to be 0.0f, got: " + com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball));
        }
        assertNoDestruction(context, fireball, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testInferredExplosionPowerFallback(TestContext context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);

        // Spawn fireball 1 and simulate its trajectory
        FireballEntity fireball1 = spawnProjectile(context, EntityType.FIREBALL, 0.1, false);
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
            throw fail("ExplosionInferenceHandler failed to infer power! Expected 3.0f, but got: " + inferred);
        }

        // Spawn second unsynced fireball and verify ClientPowerLookup falls back to inferred 3.0f
        FireballEntity fireball2 = spawnProjectile(context, EntityType.FIREBALL, 0.1, false);
        float resolvedPower = ClientPowerLookup.getPower(fireball2);
        if (resolvedPower != 3.0f) {
            throw fail("Expected resolved power for unsynced fireball to be inferred 3.0f, but got: " + resolvedPower);
        }

        ((FireballEntityAccessor) fireball2).setExplosionPower(3);
        assertExplosionDestruction(context, fireball2, Blocks.DIRT, 10);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testZeroRadiusAffectedBlockEstimationAndHierarchy(TestContext context) {
        resetGlobalState();

        Vec3d explosionPos = new Vec3d(10.0, 64.0, 10.0);
        FireballEntity fireball = new FireballEntity(EntityType.FIREBALL, context.getWorld());
        fireball.setPosition(explosionPos.x, explosionPos.y, explosionPos.z);
        FireballInferenceTracker.registerFireballLocation(fireball, explosionPos);

        // 1. Simulate Explosion packet with radius = 0 and affected blocks extending 3.9 blocks away
        // explosionPos = (9.6, 63.5, 9.5), testBlockPos = (13, 63, 9)
        // Vec3d.ofCenter(BlockPos(13, 63, 9)) = (13.5, 63.5, 9.5) -> dist = 13.5 - 9.6 = 3.9 -> 3.9 / 1.3 = 3.0f
        Vec3d testExplosionPos = new Vec3d(9.6, 63.5, 9.5);
        BlockPos testBlockPos = new BlockPos(13, 63, 9);
        FireballInferenceTracker.registerFireballLocation(fireball, testExplosionPos);

        List<BlockPos> affected = List.of(testBlockPos);
        ExplosionInferenceHandler.onExplosion(testExplosionPos, 0.0f, affected);

        Float blockEst = ClientPowerLookup.getInferredBlockEstimation();
        if (blockEst == null || Math.abs(blockEst - 3.0f) > 0.01f) {
            throw fail("Expected inferred block estimation ~3.0f, but got: " + blockEst);
        }

        // 2. Test session max retention: smaller explosion (dMax = 1.3 -> 1.0f) should not decrease retained estimation (3.0f)
        List<BlockPos> smallerAffected = List.of(
                BlockPos.ofFloored(11.3, 64.0, 10.0)
        );
        ExplosionInferenceHandler.onExplosion(explosionPos, 0.0f, smallerAffected);
        if (Math.abs(ClientPowerLookup.getInferredBlockEstimation() - 3.0f) > 0.01f) {
            throw fail("Session max retention failed! Expected 3.0f, got: " + ClientPowerLookup.getInferredBlockEstimation());
        }

        // 3. Test Precedence: Radius Inference (Tier 2) overrides Block Estimation (Tier 4)
        ExplosionInferenceHandler.onExplosion(explosionPos, 2.5f, null);
        float resolvedPower = ClientPowerLookup.getPower(fireball);
        if (resolvedPower != 2.5f) {
            throw fail("Radius inference (Tier 2) should override block estimation! Expected 2.5f, got: " + resolvedPower);
        }

        fireball.discard();
        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testInflatedPacketRadiusSanityCheckAndServerPresetPriority(TestContext context) {
        resetGlobalState();

        Vec3d explosionPos = new Vec3d(10.0, 64.0, 10.0);
        FireballEntity fireball = new FireballEntity(EntityType.FIREBALL, context.getWorld());
        fireball.setPosition(explosionPos.x, explosionPos.y, explosionPos.z);
        FireballInferenceTracker.registerFireballLocation(fireball, explosionPos);

        // 1. Simulate GommeHD packet: radius = 4.0, but blockCount = 2 (estimates power ~1.44f)
        ExplosionInferenceHandler.onExplosion(explosionPos, 4.0f, 2, null);

        // Verify that 4.0f was rejected as inflated packet radius
        Float inferredRadius = ClientPowerLookup.getInferredPacketRadius();
        if (inferredRadius != null) {
            throw fail("Expected inflated packet radius 4.0f to be rejected, but it was accepted: " + inferredRadius);
        }

        Float blockEst = ClientPowerLookup.getInferredBlockEstimation();
        if (blockEst == null || Math.abs(blockEst - 1.44f) > 0.1f) {
            throw fail("Expected block estimation from 2 blocks ~1.44f, got: " + blockEst);
        }

        // 2. Simulate legitimate radius 4.0 with large block count (40 blocks estimates power ~3.91f)
        ExplosionInferenceHandler.onExplosion(explosionPos, 4.0f, 40, null);
        Float validRadius = ClientPowerLookup.getInferredPacketRadius();
        if (validRadius == null || validRadius != 4.0f) {
            throw fail("Expected valid packet radius 4.0f to be accepted, got: " + validRadius);
        }

        fireball.discard();
        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testServerFallbackPowerSetAndUnset(TestContext context) {
        resetGlobalState();
        ModConfig config = ModConfig.instance();
        String testServer = "test.hypixel.net";

        // 1. Set server fallback power
        config.setServerFallbackPower(testServer, 2.5f);
        Float power = config.getServerFallbackPower(testServer);
        if (power == null || Math.abs(power - 2.5f) > 0.001f) {
            throw fail("Expected server fallback power to be 2.5f, but got: " + power);
        }

        // 2. Set to 0.0f (should un-set / remove from map, returning null)
        config.setServerFallbackPower(testServer, 0.0f);
        if (config.getServerFallbackPower(testServer) != null) {
            throw fail("Expected server fallback power to be null after setting 0.0f, but got: " + config.getServerFallbackPower(testServer));
        }

        // 3. Set to 3.0f then pass null (should un-set / remove from map, returning null)
        config.setServerFallbackPower(testServer, 3.0f);
        config.setServerFallbackPower(testServer, (Float) null);
        if (config.getServerFallbackPower(testServer) != null) {
            throw fail("Expected server fallback power to be null after passing null Float, but got: " + config.getServerFallbackPower(testServer));
        }

        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testOwnerInferenceNativeAndSweep(TestContext context) {
        resetGlobalState();

        // 1. Native owner via setOwner (singleplayer / NBT path)
        GhastEntity ghast = context.spawnEntity(EntityType.GHAST, 1, 3, 3);
        ghast.setPosition(context.getAbsolute(new Vec3d(1.5, 3.0, 3.5)));

        FireballEntity fireball = context.spawnEntity(EntityType.FIREBALL, 2, 3, 3);
        Vec3d spawn = context.getAbsolute(new Vec3d(3.5, 3.0, 3.5));
        fireball.setPosition(spawn);
        fireball.setOwner(ghast);
        fireball.setVelocity(context.getAbsolute(new Vec3d(0.5, 0.0, 0.0)).subtract(context.getAbsolute(Vec3d.ZERO)));

        InferenceResult nativeResult = OwnerInferenceEngine.infer(fireball, context.getWorld());
        if (nativeResult.owner() != ProjectileOwner.GHAST) {
            throw fail("Expected NATIVE GHAST owner, got: " + nativeResult.owner()
                    + " via " + nativeResult.source());
        }
        if (nativeResult.source() != InferenceResult.InferenceSource.NATIVE_NBT) {
            throw fail("Expected NATIVE_NBT source, got: " + nativeResult.source());
        }

        // 2. Environmental sweep — no setOwner, ghast looking toward the fireball
        fireball.setOwner((net.minecraft.entity.Entity) null);
        // Face +X (toward the fireball relative spawn)
        ghast.setYaw(directionYaw(context.getRotation().getDirectionTransformation().map(Direction.EAST)));
        ghast.setPitch(0.0f);

        // Place blaze farther away looking wrong way — should lose to ghast
        BlazeEntity blaze = context.spawnEntity(EntityType.BLAZE, 5, 3, 5);
        blaze.setPosition(context.getAbsolute(new Vec3d(8.0, 3.0, 8.0)));
        blaze.setYaw(0.0f);

        InferenceResult sweep = OwnerInferenceEngine.infer(fireball, context.getWorld());
        if (sweep.owner() != ProjectileOwner.GHAST && sweep.owner() != ProjectileOwner.BLAZE
                && sweep.owner() != ProjectileOwner.COMMAND) {
            throw fail("Unexpected sweep owner: " + sweep.owner() + " via " + sweep.source());
        }
        // With owner cleared, source must not be NATIVE_NBT
        if (sweep.source() == InferenceResult.InferenceSource.NATIVE_NBT) {
            throw fail("Sweep should not report NATIVE_NBT after owner cleared");
        }

        ghast.discard();
        blaze.discard();
        fireball.discard();
        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testOwnerInferenceDispenserAndDeflection(TestContext context) {
        resetGlobalState();

        // Dispenser facing EAST with fireball just outside its face
        BlockPos relDispenser = new BlockPos(2, 2, 2);
        BlockState dispenserState = Blocks.DISPENSER.getDefaultState()
                .with(DispenserBlock.FACING, Direction.EAST);
        context.setBlockState(relDispenser, dispenserState);

        FireballEntity fireball = context.spawnEntity(EntityType.FIREBALL, 3, 2, 2);
        // Absolute position roughly one block east of the dispenser centre
        Vec3d dispenseAbs = context.getAbsolute(new Vec3d(3.2, 2.5, 2.5));
        fireball.setPosition(dispenseAbs);
        Vec3d eastVel = context.getAbsolute(new Vec3d(0.5, 0.0, 0.0)).subtract(context.getAbsolute(Vec3d.ZERO));
        fireball.setVelocity(eastVel);

        InferenceResult dispenserResult = OwnerInferenceEngine.infer(fireball, context.getWorld());
        if (dispenserResult.owner() != ProjectileOwner.DISPENSER) {
            throw fail("Expected DISPENSER owner, got: " + dispenserResult.owner()
                    + " via " + dispenserResult.source());
        }

        // Deflection: reverse velocity near a server mock player in the level → PLAYER
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setPosition(dispenseAbs.x + 1.0, dispenseAbs.y, dispenseAbs.z);
        context.getWorld().spawnEntity(player);

        Vec3d prevVel = fireball.getVelocity();
        fireball.setVelocity(prevVel.multiply(-1.0));

        InferenceResult deflected = OwnerInferenceEngine.reassignOnDeflection(
                fireball, context.getWorld(), dispenserResult, prevVel);
        if (deflected.owner() != ProjectileOwner.PLAYER) {
            throw fail("Expected PLAYER after deflection, got: " + deflected.owner()
                    + " (playersNearby="
                    + context.getWorld().getEntitiesByClass(PlayerEntity.class, fireball.getBoundingBox().expand(5.0), e -> true).size()
                    + ")");
        }
        if (!deflected.isDeflected()) {
            throw fail("Expected isDeflected() to be true after deflection");
        }

        // Sideways deflection (90-degree angle change, dot product ≈ 0.0)
        Vec3d sidewaysVel = new Vec3d(0.0, 0.0, prevVel.x != 0 ? prevVel.x : 0.5);
        fireball.setVelocity(sidewaysVel);
        InferenceResult sidewaysDeflected = OwnerInferenceEngine.reassignOnDeflection(
                fireball, context.getWorld(), dispenserResult, prevVel);
        if (sidewaysDeflected.owner() != ProjectileOwner.PLAYER || !sidewaysDeflected.isDeflected()) {
            throw fail("Expected PLAYER and isDeflected()=true after 90-degree sideways deflection, got: " 
                    + sidewaysDeflected.owner());
        }

        // Config filter: player projectiles off by default
        ModConfig config = ModConfig.instance();
        boolean previousPlayer = config.trackPlayerProjectiles;
        boolean previousMaster = config.trackProjectiles;
        try {
            config.trackProjectiles = true;
            config.trackPlayerProjectiles = false;
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, false)) {
                throw fail("PlayerEntity filter should be false for non-deflected when trackPlayerProjectiles=false");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, true)) {
                throw fail("Deflected fireball filter should be true even when trackPlayerProjectiles=false");
            }
            config.trackPlayerProjectiles = true;
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw fail("PlayerEntity filter should be true when trackPlayerProjectiles=true");
            }

            // Wind charge owner filter priority check: Owner tracking > ProjectileEntity type tracking
            WindChargeEntity testWindCharge = spawnProjectile(context, EntityType.WIND_CHARGE, 0.0, false);
            config.trackProjectiles = true;
            config.trackWindCharges = true;
            config.trackPlayerProjectiles = false;
            if (TrackedProjectile.evaluateFilter(testWindCharge, ProjectileOwner.PLAYER, false)) {
                throw fail("PlayerEntity-owned wind charge filter should be false when trackPlayerProjectiles=false even if trackWindCharges=true");
            }
            config.trackPlayerProjectiles = true;
            config.trackWindCharges = false;
            if (TrackedProjectile.evaluateFilter(testWindCharge, ProjectileOwner.PLAYER, false)) {
                throw fail("Wind charge filter should be false when trackWindCharges=false even if trackPlayerProjectiles=true");
            }
            config.trackWindCharges = true;
            if (!TrackedProjectile.evaluateFilter(testWindCharge, ProjectileOwner.PLAYER, false)) {
                throw fail("Wind charge filter should be true when both trackPlayerProjectiles=true and trackWindCharges=true");
            }
            testWindCharge.discard();

            config.trackProjectiles = false;
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.GHAST)) {
                throw fail("Master off should disable ghast tracking");
            }
        } finally {
            config.trackPlayerProjectiles = previousPlayer;
            config.trackProjectiles = previousMaster;
        }

        // Classifier sanity
        if (OwnerClassifier.classifyEntity(player) != ProjectileOwner.PLAYER) {
            throw fail("classifyEntity(player) failed");
        }

        fireball.discard();
        player.discard();
        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testServerTrackingRestrictions(TestContext context) {
        resetGlobalState();

        FireballEntity fireball = context.spawnEntity(EntityType.FIREBALL, 1, 2, 1);
        ModConfig config = ModConfig.instance();
        ServerConfig serverConfig = ServerConfig.instance();

        boolean previousTrack = config.trackProjectiles;
        boolean previousMobMaster = config.trackMobProjectiles;
        boolean previousGhast = config.trackGhastFireballs;
        boolean previousOther = config.trackOtherOwnerProjectiles;
        boolean previousPlayer = config.trackPlayerProjectiles;
        boolean previousDispenser = config.trackDispenserProjectiles;
        boolean previousCommand = config.trackCommandProjectiles;
        boolean prevScMaster = serverConfig.disableOtherOwnerTracking;
        boolean prevScPlayer = serverConfig.disablePlayerTracking;
        boolean prevScDispenser = serverConfig.disableDispenserTracking;
        boolean prevScCommand = serverConfig.disableCommandTracking;
        int previousMask = ServerTrackingRules.mask();
        try {
            // Local config allows everything; the server restriction alone must gate.
            config.trackProjectiles = true;
            config.trackMobProjectiles = true;
            config.trackGhastFireballs = true;
            config.trackOtherOwnerProjectiles = true;
            config.trackPlayerProjectiles = true;
            config.trackDispenserProjectiles = true;
            config.trackCommandProjectiles = true;

            // No restrictions -> local config decides
            ServerTrackingRules.clear();
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw fail("PlayerEntity tracking should be allowed when the server does not restrict it");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.DISPENSER)) {
                throw fail("Dispenser tracking should be allowed when the server does not restrict it");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.COMMAND)) {
                throw fail("Command tracking should be allowed when the server does not restrict it");
            }

            // Sub-option restriction: players only
            ServerTrackingRules.applyMask(TrackingRules.PLAYER);
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, false)) {
                throw fail("Server restriction must disable player tracking even when locally enabled");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, true)) {
                throw fail("Deflection must not bypass the server player restriction");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.DISPENSER)) {
                throw fail("Dispenser tracking must stay enabled when only players are restricted");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.UNKNOWN)) {
                throw fail("Unknown shares the command bit and must stay enabled when only players are restricted");
            }

            // Whole "other" group restriction
            ServerTrackingRules.applyMask(TrackingRules.OTHER_GROUP);
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw fail("Whole-group restriction must disable player tracking");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.DISPENSER)) {
                throw fail("Whole-group restriction must disable dispenser tracking");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.COMMAND)) {
                throw fail("Whole-group restriction must disable command tracking");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.UNKNOWN)) {
                throw fail("Whole-group restriction must disable unknown (command) tracking");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.GHAST)) {
                throw fail("MobEntity owners are not part of the server \"other\" restriction");
            }

            // Lifting restrictions restores local behaviour immediately
            ServerTrackingRules.clear();
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw fail("Clearing the server mask must re-enable locally allowed player tracking");
            }

            // ServerConfig mask computation: sub-options combine, master covers the whole group
            serverConfig.disableOtherOwnerTracking = false;
            serverConfig.disablePlayerTracking = true;
            if (serverConfig.disabledOwnerMask() != TrackingRules.PLAYER) {
                throw fail("ServerConfig sub-option must map to its TrackingRules bit");
            }
            serverConfig.disableDispenserTracking = true;
            serverConfig.disableCommandTracking = true;
            if (serverConfig.disabledOwnerMask() != TrackingRules.OTHER_GROUP) {
                throw fail("ServerConfig sub-options must combine into the whole group mask");
            }
            serverConfig.disableOtherOwnerTracking = true;
            serverConfig.disablePlayerTracking = false;
            serverConfig.disableDispenserTracking = false;
            serverConfig.disableCommandTracking = false;
            if (serverConfig.disabledOwnerMask() != TrackingRules.OTHER_GROUP) {
                throw fail("ServerConfig master must disable the whole other group");
            }
            serverConfig.disableOtherOwnerTracking = false;
            if (serverConfig.disabledOwnerMask() != 0) {
                throw fail("Default ServerConfig must not restrict anything");
            }
        } finally {
            config.trackProjectiles = previousTrack;
            config.trackMobProjectiles = previousMobMaster;
            config.trackGhastFireballs = previousGhast;
            config.trackOtherOwnerProjectiles = previousOther;
            config.trackPlayerProjectiles = previousPlayer;
            config.trackDispenserProjectiles = previousDispenser;
            config.trackCommandProjectiles = previousCommand;
            serverConfig.disableOtherOwnerTracking = prevScMaster;
            serverConfig.disablePlayerTracking = prevScPlayer;
            serverConfig.disableDispenserTracking = prevScDispenser;
            serverConfig.disableCommandTracking = prevScCommand;
            ServerTrackingRules.applyMask(previousMask);
        }

        fireball.discard();
        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testPacketSanitization(TestContext context) {
        resetGlobalState();

        int previousMask = ServerTrackingRules.mask();
        try {
            // Apply 0xFFFF -> only valid bits (0x07: PLAYER, DISPENSER, COMMAND) must survive
            ServerTrackingRules.applyMask(0xFFFF);
            int mask = ServerTrackingRules.mask();
            if (mask != TrackingRules.OTHER_GROUP) {
                throw fail("Expected mask 0xFFFF to sanitize to OTHER_GROUP (" + TrackingRules.OTHER_GROUP + "), got: " + mask);
            }
            if ((mask & ~TrackingRules.OTHER_GROUP) != 0) {
                throw fail("Mask retained unsupported bits: 0x" + Integer.toHexString(mask));
            }

            // Apply 0xFF00 (no valid bits) -> should sanitize to 0
            ServerTrackingRules.applyMask(0xFF00);
            mask = ServerTrackingRules.mask();
            if (mask != 0) {
                throw fail("Expected mask 0xFF00 to sanitize to 0, got: " + mask);
            }
        } finally {
            ServerTrackingRules.applyMask(previousMask);
        }

        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testDisconnectReset(TestContext context) {
        resetGlobalState();

        int previousMask = ServerTrackingRules.mask();
        try {
            // Join server A (restricted)
            ServerTrackingRules.applyMask(TrackingRules.PLAYER | TrackingRules.DISPENSER);
            if (!ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER) || !ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)) {
                throw fail("Failed to set restrictions for server A");
            }

            // Disconnect -> clear restrictions (simulating disconnect event listener)
            ServerTrackingRules.clear();
            if (ServerTrackingRules.mask() != 0) {
                throw fail("Stale mask remains after disconnect! Expected 0, got: " + ServerTrackingRules.mask());
            }

            if (ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw fail("Stale restriction active after disconnect!");
            }
        } finally {
            ServerTrackingRules.applyMask(previousMask);
        }

        context.complete();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testGuiOptionAvailability(TestContext context) {
        resetGlobalState();

        int previousMask = ServerTrackingRules.mask();

        try {
            // 1. Unrestricted state -> no options restricted
            ServerTrackingRules.clear();
            if (ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw fail("No options should be restricted when mask is clear");
            }

            // 2. Restricted PLAYER bit -> only PLAYER option disabled
            ServerTrackingRules.applyMask(TrackingRules.PLAYER);
            if (!ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)) {
                throw fail("PLAYER option should be restricted when PLAYER bit is set");
            }
            if (ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw fail("DISPENSER and COMMAND options should remain available when only PLAYER is restricted");
            }

            // 3. Whole OTHER_GROUP restricted -> all 3 options disabled
            ServerTrackingRules.applyMask(TrackingRules.OTHER_GROUP);
            if (!ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || !ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || !ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw fail("All tracking options should be restricted when OTHER_GROUP is set");
            }

            // 4. Disconnect / clear restrictions -> re-enabled
            ServerTrackingRules.clear();
            if (ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw fail("All tracking options should re-enable after server restrictions are lifted");
            }
        } finally {
            ServerTrackingRules.applyMask(previousMask);
        }

        context.complete();
    }
}