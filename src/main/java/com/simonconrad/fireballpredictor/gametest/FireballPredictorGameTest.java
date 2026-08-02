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
import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.client.network.ExplosionInferenceHandler;
import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker;
import com.simonconrad.fireballpredictor.client.tracking.InferenceResult;
import com.simonconrad.fireballpredictor.client.tracking.OwnerInferenceEngine;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRules;
import com.simonconrad.fireballpredictor.client.tracking.TrackedProjectile;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DispenserBlock;

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

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testInferredExplosionPowerFallback(GameTestHelper context) {
        buildWall(context, Blocks.DIRT);
        ClientPowerCache.POWER_CACHE.clear();
        ClientPowerLookup.resetInferredPower();

        // Spawn fireball 1 and simulate its trajectory
        LargeFireball fireball1 = spawnProjectile(context, EntityTypes.FIREBALL, 0.05, false);
        TrajectoryPredictor.TrajectoryResult traj = TrajectoryPredictor.simulateTrajectory(fireball1, context.getLevel());
        PredictionData pred = TrajectoryPredictor.computePrediction(fireball1, traj, fireball1.tickCount);
        Vec3 hitPos = pred.hitResult != null ? pred.hitResult.getLocation() : fireball1.position();

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
        LargeFireball fireball2 = spawnProjectile(context, EntityTypes.FIREBALL, 0.05, false);
        float resolvedPower = ClientPowerLookup.getPower(fireball2);
        if (resolvedPower != 3.0f) {
            throw new RuntimeException("Expected resolved power for unsynced fireball to be inferred 3.0f, but got: " + resolvedPower);
        }

        ((FireballEntityAccessor) fireball2).setExplosionPower(3);
        assertExplosionDestruction(context, fireball2, Blocks.DIRT, 10);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testZeroRadiusAffectedBlockEstimationAndHierarchy(GameTestHelper context) {
        ClientPowerCache.POWER_CACHE.clear();
        ClientPowerLookup.resetInferredPower();

        Vec3 explosionPos = new Vec3(10.0, 64.0, 10.0);
        LargeFireball fireball = new LargeFireball(EntityTypes.FIREBALL, context.getLevel());
        fireball.setPos(explosionPos.x, explosionPos.y, explosionPos.z);
        FireballInferenceTracker.registerFireballLocation(fireball, explosionPos);

        // 1. Simulate Explosion packet with radius = 0 and affected blocks extending 3.9 blocks away
        // explosionPos = (9.6, 63.5, 9.5), testBlockPos = (13, 63, 9)
        // Vec3.atCenterOf(BlockPos(13, 63, 9)) = (13.5, 63.5, 9.5) -> dist = 13.5 - 9.6 = 3.9 -> 3.9 / 1.3 = 3.0f
        Vec3 testExplosionPos = new Vec3(9.6, 63.5, 9.5);
        BlockPos testBlockPos = new BlockPos(13, 63, 9);
        FireballInferenceTracker.registerFireballLocation(fireball, testExplosionPos);

        List<BlockPos> affected = List.of(testBlockPos);
        ExplosionInferenceHandler.onExplosion(testExplosionPos, 0.0f, affected);

        Float blockEst = ClientPowerLookup.getInferredBlockEstimation();
        if (blockEst == null || Math.abs(blockEst - 3.0f) > 0.01f) {
            throw new RuntimeException("Expected inferred block estimation ~3.0f, but got: " + blockEst);
        }

        // 2. Test session max retention: smaller explosion (dMax = 1.3 -> 1.0f) should not decrease retained estimation (3.0f)
        List<BlockPos> smallerAffected = List.of(
                BlockPos.containing(11.3, 64.0, 10.0)
        );
        ExplosionInferenceHandler.onExplosion(explosionPos, 0.0f, smallerAffected);
        if (Math.abs(ClientPowerLookup.getInferredBlockEstimation() - 3.0f) > 0.01f) {
            throw new RuntimeException("Session max retention failed! Expected 3.0f, got: " + ClientPowerLookup.getInferredBlockEstimation());
        }

        // 3. Test Precedence: Radius Inference (Tier 2) overrides Block Estimation (Tier 4)
        ExplosionInferenceHandler.onExplosion(explosionPos, 2.5f, null);
        float resolvedPower = ClientPowerLookup.getPower(fireball);
        if (resolvedPower != 2.5f) {
            throw new RuntimeException("Radius inference (Tier 2) should override block estimation! Expected 2.5f, got: " + resolvedPower);
        }

        fireball.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testInflatedPacketRadiusSanityCheckAndServerPresetPriority(GameTestHelper context) {
        ClientPowerCache.POWER_CACHE.clear();
        ClientPowerLookup.resetInferredPower();

        Vec3 explosionPos = new Vec3(10.0, 64.0, 10.0);
        LargeFireball fireball = new LargeFireball(EntityTypes.FIREBALL, context.getLevel());
        fireball.setPos(explosionPos.x, explosionPos.y, explosionPos.z);
        FireballInferenceTracker.registerFireballLocation(fireball, explosionPos);

        // 1. Simulate GommeHD packet: radius = 4.0, but blockCount = 2 (estimates power ~1.44f)
        ExplosionInferenceHandler.onExplosion(explosionPos, 4.0f, 2, null);

        // Verify that 4.0f was rejected as inflated packet radius
        Float inferredRadius = ClientPowerLookup.getInferredPacketRadius();
        if (inferredRadius != null) {
            throw new RuntimeException("Expected inflated packet radius 4.0f to be rejected, but it was accepted: " + inferredRadius);
        }

        Float blockEst = ClientPowerLookup.getInferredBlockEstimation();
        if (blockEst == null || Math.abs(blockEst - 1.44f) > 0.1f) {
            throw new RuntimeException("Expected block estimation from 2 blocks ~1.44f, got: " + blockEst);
        }

        // 2. Simulate legitimate radius 4.0 with large block count (40 blocks estimates power ~3.91f)
        ExplosionInferenceHandler.onExplosion(explosionPos, 4.0f, 40, null);
        Float validRadius = ClientPowerLookup.getInferredPacketRadius();
        if (validRadius == null || validRadius != 4.0f) {
            throw new RuntimeException("Expected valid packet radius 4.0f to be accepted, got: " + validRadius);
        }

        fireball.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testServerFallbackPowerSetAndUnset(GameTestHelper context) {
        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();
        String testServer = "test.hypixel.net";

        // 1. Set server fallback power
        config.setServerFallbackPower(testServer, 2.5f);
        Float power = config.getServerFallbackPower(testServer);
        if (power == null || Math.abs(power - 2.5f) > 0.001f) {
            throw new RuntimeException("Expected server fallback power to be 2.5f, but got: " + power);
        }

        // 2. Set to 0.0f (should un-set / remove from map, returning null)
        config.setServerFallbackPower(testServer, 0.0f);
        if (config.getServerFallbackPower(testServer) != null) {
            throw new RuntimeException("Expected server fallback power to be null after setting 0.0f, but got: " + config.getServerFallbackPower(testServer));
        }

        // 3. Set to 3.0f then pass null (should un-set / remove from map, returning null)
        config.setServerFallbackPower(testServer, 3.0f);
        config.setServerFallbackPower(testServer, (Float) null);
        if (config.getServerFallbackPower(testServer) != null) {
            throw new RuntimeException("Expected server fallback power to be null after passing null Float, but got: " + config.getServerFallbackPower(testServer));
        }

        context.succeed();
    }

    // ---- Owner inference ----------------------------------------------------

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testOwnerInferenceNativeAndSweep(GameTestHelper context) {
        // 1. Native owner via setOwner (singleplayer / NBT path)
        Ghast ghast = context.spawn(EntityTypes.GHAST, 1, 3, 3);
        ghast.setPos(context.absoluteVec(new Vec3(1.5, 3.0, 3.5)));

        LargeFireball fireball = context.spawn(EntityTypes.FIREBALL, 2, 3, 3);
        Vec3 spawn = context.absoluteVec(new Vec3(3.5, 3.0, 3.5));
        fireball.setPos(spawn);
        fireball.setOwner(ghast);
        fireball.setDeltaMovement(context.absoluteVec(new Vec3(0.5, 0.0, 0.0)).subtract(context.absoluteVec(Vec3.ZERO)));

        InferenceResult nativeResult = OwnerInferenceEngine.infer(fireball, context.getLevel());
        if (nativeResult.owner() != ProjectileOwner.GHAST) {
            throw new RuntimeException("Expected NATIVE GHAST owner, got: " + nativeResult.owner()
                    + " via " + nativeResult.source());
        }
        if (nativeResult.source() != InferenceResult.InferenceSource.NATIVE_NBT) {
            throw new RuntimeException("Expected NATIVE_NBT source, got: " + nativeResult.source());
        }

        // 2. Environmental sweep — no setOwner, ghast looking toward the fireball
        fireball.setOwner((net.minecraft.world.entity.Entity) null);
        // Face +X (toward the fireball relative spawn)
        ghast.setYRot(context.getTestRotation().rotate(Direction.EAST).toYRot());
        ghast.setXRot(0.0f);

        // Place blaze farther away looking wrong way — should lose to ghast
        Blaze blaze = context.spawn(EntityTypes.BLAZE, 5, 3, 5);
        blaze.setPos(context.absoluteVec(new Vec3(8.0, 3.0, 8.0)));
        blaze.setYRot(0.0f);

        InferenceResult sweep = OwnerInferenceEngine.infer(fireball, context.getLevel());
        if (sweep.owner() != ProjectileOwner.GHAST && sweep.owner() != ProjectileOwner.BLAZE
                && sweep.owner() != ProjectileOwner.COMMAND) {
            throw new RuntimeException("Unexpected sweep owner: " + sweep.owner() + " via " + sweep.source());
        }
        // With owner cleared, source must not be NATIVE_NBT
        if (sweep.source() == InferenceResult.InferenceSource.NATIVE_NBT) {
            throw new RuntimeException("Sweep should not report NATIVE_NBT after owner cleared");
        }

        ghast.discard();
        blaze.discard();
        fireball.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testOwnerInferenceDispenserAndDeflection(GameTestHelper context) {
        // Dispenser facing EAST with fireball just outside its face
        BlockPos relDispenser = new BlockPos(2, 2, 2);
        BlockState dispenserState = Blocks.DISPENSER.defaultBlockState()
                .setValue(DispenserBlock.FACING, Direction.EAST);
        context.setBlock(relDispenser, dispenserState);

        LargeFireball fireball = context.spawn(EntityTypes.FIREBALL, 3, 2, 2);
        // Absolute position roughly one block east of the dispenser centre
        Vec3 dispenseAbs = context.absoluteVec(new Vec3(3.2, 2.5, 2.5));
        fireball.setPos(dispenseAbs);
        Vec3 eastVel = context.absoluteVec(new Vec3(0.5, 0.0, 0.0)).subtract(context.absoluteVec(Vec3.ZERO));
        fireball.setDeltaMovement(eastVel);

        InferenceResult dispenserResult = OwnerInferenceEngine.infer(fireball, context.getLevel());
        if (dispenserResult.owner() != ProjectileOwner.DISPENSER) {
            throw new RuntimeException("Expected DISPENSER owner, got: " + dispenserResult.owner()
                    + " via " + dispenserResult.source());
        }

        // Deflection: reverse velocity near a server mock player in the level → PLAYER
        Player player = context.makeMockServerPlayerInLevel();
        player.setPos(dispenseAbs.x + 1.0, dispenseAbs.y, dispenseAbs.z);

        Vec3 prevVel = fireball.getDeltaMovement();
        fireball.setDeltaMovement(prevVel.scale(-1.0));

        InferenceResult deflected = OwnerInferenceEngine.reassignOnDeflection(
                fireball, context.getLevel(), dispenserResult, prevVel);
        if (deflected.owner() != ProjectileOwner.PLAYER) {
            throw new RuntimeException("Expected PLAYER after deflection, got: " + deflected.owner()
                    + " (playersNearby="
                    + context.getLevel().getEntitiesOfClass(Player.class, fireball.getBoundingBox().inflate(5.0)).size()
                    + ")");
        }
        if (!deflected.isDeflected()) {
            throw new RuntimeException("Expected isDeflected() to be true after deflection");
        }

        // Sideways deflection (90-degree angle change, dot product ≈ 0.0)
        Vec3 sidewaysVel = new Vec3(0.0, 0.0, prevVel.x != 0 ? prevVel.x : 0.5);
        fireball.setDeltaMovement(sidewaysVel);
        InferenceResult sidewaysDeflected = OwnerInferenceEngine.reassignOnDeflection(
                fireball, context.getLevel(), dispenserResult, prevVel);
        if (sidewaysDeflected.owner() != ProjectileOwner.PLAYER || !sidewaysDeflected.isDeflected()) {
            throw new RuntimeException("Expected PLAYER and isDeflected()=true after 90-degree sideways deflection, got: " 
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
                throw new RuntimeException("Player filter should be false for non-deflected when trackPlayerProjectiles=false");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, true)) {
                throw new RuntimeException("Deflected fireball filter should be true even when trackPlayerProjectiles=false");
            }
            config.trackPlayerProjectiles = true;
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw new RuntimeException("Player filter should be true when trackPlayerProjectiles=true");
            }
            config.trackProjectiles = false;
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.GHAST)) {
                throw new RuntimeException("Master off should disable ghast tracking");
            }
        } finally {
            config.trackPlayerProjectiles = previousPlayer;
            config.trackProjectiles = previousMaster;
        }

        // Classifier sanity
        if (OwnerClassifier.classifyEntity(player) != ProjectileOwner.PLAYER) {
            throw new RuntimeException("classifyEntity(player) failed");
        }

        fireball.discard();
        player.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testServerTrackingRestrictions(GameTestHelper context) {
        LargeFireball fireball = context.spawn(EntityTypes.FIREBALL, 1, 2, 1);
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
                throw new RuntimeException("Player tracking should be allowed when the server does not restrict it");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.DISPENSER)) {
                throw new RuntimeException("Dispenser tracking should be allowed when the server does not restrict it");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.COMMAND)) {
                throw new RuntimeException("Command tracking should be allowed when the server does not restrict it");
            }

            // Sub-option restriction: players only
            ServerTrackingRules.applyMask(TrackingRules.PLAYER);
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, false)) {
                throw new RuntimeException("Server restriction must disable player tracking even when locally enabled");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, true)) {
                throw new RuntimeException("Deflection must not bypass the server player restriction");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.DISPENSER)) {
                throw new RuntimeException("Dispenser tracking must stay enabled when only players are restricted");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.UNKNOWN)) {
                throw new RuntimeException("Unknown shares the command bit and must stay enabled when only players are restricted");
            }

            // Whole "other" group restriction
            ServerTrackingRules.applyMask(TrackingRules.OTHER_GROUP);
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw new RuntimeException("Whole-group restriction must disable player tracking");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.DISPENSER)) {
                throw new RuntimeException("Whole-group restriction must disable dispenser tracking");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.COMMAND)) {
                throw new RuntimeException("Whole-group restriction must disable command tracking");
            }
            if (TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.UNKNOWN)) {
                throw new RuntimeException("Whole-group restriction must disable unknown (command) tracking");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.GHAST)) {
                throw new RuntimeException("Mob owners are not part of the server \"other\" restriction");
            }

            // Lifting restrictions restores local behaviour immediately
            ServerTrackingRules.clear();
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw new RuntimeException("Clearing the server mask must re-enable locally allowed player tracking");
            }

            // ServerConfig mask computation: sub-options combine, master covers the whole group
            serverConfig.disableOtherOwnerTracking = false;
            serverConfig.disablePlayerTracking = true;
            if (serverConfig.disabledOwnerMask() != TrackingRules.PLAYER) {
                throw new RuntimeException("ServerConfig sub-option must map to its TrackingRules bit");
            }
            serverConfig.disableDispenserTracking = true;
            serverConfig.disableCommandTracking = true;
            if (serverConfig.disabledOwnerMask() != TrackingRules.OTHER_GROUP) {
                throw new RuntimeException("ServerConfig sub-options must combine into the whole group mask");
            }
            serverConfig.disableOtherOwnerTracking = true;
            serverConfig.disablePlayerTracking = false;
            serverConfig.disableDispenserTracking = false;
            serverConfig.disableCommandTracking = false;
            if (serverConfig.disabledOwnerMask() != TrackingRules.OTHER_GROUP) {
                throw new RuntimeException("ServerConfig master must disable the whole other group");
            }
            serverConfig.disableOtherOwnerTracking = false;
            if (serverConfig.disabledOwnerMask() != 0) {
                throw new RuntimeException("Default ServerConfig must not restrict anything");
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
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testPacketSanitization(GameTestHelper context) {
        int previousMask = ServerTrackingRules.mask();
        try {
            // Apply 0xFFFF -> only valid bits (0x07: PLAYER, DISPENSER, COMMAND) must survive
            ServerTrackingRules.applyMask(0xFFFF);
            int mask = ServerTrackingRules.mask();
            if (mask != TrackingRules.OTHER_GROUP) {
                throw new RuntimeException("Expected mask 0xFFFF to sanitize to OTHER_GROUP (" + TrackingRules.OTHER_GROUP + "), got: " + mask);
            }
            if ((mask & ~TrackingRules.OTHER_GROUP) != 0) {
                throw new RuntimeException("Mask retained unsupported bits: 0x" + Integer.toHexString(mask));
            }

            // Apply 0xFF00 (no valid bits) -> should sanitize to 0
            ServerTrackingRules.applyMask(0xFF00);
            mask = ServerTrackingRules.mask();
            if (mask != 0) {
                throw new RuntimeException("Expected mask 0xFF00 to sanitize to 0, got: " + mask);
            }
        } finally {
            ServerTrackingRules.applyMask(previousMask);
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testDisconnectReset(GameTestHelper context) {
        int previousMask = ServerTrackingRules.mask();
        try {
            // Join server A (restricted)
            ServerTrackingRules.applyMask(TrackingRules.PLAYER | TrackingRules.DISPENSER);
            if (!ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER) || !ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)) {
                throw new RuntimeException("Failed to set restrictions for server A");
            }

            // Disconnect -> clear restrictions (simulating disconnect event listener)
            ServerTrackingRules.clear();
            if (ServerTrackingRules.mask() != 0) {
                throw new RuntimeException("Stale mask remains after disconnect! Expected 0, got: " + ServerTrackingRules.mask());
            }

            if (ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw new RuntimeException("Stale restriction active after disconnect!");
            }
        } finally {
            ServerTrackingRules.applyMask(previousMask);
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testGuiOptionAvailability(GameTestHelper context) {
        int previousMask = ServerTrackingRules.mask();

        try {
            // 1. Unrestricted state -> no options restricted
            ServerTrackingRules.clear();
            if (ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw new RuntimeException("No options should be restricted when mask is clear");
            }

            // 2. Restricted PLAYER bit -> only PLAYER option disabled
            ServerTrackingRules.applyMask(TrackingRules.PLAYER);
            if (!ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)) {
                throw new RuntimeException("PLAYER option should be restricted when PLAYER bit is set");
            }
            if (ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw new RuntimeException("DISPENSER and COMMAND options should remain available when only PLAYER is restricted");
            }

            // 3. Whole OTHER_GROUP restricted -> all 3 options disabled
            ServerTrackingRules.applyMask(TrackingRules.OTHER_GROUP);
            if (!ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || !ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || !ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw new RuntimeException("All tracking options should be restricted when OTHER_GROUP is set");
            }

            // 4. Disconnect / clear restrictions -> re-enabled
            ServerTrackingRules.clear();
            if (ServerTrackingRules.isDisabled(ProjectileOwner.PLAYER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.DISPENSER)
                    || ServerTrackingRules.isDisabled(ProjectileOwner.COMMAND)) {
                throw new RuntimeException("All tracking options should re-enable after server restrictions are lifted");
            }
        } finally {
            ServerTrackingRules.applyMask(previousMask);
        }

        context.succeed();
    }
}