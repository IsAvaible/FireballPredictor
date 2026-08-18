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
import com.simonconrad.fireballpredictor.client.render.WarningProjectileType;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import com.simonconrad.fireballpredictor.math.DamageCalculator;
import com.simonconrad.fireballpredictor.math.DamageCalculator.DamageEstimate;
import com.simonconrad.fireballpredictor.math.ImpactPredictor;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

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

    private static final Vec3 SPAWN_POS = new Vec3(1.5, 3.0, 3.5);
    private static final Vec3 INITIAL_VELOCITY = new Vec3(0.5, 0.0, 0.0);

    /**
     * Helper to construct a framework-native GameTestAssertException with a Component message.
     */
    private static GameTestAssertException fail(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
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

    private void buildWall(GameTestHelper context, BlockState state) {
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                context.setBlock(new BlockPos(WALL_X, y, z), state);
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
        projectile.setPos(context.absoluteVec(SPAWN_POS));
        Vec3 rotatedVelocity = context.absoluteVec(INITIAL_VELOCITY).subtract(context.absoluteVec(Vec3.ZERO));
        projectile.setDeltaMovement(rotatedVelocity);
        projectile.accelerationPower = accelerationPower;
        if (projectile instanceof WitherSkull skull) {
            skull.setDangerous(isCharged);
        }
        return projectile;
    }

    private List<BlockPos> getBrokenBlocks(GameTestHelper context, Block originalBlock) {
        List<BlockPos> actualAbsoluteBroken = new ArrayList<>();
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                BlockPos relPos = new BlockPos(WALL_X, y, z);
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
        PredictionData prediction = TrajectoryPredictor.computePrediction(trajResult, projectile.tickCount);
        return prediction.brokenBlocks();
    }

    private void assertExplosionDestruction(
            GameTestHelper context,
            AbstractHurtingProjectile projectile,
            Block wallBlock,
            int minExpectedActualCount
    ) {
        List<BlockPos> predictedAbsoluteBroken = getPredictedBrokenBlocks(projectile, context);
        if (predictedAbsoluteBroken.isEmpty()) {
            throw fail("Predicted 0 broken blocks, but it should hit the wall and break blocks.");
        }

        Set<BlockPos> predictedSet = new HashSet<>(predictedAbsoluteBroken);

        context.succeedWhen(() -> {
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
        });
    }

    private void assertNoDestruction(
            GameTestHelper context,
            AbstractHurtingProjectile projectile,
            Block wallBlock
    ) {
        List<BlockPos> predictedAbsoluteBroken = getPredictedBrokenBlocks(projectile, context);
        if (!predictedAbsoluteBroken.isEmpty()) {
            throw fail("Predicted " + predictedAbsoluteBroken.size() + " broken blocks, but it should not break any.");
        }

        context.succeedWhen(() -> {
            if (projectile.isAlive()) {
                throw fail("Waiting for projectile to collide/explode...");
            }
            List<BlockPos> actualAbsoluteBroken = getBrokenBlocks(context, wallBlock);
            if (!actualAbsoluteBroken.isEmpty()) {
                throw fail("Explosion actually broke " + actualAbsoluteBroken.size() + " blocks, but was expected to break 0.");
            }
        });
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testFireballPredictionAndExplosion(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWitherSkullPredictionAndExplosion(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, false);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullPredictionAndExplosion(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.DIRT, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstObsidian(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OBSIDIAN, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalWitherSkullAgainstObsidian(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.OBSIDIAN);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, false);
        assertNoDestruction(context, skull, Blocks.OBSIDIAN);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstReinforcedDeepslate(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.REINFORCED_DEEPSLATE);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        assertNoDestruction(context, skull, Blocks.REINFORCED_DEEPSLATE);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNormalFireballAgainstWaterloggedSlab(GameTestHelper context) {
        resetGlobalState();
        BlockState waterloggedSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        assertNoDestruction(context, fireball, Blocks.OAK_SLAB);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstWaterloggedSlab(GameTestHelper context) {
        resetGlobalState();
        BlockState waterloggedSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        assertExplosionDestruction(context, skull, Blocks.OAK_SLAB, 1);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testHighPowerFireballPredictionAndExplosion(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ((FireballEntityAccessor) fireball).setExplosionPower(3);
        assertExplosionDestruction(context, fireball, Blocks.DIRT, 10);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWindChargePredictionAndExplosion(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        WindCharge windCharge = spawnProjectile(context, EntityTypes.WIND_CHARGE, 0.0, false);
        assertNoDestruction(context, windCharge, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWindChargeZeroDamageEstimate(GameTestHelper context) {
        resetGlobalState();
        WindCharge windCharge = spawnProjectile(context, EntityTypes.WIND_CHARGE, 0.0, false);
        float resolvedPower = ImpactPredictor.resolveExplosionPower(windCharge);
        if (Math.abs(resolvedPower - 1.2f) > 1e-4) {
            throw fail("WindCharge should have 1.2 power, but got resolved=" + resolvedPower);
        }

        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        DamageEstimate directEstimate = DamageCalculator.calculateDirectHit(
                player.position(), resolvedPower, player, context.getLevel(), windCharge, null);
        if (!directEstimate.inRange()) {
            throw fail("WindCharge direct hit should be in range");
        }
        if (directEstimate.finalDamage() != 0.0f) {
            throw fail("WindCharge direct hit should deal 0 final damage, but got " + directEstimate.finalDamage());
        }
        if (directEstimate.knockbackBlocksPerSecond() <= 0.0) {
            throw fail("WindCharge direct hit should have positive knockback, but got " + directEstimate.knockbackBlocksPerSecond());
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testBreezeWindChargeKnockbackAndDamageEstimate(GameTestHelper context) {
        resetGlobalState();
        BreezeWindCharge breezeCharge = spawnProjectile(context, EntityTypes.BREEZE_WIND_CHARGE, 0.0, false);
        float resolvedPower = ImpactPredictor.resolveExplosionPower(breezeCharge);
        if (Math.abs(resolvedPower - 3.0f) > 1e-4) {
            throw fail("BreezeWindCharge should have 3.0 power, but got resolved=" + resolvedPower);
        }

        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        DamageEstimate directEstimate = DamageCalculator.calculateDirectHit(
                player.position(), resolvedPower, player, context.getLevel(), breezeCharge, null);
        if (!directEstimate.inRange()) {
            throw fail("BreezeWindCharge direct hit should be in range");
        }
        if (directEstimate.finalDamage() != 0.0f) {
            throw fail("BreezeWindCharge direct hit should deal 0 final damage, but got " + directEstimate.finalDamage());
        }
        if (directEstimate.knockbackBlocksPerSecond() <= 0.0) {
            throw fail("BreezeWindCharge direct hit should have positive knockback, but got " + directEstimate.knockbackBlocksPerSecond());
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWaterDragPrediction(GameTestHelper context) {
        resetGlobalState();
        for (int x = 0; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    context.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.0, false);

        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(fireball, context.getLevel());

        Vec3 v0 = trajResult.velocities().get(0);
        Vec3 v1 = trajResult.velocities().get(1);
        double expectedSpeed1 = v0.length() * 0.8;
        if (Math.abs(v1.length() - expectedSpeed1) > 1e-4) {
            throw fail("Expected water drag 0.8 to reduce speed from " + v0.length() + " to " + expectedSpeed1 + ", but got " + v1.length());
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWindChargeWaterDragPrediction(GameTestHelper context) {
        resetGlobalState();
        for (int x = 0; x <= 5; x++) {
            for (int y = 1; y <= 5; y++) {
                for (int z = 1; z <= 5; z++) {
                    context.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            }
        }
        WindCharge windCharge = spawnProjectile(context, EntityTypes.WIND_CHARGE, 0.0, false);

        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(windCharge, context.getLevel());

        Vec3 v0 = trajResult.velocities().get(0);
        Vec3 v1 = trajResult.velocities().get(1);
        if (Math.abs(v1.length() - v0.length()) > 1e-4) {
            throw fail("Expected wind charge in water to maintain 1.0 drag (speed " + v0.length() + "), but got " + v1.length());
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testBlockGetterWaterDetection(GameTestHelper context) {
        resetGlobalState();
        BlockPos waterPos = context.absolutePos(new BlockPos(2, 2, 2));
        context.setBlock(new BlockPos(2, 2, 2), Blocks.WATER);

        com.simonconrad.fireballpredictor.math.BlockStateSnapshot snapshot =
            new com.simonconrad.fireballpredictor.math.BlockStateSnapshot(
                context.getLevel(), waterPos.offset(-1, -1, -1), waterPos.offset(1, 1, 1)
            );

        boolean touching = TrajectoryPredictor.isTouchingWater(
            snapshot, waterPos.getX() + 0.1, waterPos.getY() + 0.1, waterPos.getZ() + 0.1,
            waterPos.getX() + 0.9, waterPos.getY() + 0.9, waterPos.getZ() + 0.9
        );

        if (!touching) {
            throw fail("Expected snapshot BlockGetter isTouchingWater to return true for water block");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testSmallFireballPowerAndNoDestruction(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        SmallFireball fireball = spawnProjectile(context, EntityTypes.SMALL_FIREBALL, 0.0, false);
        if (com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball) != 0.0f) {
            throw fail("SmallFireball explosion power expected to be 0.0f, got: " + com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball));
        }
        assertNoDestruction(context, fireball, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDragonFireballPowerAndNoDestruction(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        DragonFireball fireball = spawnProjectile(context, EntityTypes.DRAGON_FIREBALL, 0.0, false);
        if (com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball) != 0.0f) {
            throw fail("DragonFireball explosion power expected to be 0.0f, got: " + com.simonconrad.fireballpredictor.math.ImpactPredictor.resolveExplosionPower(fireball));
        }
        assertNoDestruction(context, fireball, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testInferredExplosionPowerFallback(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);

        // Spawn fireball 1 and simulate its trajectory
        LargeFireball fireball1 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        TrajectoryPredictor.TrajectoryResult traj = TrajectoryPredictor.simulateTrajectory(fireball1, context.getLevel());
        PredictionData pred = TrajectoryPredictor.computePrediction(traj, fireball1.tickCount);
        Vec3 hitPos = pred.hitResult() != null ? pred.hitResult().getLocation() : fireball1.position();

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
        LargeFireball fireball2 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        float resolvedPower = ClientPowerLookup.getPower(fireball2);
        if (resolvedPower != 3.0f) {
            throw fail("Expected resolved power for unsynced fireball to be inferred 3.0f, but got: " + resolvedPower);
        }

        ((FireballEntityAccessor) fireball2).setExplosionPower(3);
        assertExplosionDestruction(context, fireball2, Blocks.DIRT, 10);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testZeroRadiusAffectedBlockEstimationAndHierarchy(GameTestHelper context) {
        resetGlobalState();

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
            throw fail("Expected inferred block estimation ~3.0f, but got: " + blockEst);
        }

        // 2. Test session max retention: smaller explosion (dMax = 1.3 -> 1.0f) should not decrease retained estimation (3.0f)
        List<BlockPos> smallerAffected = List.of(
                BlockPos.containing(11.3, 64.0, 10.0)
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
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testInflatedPacketRadiusSanityCheckAndServerPresetPriority(GameTestHelper context) {
        resetGlobalState();

        Vec3 explosionPos = new Vec3(10.0, 64.0, 10.0);
        LargeFireball fireball = new LargeFireball(EntityTypes.FIREBALL, context.getLevel());
        fireball.setPos(explosionPos.x, explosionPos.y, explosionPos.z);
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
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testServerFallbackPowerSetAndUnset(GameTestHelper context) {
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

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testOwnerInferenceNativeAndSweep(GameTestHelper context) {
        resetGlobalState();

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
            throw fail("Expected NATIVE GHAST owner, got: " + nativeResult.owner()
                    + " via " + nativeResult.source());
        }
        if (nativeResult.source() != InferenceResult.InferenceSource.NATIVE_NBT) {
            throw fail("Expected NATIVE_NBT source, got: " + nativeResult.source());
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
            throw fail("Unexpected sweep owner: " + sweep.owner() + " via " + sweep.source());
        }
        // With owner cleared, source must not be NATIVE_NBT
        if (sweep.source() == InferenceResult.InferenceSource.NATIVE_NBT) {
            throw fail("Sweep should not report NATIVE_NBT after owner cleared");
        }

        ghast.discard();
        blaze.discard();
        fireball.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testOwnerInferenceDispenserAndDeflection(GameTestHelper context) {
        resetGlobalState();

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
            throw fail("Expected DISPENSER owner, got: " + dispenserResult.owner()
                    + " via " + dispenserResult.source());
        }

        // Deflection: reverse velocity near a server mock player in the level → PLAYER
        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(dispenseAbs.x + 1.0, dispenseAbs.y, dispenseAbs.z);
        context.getLevel().addFreshEntity(player);

        Vec3 prevVel = fireball.getDeltaMovement();
        fireball.setDeltaMovement(prevVel.scale(-1.0));

        InferenceResult deflected = OwnerInferenceEngine.reassignOnDeflection(
                fireball, context.getLevel(), dispenserResult, prevVel);
        if (deflected.owner() != ProjectileOwner.PLAYER) {
            throw fail("Expected PLAYER after deflection, got: " + deflected.owner()
                    + " (playersNearby="
                    + context.getLevel().getEntitiesOfClass(Player.class, fireball.getBoundingBox().inflate(5.0)).size()
                    + ")");
        }
        if (!deflected.isDeflected()) {
            throw fail("Expected isDeflected() to be true after deflection");
        }

        // Sideways deflection (90-degree angle change, dot product ≈ 0.0)
        Vec3 sidewaysVel = new Vec3(0.0, 0.0, prevVel.x != 0 ? prevVel.x : 0.5);
        fireball.setDeltaMovement(sidewaysVel);
        InferenceResult sidewaysDeflected = OwnerInferenceEngine.reassignOnDeflection(
                fireball, context.getLevel(), dispenserResult, prevVel);
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
                throw fail("Player filter should be false for non-deflected when trackPlayerProjectiles=false");
            }
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER, true)) {
                throw fail("Deflected fireball filter should be true even when trackPlayerProjectiles=false");
            }
            config.trackPlayerProjectiles = true;
            if (!TrackedProjectile.evaluateFilter(fireball, ProjectileOwner.PLAYER)) {
                throw fail("Player filter should be true when trackPlayerProjectiles=true");
            }

            // Wind charge owner filter priority check: Owner tracking > Projectile type tracking
            WindCharge testWindCharge = spawnProjectile(context, EntityTypes.WIND_CHARGE, 0.0, false);
            config.trackProjectiles = true;
            config.trackWindCharges = true;
            config.trackPlayerProjectiles = false;
            if (TrackedProjectile.evaluateFilter(testWindCharge, ProjectileOwner.PLAYER, false)) {
                throw fail("Player-owned wind charge filter should be false when trackPlayerProjectiles=false even if trackWindCharges=true");
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
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testServerTrackingRestrictions(GameTestHelper context) {
        resetGlobalState();

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
                throw fail("Player tracking should be allowed when the server does not restrict it");
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
                throw fail("Mob owners are not part of the server \"other\" restriction");
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
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testPacketSanitization(GameTestHelper context) {
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

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testDisconnectReset(GameTestHelper context) {
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

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 10)
    public void testGuiOptionAvailability(GameTestHelper context) {
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

        context.succeed();
    }

    // ------------------------------------------------------------------
    // Damage & Knockback Estimator (DamageCalculator)
    // ------------------------------------------------------------------

    /**
     * Spawns a survival mock player at the given relative position with full HP and no velocity.
     */
    private Player spawnMockPlayer(GameTestHelper context, Vec3 relativePos) {
        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(context.absoluteVec(relativePos));
        player.setHealth(20.0F);
        player.setAbsorptionAmount(0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        context.getLevel().addFreshEntity(player);
        return player;
    }

    private ItemStack enchantedStack(ItemStack stack, Holder<Enchantment> enchantment) {
        stack.enchant(enchantment, 4);
        return stack;
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorZeroPowerNoDamage(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(8.0, 3.0, 3.5));

        DamageSource source = level.damageSources().explosion(null, null);
        DamageEstimate estimate = DamageCalculator.calculate(
                context.absoluteVec(new Vec3(4.0, 3.0, 3.5)), 0.0F, player, level, source);

        if (estimate.inRange() || estimate.finalDamage() != 0.0F || estimate.heartsLost() != 0.0F) {
            throw fail("Zero-power explosion must be out of range with zero damage, got " + estimate);
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorOutOfRange(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(10.0, 3.0, 3.5));

        DamageSource source = level.damageSources().explosion(null, null);
        // Blast radius = power * 2 = 2 blocks; the player is 6 blocks away.
        DamageEstimate estimate = DamageCalculator.calculate(
                context.absoluteVec(new Vec3(4.0, 3.0, 3.5)), 1.0F, player, level, source);

        if (estimate.inRange()) {
            throw fail("Player outside blast radius must be out of range, got " + estimate);
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorMatchesRealExplosionNoArmor(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(8.0, 3.0, 3.5));

        Vec3 explosionPos = context.absoluteVec(new Vec3(7.0, 3.0, 3.5));
        float power = 1.0F;

        // 1. Estimator prediction.
        DamageSource source = level.damageSources().explosion(null, null);

        DamageEstimate estimate = DamageCalculator.calculate(explosionPos, power, player, level, source);
        if (!estimate.inRange()) {
            throw fail("Estimate unexpectedly out of range: " + estimate);
        }

        // 2. Real vanilla explosion ground truth. Damage is deterministic: getSeenPercent and the
        //    damage formula use no RNG, and entity damage is applied before any block destruction.
        player.setDeltaMovement(Vec3.ZERO);
        float hpBefore = player.getHealth();
        level.explode(null, explosionPos.x, explosionPos.y, explosionPos.z, power, false, Level.ExplosionInteraction.NONE);
        float realDamage = hpBefore - player.getHealth();
        Vec3 realVelocity = player.getDeltaMovement();

        if (Math.abs(realDamage - estimate.finalDamage()) > 0.05F) {
            throw fail("No-armor damage mismatch: estimator=" + estimate.finalDamage() + " real=" + realDamage);
        }
        double expectedImpulse = estimate.knockbackBlocksPerSecond() / 20.0;
        double realImpulse = realVelocity.length();
        if (Math.abs(realImpulse - expectedImpulse) > 0.05) {
            throw fail("Knockback mismatch: estimator=" + expectedImpulse + " real=" + realImpulse);
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorWithArmorAndBlastProtection(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(8.0, 3.0, 3.5));

        // Full diamond armor with Blast Protection IV on every piece: EPF 8 per piece (32 total,
        // soft-capped at 20) and 4 * 0.15 = 0.6 EXPLOSION_KNOCKBACK_RESISTANCE.
        Registry<Enchantment> enchantments = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Holder<Enchantment> blastProtection = enchantments.getOrThrow(Enchantments.BLAST_PROTECTION);
        player.setItemSlot(EquipmentSlot.HEAD, enchantedStack(new ItemStack(Items.DIAMOND_HELMET), blastProtection));
        player.setItemSlot(EquipmentSlot.CHEST, enchantedStack(new ItemStack(Items.DIAMOND_CHESTPLATE), blastProtection));
        player.setItemSlot(EquipmentSlot.LEGS, enchantedStack(new ItemStack(Items.DIAMOND_LEGGINGS), blastProtection));
        player.setItemSlot(EquipmentSlot.FEET, enchantedStack(new ItemStack(Items.DIAMOND_BOOTS), blastProtection));

        Vec3 explosionPos = context.absoluteVec(new Vec3(7.0, 3.0, 3.5));
        float power = 1.0F;

        DamageSource source = level.damageSources().explosion(null, null);
        DamageEstimate estimate = DamageCalculator.calculate(explosionPos, power, player, level, source);
        if (!estimate.inRange()) {
            throw fail("Estimate unexpectedly out of range: " + estimate);
        }

        player.setDeltaMovement(Vec3.ZERO);
        float hpBefore = player.getHealth();
        level.explode(null, explosionPos.x, explosionPos.y, explosionPos.z, power, false, Level.ExplosionInteraction.NONE);
        float realDamage = hpBefore - player.getHealth();
        Vec3 realVelocity = player.getDeltaMovement();

        if (Math.abs(realDamage - estimate.finalDamage()) > 0.05F) {
            throw fail("Armor/Blast Protection damage mismatch: estimator=" + estimate.finalDamage() + " real=" + realDamage);
        }
        double expectedImpulse = estimate.knockbackBlocksPerSecond() / 20.0;
        double realImpulse = realVelocity.length();
        if (Math.abs(realImpulse - expectedImpulse) > 0.05) {
            throw fail("Knockback mismatch with Blast Protection: estimator=" + expectedImpulse + " real=" + realImpulse);
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorCoverReducesDamage(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(8.0, 3.0, 3.5));

        Vec3 explosionPos = context.absoluteVec(new Vec3(6.5, 3.0, 3.5));
        float power = 1.0F;

        DamageSource source = level.damageSources().explosion(null, null);
        DamageEstimate uncovered = DamageCalculator.calculate(explosionPos, power, player, level, source);
        if (!uncovered.inRange()) {
            throw fail("Uncovered estimate unexpectedly out of range: " + uncovered);
        }

        // Build a stone wall directly between the player and the blast (relative x = 7).
        for (int y = 1; y <= 5; y++) {
            for (int z = 3; z <= 4; z++) {
                context.setBlock(new BlockPos(7, y, z), Blocks.STONE);
            }
        }

        DamageEstimate covered = DamageCalculator.calculate(explosionPos, power, player, level, source);

        if (!(covered.seenPercent() < uncovered.seenPercent())) {
            throw fail("Cover should reduce seenPercent, uncovered=" + uncovered.seenPercent()
                    + " covered=" + covered.seenPercent());
        }
        if (!(covered.finalDamage() < uncovered.finalDamage())) {
            throw fail("Cover should reduce final damage, uncovered=" + uncovered.finalDamage()
                    + " covered=" + covered.finalDamage());
        }

        // Real explosion behind the intact wall: the damage phase runs before block destruction,
        // so the estimator and the real explosion both see the wall.
        player.setDeltaMovement(Vec3.ZERO);
        float hpBefore = player.getHealth();
        level.explode(null, explosionPos.x, explosionPos.y, explosionPos.z, power, false, Level.ExplosionInteraction.NONE);
        float realDamage = hpBefore - player.getHealth();
        if (Math.abs(realDamage - covered.finalDamage()) > 0.05F) {
            throw fail("Cover damage mismatch: estimator=" + covered.finalDamage() + " real=" + realDamage);
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorDirectHit(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(2.0, 3.0, 3.5));
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);

        Vec3 impactPos = context.absoluteVec(new Vec3(2.0, 3.0, 3.5));
        DamageEstimate estimate = DamageCalculator.calculateDirectHit(impactPos, 1.0F, player, level, fireball, null);

        if (!estimate.inRange()) {
            throw fail("Direct hit must be in range, got " + estimate);
        }
        // Under vanilla hurt cooldown (invulnerability frames), the direct hit (6.0) and point-blank
        // power-1 blast (raw 15.0) happen in the same tick, taking max(6.0, 15.0) = 15.0 raw damage.
        float expectedRaw = 15.0F;
        if (Math.abs(estimate.rawDamage() - expectedRaw) > 0.05F) {
            throw fail("Direct-hit raw damage mismatch: expected=" + expectedRaw + " got=" + estimate.rawDamage());
        }
        if (Math.abs(estimate.heartsLost() - 7.5F) > 0.05F) {
            throw fail("Direct hit hearts lost should be 7.5, got " + estimate.heartsLost());
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorWitherSkullDirectHit(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(2.0, 3.0, 3.5));
        WitherSkull skull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, false);

        Vec3 impactPos = context.absoluteVec(new Vec3(2.0, 3.0, 3.5));
        DamageEstimate estimate = DamageCalculator.calculateDirectHit(impactPos, 1.0F, player, level, skull, null);

        if (!estimate.inRange()) {
            throw fail("Direct hit must be in range, got " + estimate);
        }
        // WitherSkull deals 8.0 direct magic damage; under point-blank power-1 blast (15.0 raw), max is 15.0 raw
        float expectedRaw = 15.0F;
        if (Math.abs(estimate.rawDamage() - expectedRaw) > 0.05F) {
            throw fail("WitherSkull direct-hit raw damage mismatch: expected=" + expectedRaw + " got=" + estimate.rawDamage());
        }
        if (Math.abs(estimate.heartsLost() - 7.5F) > 0.05F) {
            throw fail("WitherSkull direct hit hearts lost should be 7.5, got " + estimate.heartsLost());
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorDirectHitRealCollision(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(8.0, 3.0, 3.5));

        Vec3 impactPos = context.absoluteVec(new Vec3(8.0, 3.0, 3.5));
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.0, false);
        fireball.setPos(impactPos);

        DamageEstimate estimate = DamageCalculator.calculateDirectHit(impactPos, 1.0F, player, level, fireball, null);

        // Real direct collision: LargeFireball.onHitEntity deals 6.0 direct damage and explosion deals point-blank blast
        float hpBefore = player.getHealth();
        fireball.teleportTo(player.getX(), player.getY() + 0.5, player.getZ());
        
        // Trigger direct impact logic via vanilla hurt or tick
        DamageSource directSource = level.damageSources().fireball(fireball, null);
        player.hurtServer(level, directSource, DamageCalculator.DIRECT_HIT_DAMAGE);
        level.explode(fireball, impactPos.x, impactPos.y, impactPos.z, 1.0F, false, Level.ExplosionInteraction.NONE);

        float realDamage = hpBefore - player.getHealth();
        if (Math.abs(realDamage - estimate.finalDamage()) > 0.05F) {
            throw fail("Direct hit real damage mismatch: estimator=" + estimate.finalDamage() + " real=" + realDamage);
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 60)
    public void testDamageCalculatorAbsorptionClamp(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();
        Player player = spawnMockPlayer(context, new Vec3(8.0, 4.0, 3.5));
        player.setAbsorptionAmount(4.0F);

        Vec3 explosionPos = context.absoluteVec(new Vec3(8.0, 4.0, 3.5));
        DamageSource source = level.damageSources().explosion(null, null);
        DamageEstimate estimate = DamageCalculator.calculate(explosionPos, 4.0F, player, level, source);

        if (!estimate.inRange()) {
            throw fail("Point-blank blast must be in range, got " + estimate);
        }
        if (Math.abs(estimate.heartsLost() - 6.722223F) > 0.05F) {
            throw fail("Hearts lost mismatch: expected 6.72, got " + estimate.heartsLost());
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testTrajectorySimulationEntityCollision(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();

        // Build a wall far behind at relative x = 10
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlock(new BlockPos(10, y, z), Blocks.STONE);
            }
        }

        // Spawn a player at relative (5.0, 3.0, 3.5) directly in front of the projectile path
        Player player = spawnMockPlayer(context, new Vec3(5.0, 3.0, 3.5));

        // Spawn a fireball at SPAWN_POS = (1.5, 3.0, 3.5) heading +X towards the player
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.0, false);

        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(fireball, level);

        // 1. Rendering: The path and visual hitResult continue past the entity to the wall behind at x = 10
        if (trajResult.hitResult() == null) {
            throw fail("Expected trajectory to collide with the wall, but hitResult is null");
        }
        if (trajResult.hitResult().getType() != HitResult.Type.BLOCK) {
            throw fail("Expected visual hitResult to be BLOCK at the wall, but got " + trajResult.hitResult().getType());
        }
        Vec3 wallHitPos = trajResult.hitResult().getLocation();
        Vec3 wallAbsPos = context.absoluteVec(new Vec3(10.0, 3.0, 3.5));
        if (wallHitPos.distanceToSqr(wallAbsPos) > 9.0) {
            throw fail("Expected visual hit position near wall " + wallAbsPos + ", but got " + wallHitPos);
        }

        // 2. Damage calculation: damageHitResult captures the intercepted player
        if (trajResult.damageHitResult() == null) {
            throw fail("Expected damageHitResult to capture the player, but it is null");
        }
        if (trajResult.damageHitResult().getType() != HitResult.Type.ENTITY) {
            throw fail("Expected damageHitResult to be ENTITY, but got " + trajResult.damageHitResult().getType());
        }
        if (!(trajResult.damageHitResult() instanceof EntityHitResult entityHit)) {
            throw fail("Expected damageHitResult to be instance of EntityHitResult");
        }
        if (entityHit.getEntity() != player) {
            throw fail("Expected hit entity to be player, but was " + entityHit.getEntity());
        }

        Vec3 entityHitPos = trajResult.damageHitResult().getLocation();
        Vec3 playerAbsPos = context.absoluteVec(new Vec3(5.0, 3.0, 3.5));
        if (entityHitPos.distanceToSqr(playerAbsPos) > 4.0) {
            throw fail("Expected damage hit position near player " + playerAbsPos + ", but got " + entityHitPos);
        }

        // Test damage prediction using the entity hit position
        DamageEstimate estimate = DamageCalculator.calculateDirectHit(entityHitPos, trajResult.explosionPower(), player, level, fireball, null);
        if (!estimate.inRange()) {
            throw fail("Direct hit damage estimate should be in range");
        }
        if (estimate.finalDamage() <= 0.0F) {
            throw fail("Direct hit damage should be positive, got " + estimate.finalDamage());
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testCanHitEntityFallback(GameTestHelper context) {
        resetGlobalState();
        Player player = spawnMockPlayer(context, new Vec3(5.0, 3.0, 3.5));
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.0, false);

        // Standard querying returns true for player
        boolean canHit = TrajectoryPredictor.canHitEntity(fireball, player);
        if (!canHit) {
            throw fail("Expected canHitEntity to return true for player");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDynamicEntityMovementDamagePrediction(GameTestHelper context) {
        resetGlobalState();
        ServerLevel level = context.getLevel();

        // Build a wall far behind at relative x = 10
        for (int y = 1; y <= 5; y++) {
            for (int z = 1; z <= 5; z++) {
                context.setBlock(new BlockPos(10, y, z), Blocks.STONE);
            }
        }

        // Spawn a player at relative (5.0, 3.0, 3.5) directly in front of the projectile path
        Player player = spawnMockPlayer(context, new Vec3(5.0, 3.0, 3.5));

        // Spawn a fireball at SPAWN_POS = (1.5, 3.0, 3.5) heading +X towards the player
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.0, false);

        PredictionData predData = TrajectoryPredictor.predict(fireball, level);

        // 1. Initially, player is in the path -> findDamageHitResult returns EntityHitResult
        HitResult initialHit = TrajectoryPredictor.findDamageHitResult(level, fireball, predData);
        if (initialHit == null || initialHit.getType() != HitResult.Type.ENTITY) {
            throw fail("Expected initial damageHit to be ENTITY, got " + (initialHit != null ? initialHit.getType() : "null"));
        }

        // 2. Player moves away from the path to relative y = 10.0
        player.setPos(context.absoluteVec(new Vec3(5.0, 10.0, 3.5)));

        // findDamageHitResult should now dynamically return the BLOCK hit at the wall
        HitResult movedHit = TrajectoryPredictor.findDamageHitResult(level, fireball, predData);
        if (movedHit == null || movedHit.getType() != HitResult.Type.BLOCK) {
            throw fail("Expected damageHit after player moves to be BLOCK, got " + (movedHit != null ? movedHit.getType() : "null"));
        }

        // 3. Player moves back into the path
        player.setPos(context.absoluteVec(new Vec3(5.0, 3.0, 3.5)));

        HitResult returnedHit = TrajectoryPredictor.findDamageHitResult(level, fireball, predData);
        if (returnedHit == null || returnedHit.getType() != HitResult.Type.ENTITY) {
            throw fail("Expected damageHit after player returns to be ENTITY, got " + (returnedHit != null ? returnedHit.getType() : "null"));
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testWarningProjectileTypeResolution(GameTestHelper context) {
        resetGlobalState();

        LargeFireball largeFireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.0, false);
        SmallFireball smallFireball = spawnProjectile(context, EntityTypes.SMALL_FIREBALL, 0.0, false);
        DragonFireball dragonFireball = spawnProjectile(context, EntityTypes.DRAGON_FIREBALL, 0.0, false);
        WitherSkull normalSkull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, false);
        WitherSkull chargedSkull = spawnProjectile(context, EntityTypes.WITHER_SKULL, 0.0, true);
        WindCharge windCharge = spawnProjectile(context, EntityTypes.WIND_CHARGE, 0.0, false);

        if (WarningProjectileType.fromProjectile(largeFireball) != WarningProjectileType.FIREBALL
                || WarningProjectileType.FIREBALL.icon().getItem() != Items.FIRE_CHARGE) {
            throw fail("LargeFireball should resolve to WarningProjectileType.FIREBALL with FIRE_CHARGE icon");
        }
        if (WarningProjectileType.fromProjectile(smallFireball) != WarningProjectileType.FIREBALL) {
            throw fail("SmallFireball should resolve to WarningProjectileType.FIREBALL");
        }
        if (WarningProjectileType.fromProjectile(dragonFireball) != WarningProjectileType.DRAGON_FIREBALL
                || WarningProjectileType.DRAGON_FIREBALL.barFillColor() != 0xFFC832D4
                || WarningProjectileType.DRAGON_FIREBALL.customTexture() == null) {
            throw fail("DragonFireball should resolve to WarningProjectileType.DRAGON_FIREBALL with purple bar and dragon fireball texture");
        }
        if (WarningProjectileType.fromProjectile(normalSkull) != WarningProjectileType.WITHER_SKULL
                || WarningProjectileType.WITHER_SKULL.icon().getItem() != Items.WITHER_SKELETON_SKULL) {
            throw fail("Normal WitherSkull should resolve to WarningProjectileType.WITHER_SKULL with WITHER_SKELETON_SKULL icon");
        }
        if (WarningProjectileType.fromProjectile(chargedSkull) != WarningProjectileType.WITHER_SKULL) {
            throw fail("Charged WitherSkull should resolve to WarningProjectileType.WITHER_SKULL");
        }
        if (WarningProjectileType.fromProjectile(windCharge) != WarningProjectileType.WIND_CHARGE
                || WarningProjectileType.WIND_CHARGE.icon().getItem() != Items.WIND_CHARGE) {
            throw fail("WindCharge should resolve to WarningProjectileType.WIND_CHARGE with WIND_CHARGE icon");
        }

        context.succeed();
    }


    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testVisualThemesRosterAndColorMath(GameTestHelper context) {
        resetGlobalState();

        VisualTheme[] themes = VisualTheme.values();
        if (themes.length != 16) {
            throw fail("Expected 16 visual themes, found: " + themes.length);
        }

        if (VisualTheme.DEFAULT.isCustomTheme()) {
            throw fail("VisualTheme.DEFAULT should return false for isCustomTheme()");
        }

        int fallbackRgb = 0xFF8000;
        for (VisualTheme theme : themes) {
            if (theme.getKey() == null || theme.getKey().isEmpty()) {
                throw fail("Theme " + theme.name() + " has null or empty key");
            }
            if (theme.getDisplayName() == null) {
                throw fail("Theme " + theme.name() + " returned null getDisplayName()");
            }

            if (theme != VisualTheme.DEFAULT && !theme.isCustomTheme()) {
                throw fail("Theme " + theme.name() + " should return true for isCustomTheme()");
            }

            // Test ribbon color evaluation across progress
            for (float p = 0.0f; p <= 1.0f; p += 0.25f) {
                int shroudRgb = theme.getRibbonColorPacked(p, 10.0, 0, false, fallbackRgb);
                int coreRgb = theme.getRibbonColorPacked(p, 10.0, 0, true, fallbackRgb);
                float alphaMod = theme.getRibbonAlphaModulation(p, 10.0, 0);

                if (shroudRgb < 0 || shroudRgb > 0xFFFFFF) {
                    throw fail("Invalid shroud RGB for theme " + theme.name() + ": 0x" + Integer.toHexString(shroudRgb));
                }
                if (coreRgb < 0 || coreRgb > 0xFFFFFF) {
                    throw fail("Invalid core RGB for theme " + theme.name() + ": 0x" + Integer.toHexString(coreRgb));
                }
                if (alphaMod < 0.0f || alphaMod > 3.0f) {
                    throw fail("Invalid alpha modulation for theme " + theme.name() + ": " + alphaMod);
                }
            }

            // Test dome color evaluation across lat/lon
            for (float lat = 0.0f; lat <= 1.0f; lat += 0.25f) {
                for (float lon = 0.0f; lon <= 1.0f; lon += 0.25f) {
                    int domeRgb = theme.getDomeColorPacked(null, null, lat, lon, 10.0, fallbackRgb);
                    if (domeRgb < 0 || domeRgb > 0xFFFFFF) {
                        throw fail("Invalid dome RGB for theme " + theme.name() + ": 0x" + Integer.toHexString(domeRgb));
                    }
                }
            }
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testThemePreviewGallery(GameTestHelper context) {
        resetGlobalState();

        // 1. Test procedural dome mesh creation used by the theme preview gallery
        PredictionRenderData domeMesh = TrajectoryPredictor.createRenderData(1.3f);
        if (domeMesh == null || domeMesh.domeQuads().isEmpty()) {
            throw fail("TrajectoryPredictor.createRenderData(1.3f) returned empty or null dome quads");
        }

        if (domeMesh.domeQuads().size() < 100) {
            throw fail("Expected at least 100 dome quads, found: " + domeMesh.domeQuads().size());
        }

        // Verify each dome quad has 4 valid vertices and alpha bounds
        for (PredictionRenderData.DomeQuad quad : domeMesh.domeQuads()) {
            if (quad.p1() == null || quad.p2() == null || quad.p3() == null || quad.p4() == null) {
                throw fail("Found null vertex in preview gallery dome mesh");
            }
            if (quad.alpha1() < 0 || quad.alpha1() > 255 || quad.alpha2() < 0 || quad.alpha2() > 255) {
                throw fail("Invalid alpha bounds in preview gallery dome mesh: " + quad.alpha1() + ", " + quad.alpha2());
            }
        }

        // 2. Test mathematical circular gallery track distribution and chord spacing
        int count = VisualTheme.values().length;
        double spacing = 6.5;
        double minRadius = 12.0;
        double dynamicRadius = spacing / (2.0 * Math.sin(Math.PI / count));
        double radius = Math.max(minRadius, dynamicRadius);

        if (radius < 12.0) {
            throw fail("Gallery radius must be at least 12.0 blocks, got: " + radius);
        }

        // Verify adjacent track points satisfy chord distance >= 6.49
        double angle0 = 0.0;
        double angle1 = (2.0 * Math.PI) / count;
        Vec3 pos0 = new Vec3(-Math.sin(angle0) * radius, 0.0, Math.cos(angle0) * radius);
        Vec3 pos1 = new Vec3(-Math.sin(angle1) * radius, 0.0, Math.cos(angle1) * radius);
        double chordDist = pos0.distanceTo(pos1);
        if (chordDist < 6.49) {
            throw fail("Adjacent gallery tracks chord distance " + chordDist + " is less than minimum 6.5 blocks");
        }

        // 3. Test ModConfig theme state modification and persistence
        ModConfig.instance().visualTheme = VisualTheme.INFERNO;
        if (ModConfig.instance().visualTheme != VisualTheme.INFERNO) {
            throw fail("Failed to set config visualTheme to INFERNO");
        }

        ModConfig.instance().visualTheme = VisualTheme.MATRIX;
        if (ModConfig.instance().visualTheme != VisualTheme.MATRIX) {
            throw fail("Failed to set config visualTheme to MATRIX");
        }

        ModConfig.instance().visualTheme = VisualTheme.DEFAULT;
        if (ModConfig.instance().visualTheme != VisualTheme.DEFAULT) {
            throw fail("Failed to reset config visualTheme to DEFAULT");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testVisualThemeConfigOptionDisabling(GameTestHelper context) {
        resetGlobalState();

        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) {
            context.succeed();
            return;
        }

        try {
            Class<?> guiClass = Class.forName("com.simonconrad.fireballpredictor.client.gui.ModConfigGui");
            java.lang.reflect.Method generateGuiMethod = guiClass.getMethod("generateGui");

            ModConfig.instance().visualTheme = VisualTheme.DEFAULT;
            dev.isxander.yacl3.api.YetAnotherConfigLib gui = (dev.isxander.yacl3.api.YetAnotherConfigLib) generateGuiMethod.invoke(null);

            dev.isxander.yacl3.api.Option<VisualTheme> themeOpt = null;
            java.util.Map<String, dev.isxander.yacl3.api.Option<?>> colorOpts = new java.util.HashMap<>();

            for (dev.isxander.yacl3.api.ConfigCategory cat : gui.categories()) {
                for (dev.isxander.yacl3.api.OptionGroup grp : cat.groups()) {
                    for (dev.isxander.yacl3.api.Option<?> opt : grp.options()) {
                        if (opt.name().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                            String key = tc.getKey();
                            if (key.endsWith("visualTheme")) {
                                //noinspection unchecked
                                themeOpt = (dev.isxander.yacl3.api.Option<VisualTheme>) opt;
                            } else if (key.endsWith("trajectoryColor") || key.endsWith("windChargeTrajectoryColor")
                                    || key.endsWith("shockwaveColor") || key.endsWith("windChargeShockwaveColor")) {
                                colorOpts.put(key, opt);
                            }
                        }
                    }
                }
            }

            if (themeOpt == null) {
                throw fail("Could not find visualTheme option in GUI");
            }
            if (colorOpts.size() != 4) {
                throw fail("Expected 4 theme-overridden color options, found: " + colorOpts.size());
            }

            // 1. In DEFAULT theme, all color options must be available (enabled)
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (!entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be available in DEFAULT theme");
                }
            }

            // 2. Switching to a non-default theme dynamically disables all 4 color options
            themeOpt.requestSet(VisualTheme.RAINBOW);
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be disabled in RAINBOW theme");
                }
            }

            themeOpt.requestSet(VisualTheme.CYBERPUNK);
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be disabled in CYBERPUNK theme");
                }
            }

            // 3. Switching back to DEFAULT theme re-enables all 4 color options
            themeOpt.requestSet(VisualTheme.DEFAULT);
            for (java.util.Map.Entry<String, dev.isxander.yacl3.api.Option<?>> entry : colorOpts.entrySet()) {
                if (!entry.getValue().available()) {
                    throw fail("Color option " + entry.getKey() + " should be re-enabled when returning to DEFAULT theme");
                }
            }

            // 4. GUI initialized with a non-default theme must have color options disabled initially
            ModConfig.instance().visualTheme = VisualTheme.MATRIX;
            dev.isxander.yacl3.api.YetAnotherConfigLib nonDefaultGui = (dev.isxander.yacl3.api.YetAnotherConfigLib) generateGuiMethod.invoke(null);
            for (dev.isxander.yacl3.api.ConfigCategory cat : nonDefaultGui.categories()) {
                for (dev.isxander.yacl3.api.OptionGroup grp : cat.groups()) {
                    for (dev.isxander.yacl3.api.Option<?> opt : grp.options()) {
                        if (opt.name().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
                            String key = tc.getKey();
                            if (key.endsWith("trajectoryColor") || key.endsWith("windChargeTrajectoryColor")
                                    || key.endsWith("shockwaveColor") || key.endsWith("windChargeShockwaveColor")) {
                                if (opt.available()) {
                                    throw fail("Color option " + key + " should be initially disabled when config has MATRIX theme");
                                }
                            }
                        }
                    }
                }
            }

            ModConfig.instance().visualTheme = VisualTheme.DEFAULT;
        } catch (GameTestAssertException gae) {
            throw gae;
        } catch (Exception e) {
            throw fail("Failed to test GUI: " + e.getMessage());
        }
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNegativePowerSentinelFallsThrough(GameTestHelper context) {
        resetGlobalState();

        // The server sends -1.0f for hurting projectiles without a statically known power
        // (e.g. wither skulls). A non-positive cached value must be treated as "no authoritative
        // power" so the prediction pipeline falls back to the inference chain instead of
        // propagating an invalid power (which would silently disable the shockwave dome,
        // block-destruction overlay and damage estimates for those projectiles).
        ClientPowerCache.POWER_CACHE.clear();
        ClientPowerCache.POWER_CACHE.put(1, -1.0f);
        if (ClientPowerLookup.cachedPower(1) != null) {
            throw fail("Negative cached power (-1.0f sentinel) must be treated as 'no value'");
        }

        ClientPowerCache.POWER_CACHE.put(2, 0.0f);
        if (ClientPowerLookup.cachedPower(2) != null) {
            throw fail("Zero cached power must be treated as 'no value'");
        }

        ClientPowerCache.POWER_CACHE.put(3, 2.5f);
        Float positive = ClientPowerLookup.cachedPower(3);
        if (positive == null || positive != 2.5f) {
            throw fail("Positive cached power must be returned verbatim, got: " + positive);
        }

        ClientPowerCache.POWER_CACHE.clear();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testThemeTimeAndColorPins(GameTestHelper context) {
        resetGlobalState();

        // Frozen animation speed must freeze the dome pulse at full alpha (not at the sine midpoint),
        // and the pulse must stay within its designed range for normal speeds.
        if (VisualTheme.computePulseFactor(0.0) != 1.0f) {
            throw fail("computePulseFactor(0.0) must return 1.0f, got: " + VisualTheme.computePulseFactor(0.0));
        }
        float pulse = VisualTheme.computePulseFactor(1.0);
        if (pulse < 0.6f || pulse > 1.0f) {
            throw fail("computePulseFactor(1.0) out of range: " + pulse);
        }

        // The DEFAULT theme must preserve the pre-theme trail core brightening (0.35 white mix)
        // so the default rendering stays pixel-identical to the pre-theme renderer.
        int fallback = 0xFF8000;
        int core = VisualTheme.DEFAULT.getRibbonColorPacked(0.5f, 1.0, 0, true, fallback);
        int expected = VisualTheme.lightenRgb(fallback, 0.35f);
        if (core != expected) {
            throw fail("DEFAULT core color changed: got 0x" + Integer.toHexString(core)
                    + ", expected 0x" + Integer.toHexString(expected));
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testVisualThemesPreviewRepresentation(GameTestHelper context) {
        resetGlobalState();

        VisualTheme[] themes = VisualTheme.values();
        if (themes.length != 16) {
            throw fail("Expected exactly 16 visual themes, got: " + themes.length);
        }

        int fallbackRgb = 0xFF8000;
        for (VisualTheme theme : themes) {
            if (theme.getKey() == null || theme.getKey().isEmpty()) {
                throw fail("Theme " + theme.name() + " has null/empty key");
            }
            if (theme.getDisplayName() == null) {
                throw fail("Theme " + theme.name() + " has null displayName");
            }

            // Test color evaluations at multiple time and spatial sample points
            for (double t = 0.0; t <= 10.0; t += 2.5) {
                for (float p = 0.0f; p <= 1.0f; p += 0.2f) {
                    int ribbonShroud = theme.getRibbonColorPacked(p, t, 0, false, fallbackRgb);
                    int ribbonCore = theme.getRibbonColorPacked(p, t, 0, true, fallbackRgb);
                    float ribbonAlphaMod = theme.getRibbonAlphaModulation(p, t, 0);

                    if (ribbonShroud < 0 || ribbonShroud > 0xFFFFFF) {
                        throw fail("Theme " + theme.name() + " invalid ribbon shroud: 0x" + Integer.toHexString(ribbonShroud));
                    }
                    if (ribbonCore < 0 || ribbonCore > 0xFFFFFF) {
                        throw fail("Theme " + theme.name() + " invalid ribbon core: 0x" + Integer.toHexString(ribbonCore));
                    }
                    if (ribbonAlphaMod < 0.0f || ribbonAlphaMod > 4.0f) {
                        throw fail("Theme " + theme.name() + " invalid ribbon alpha mod: " + ribbonAlphaMod);
                    }

                    int domePacked = theme.getDomeColorPacked(null, null, p, p, t, fallbackRgb);
                    float domeAlphaMod = theme.getDomeAlphaModulation(p, p, t);

                    if (domePacked < 0 || domePacked > 0xFFFFFF) {
                        throw fail("Theme " + theme.name() + " invalid dome color: 0x" + Integer.toHexString(domePacked));
                    }
                    if (domeAlphaMod < 0.0f || domeAlphaMod > 4.0f) {
                        throw fail("Theme " + theme.name() + " invalid dome alpha mod: " + domeAlphaMod);
                    }
                }
            }
        }

        context.succeed();
    }
}