package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.client.network.ExplosionInferenceHandler;
import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker;
import com.simonconrad.fireballpredictor.client.tracking.ClientOwnerCache;
import com.simonconrad.fireballpredictor.client.tracking.InferenceResult;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.math.DamageCalculator;
import com.simonconrad.fireballpredictor.math.DamageCalculator.DamageEstimate;
import com.simonconrad.fireballpredictor.math.ImpactPredictor;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import com.simonconrad.fireballpredictor.network.FireballOwnerPayload;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TrajectoryTests extends GameTestBase {

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
        BlockState waterloggedSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
        buildWall(context, waterloggedSlab);
        LargeFireball fireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        assertNoDestruction(context, fireball, Blocks.OAK_SLAB);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testChargedWitherSkullAgainstWaterloggedSlab(GameTestHelper context) {
        resetGlobalState();
        BlockState waterloggedSlab = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true);
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
        if (ImpactPredictor.resolveExplosionPower(fireball) != 0.0f) {
            throw fail("SmallFireball explosion power expected to be 0.0f, got: " + ImpactPredictor.resolveExplosionPower(fireball));
        }
        assertNoDestruction(context, fireball, Blocks.DIRT);
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testDragonFireballPowerAndNoDestruction(GameTestHelper context) {
        resetGlobalState();
        buildWall(context, Blocks.DIRT);
        DragonFireball fireball = spawnProjectile(context, EntityTypes.DRAGON_FIREBALL, 0.0, false);
        if (ImpactPredictor.resolveExplosionPower(fireball) != 0.0f) {
            throw fail("DragonFireball explosion power expected to be 0.0f, got: " + ImpactPredictor.resolveExplosionPower(fireball));
        }
        Player player = spawnMockPlayer(context, new Vec3(8.0, 3.0, 3.5));
        DamageEstimate estimate = DamageCalculator.calculateDirectHit(fireball.position(), 0.0F, player, context.getLevel(), fireball, null);
        if (estimate.finalDamage() != 0.0f) {
            throw fail("DragonFireball direct hit damage expected to be 0.0f, got: " + estimate.finalDamage());
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

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testNegativePowerSentinelFallsThrough(GameTestHelper context) {
        resetGlobalState();

        // The server sends -1.0f for hurting projectiles without a statically known power
        // (e.g. wither skulls). A non-positive cached value must be treated as "no authoritative
        // power" so the prediction pipeline falls back to the inference chain instead of
        // propagating an invalid power (which would silently disable the shockwave dome,
        // block-destruction overlay and damage estimates for those projectiles).
        ClientPowerCache.clear();
        ClientPowerCache.put(1, -1.0f);
        if (ClientPowerLookup.cachedPower(1) != null) {
            throw fail("Negative cached power (-1.0f sentinel) must be treated as 'no value'");
        }

        ClientPowerCache.put(2, 0.0f);
        if (ClientPowerLookup.cachedPower(2) != null) {
            throw fail("Zero cached power must be treated as 'no value'");
        }

        ClientPowerCache.put(3, 2.5f);
        Float positive = ClientPowerLookup.cachedPower(3);
        if (positive == null || positive != 2.5f) {
            throw fail("Positive cached power must be returned verbatim, got: " + positive);
        }

        ClientPowerCache.clear();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 50)
    public void testComputeTrajectoryDomeIntercept_Geometry(GameTestHelper context) {
        resetGlobalState();

        Vec3 hitPos = new Vec3(10.0, 5.0, 10.0);
        float radius = 2.6f;

        // 1. Vertical incoming path
        List<Vec3> verticalPath = List.of(
                new Vec3(10.0, 15.0, 10.0),
                new Vec3(10.0, 10.0, 10.0),
                new Vec3(10.0, 5.0, 10.0)
        );
        Vec3 vIntercept = TrajectoryPredictor.computeTrajectoryDomeIntercept(verticalPath, hitPos, radius);
        if (Math.abs(vIntercept.x) > 1e-4 || Math.abs(vIntercept.y - 1.0) > 1e-4 || Math.abs(vIntercept.z) > 1e-4) {
            throw fail("Expected vertical intercept (0, 1, 0), got: " + vIntercept);
        }

        // 2. Horizontal incoming path from negative X
        List<Vec3> horizPath = List.of(
                new Vec3(0.0, 5.0, 10.0),
                new Vec3(5.0, 5.0, 10.0),
                new Vec3(10.0, 5.0, 10.0)
        );
        Vec3 hIntercept = TrajectoryPredictor.computeTrajectoryDomeIntercept(horizPath, hitPos, radius);
        if (Math.abs(hIntercept.x - (-1.0)) > 1e-4 || Math.abs(hIntercept.y) > 1e-4 || Math.abs(hIntercept.z) > 1e-4) {
            throw fail("Expected horizontal intercept (-1, 0, 0), got: " + hIntercept);
        }

        // 3. Diagonal multi-segment path
        List<Vec3> diagPath = List.of(
                new Vec3(10.0 + 10.0, 5.0 + 10.0, 10.0 + 10.0),
                new Vec3(10.0 + 5.0, 5.0 + 5.0, 10.0 + 5.0),
                new Vec3(10.0 + 1.0, 5.0 + 1.0, 10.0 + 1.0),
                hitPos
        );
        Vec3 dIntercept = TrajectoryPredictor.computeTrajectoryDomeIntercept(diagPath, hitPos, radius);
        double len = dIntercept.length();
        if (Math.abs(len - 1.0) > 1e-4) {
            throw fail("Expected normalized intercept vector of length 1.0, got length " + len);
        }
        double expectedComponent = 1.0 / Math.sqrt(3.0);
        if (Math.abs(dIntercept.x - expectedComponent) > 1e-3 || Math.abs(dIntercept.y - expectedComponent) > 1e-3 || Math.abs(dIntercept.z - expectedComponent) > 1e-3) {
            throw fail("Expected diagonal intercept (" + expectedComponent + ", " + expectedComponent + ", " + expectedComponent + "), got: " + dIntercept);
        }

        // 4. Degenerate and null fallbacks
        Vec3 fallbackNull = TrajectoryPredictor.computeTrajectoryDomeIntercept(null, hitPos, radius);
        Vec3 fallbackEmpty = TrajectoryPredictor.computeTrajectoryDomeIntercept(List.of(), hitPos, radius);
        Vec3 fallbackSingle = TrajectoryPredictor.computeTrajectoryDomeIntercept(List.of(hitPos), hitPos, radius);
        Vec3 fallbackZeroR = TrajectoryPredictor.computeTrajectoryDomeIntercept(verticalPath, hitPos, 0.0f);

        if (Math.abs(fallbackNull.y - 1.0) > 1e-4 || Math.abs(fallbackEmpty.y - 1.0) > 1e-4 ||
            Math.abs(fallbackSingle.y - 1.0) > 1e-4 || Math.abs(fallbackZeroR.y - 1.0) > 1e-4) {
            throw fail("Degenerate inputs did not fallback to (0, 1, 0)");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testPerOwnerPowerInferenceIsolation(GameTestHelper context) {
        resetGlobalState();

        // 1. Simulate a custom power 3.0 Bedwars fireball launched by a PLAYER
        Vec3 playerHitPos = new Vec3(10.0, 64.0, 10.0);
        LargeFireball playerFireball1 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ClientOwnerCache.put(playerFireball1.getId(), InferenceResult.of(ProjectileOwner.PLAYER, null, InferenceResult.InferenceSource.SERVER_PACKET));
        FireballInferenceTracker.registerFireballLocation(playerFireball1, playerHitPos, ProjectileOwner.PLAYER);

        ExplosionInferenceHandler.onExplosion(playerHitPos, 3.0f);
        playerFireball1.discard();

        // Verify PLAYER power inferred to 3.0f
        LargeFireball playerFireball2 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ClientOwnerCache.put(playerFireball2.getId(), InferenceResult.of(ProjectileOwner.PLAYER, null, InferenceResult.InferenceSource.SERVER_PACKET));
        float resolvedPlayerPower = ClientPowerLookup.getPower(playerFireball2);
        if (Math.abs(resolvedPlayerPower - 3.0f) > 0.01f) {
            throw fail("Expected PLAYER fireball to resolve to inferred 3.0f, but got: " + resolvedPlayerPower);
        }

        // 2. Spawn a GHAST fireball — must NOT be poisoned by the PLAYER power 3.0 blast!
        LargeFireball ghastFireball = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ClientOwnerCache.put(ghastFireball.getId(), InferenceResult.of(ProjectileOwner.GHAST, null, InferenceResult.InferenceSource.SERVER_PACKET));
        float resolvedGhastPower = ClientPowerLookup.getPower(ghastFireball);
        if (Math.abs(resolvedGhastPower - 1.0f) > 0.01f) {
            throw fail("Ghast fireball was incorrectly poisoned by player power! Expected 1.0f, but got: " + resolvedGhastPower);
        }

        playerFireball2.discard();
        ghastFireball.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testPerOwnerPowerInferenceUpgrade(GameTestHelper context) {
        resetGlobalState();

        // 1. Infer GHAST fireball at custom power 2.0
        Vec3 ghastHitPos = new Vec3(5.0, 64.0, 5.0);
        LargeFireball ghast1 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        FireballInferenceTracker.registerFireballLocation(ghast1, ghastHitPos, ProjectileOwner.GHAST);
        ExplosionInferenceHandler.onExplosion(ghastHitPos, 2.0f);
        ghast1.discard();

        // 2. Infer PLAYER fireball at custom power 3.5
        Vec3 playerHitPos = new Vec3(15.0, 64.0, 15.0);
        LargeFireball player1 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        FireballInferenceTracker.registerFireballLocation(player1, playerHitPos, ProjectileOwner.PLAYER);
        ExplosionInferenceHandler.onExplosion(playerHitPos, 3.5f);
        player1.discard();

        // 3. Verify independent inferences
        LargeFireball ghast2 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ClientOwnerCache.put(ghast2.getId(), InferenceResult.of(ProjectileOwner.GHAST, null, InferenceResult.InferenceSource.SERVER_PACKET));
        float ghastPower = ClientPowerLookup.getPower(ghast2);
        if (Math.abs(ghastPower - 2.0f) > 0.01f) {
            throw fail("Expected GHAST power 2.0f, got: " + ghastPower);
        }

        LargeFireball player2 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ClientOwnerCache.put(player2.getId(), InferenceResult.of(ProjectileOwner.PLAYER, null, InferenceResult.InferenceSource.SERVER_PACKET));
        float playerPower = ClientPowerLookup.getPower(player2);
        if (Math.abs(playerPower - 3.5f) > 0.01f) {
            throw fail("Expected PLAYER power 3.5f, got: " + playerPower);
        }

        ghast2.discard();
        player2.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testInferenceTtlExpiration(GameTestHelper context) {
        resetGlobalState();

        long pastTime = System.currentTimeMillis() - 100_000L; // 100s ago (exceeds 90s TTL)
        ClientPowerLookup.InferredPowerEntry expiredEntry = new ClientPowerLookup.InferredPowerEntry(4.0f, pastTime, true);
        if (!expiredEntry.isExpired(ClientPowerLookup.DEFAULT_INFERENCE_TTL_MS)) {
            throw fail("Expected entry from 100s ago to be expired under 90s TTL");
        }

        ClientPowerLookup.InferredPowerEntry freshEntry = new ClientPowerLookup.InferredPowerEntry(4.0f, System.currentTimeMillis(), true);
        if (freshEntry.isExpired(ClientPowerLookup.DEFAULT_INFERENCE_TTL_MS)) {
            throw fail("Expected fresh entry to not be expired under 90s TTL");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testInferenceTtlRefreshOnNewShot(GameTestHelper context) {
        resetGlobalState();

        // 1. Initial PLAYER explosion establishes 3.0 power
        Vec3 hitPos1 = new Vec3(10.0, 64.0, 10.0);
        LargeFireball fb1 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        FireballInferenceTracker.registerFireballLocation(fb1, hitPos1, ProjectileOwner.PLAYER);
        ExplosionInferenceHandler.onExplosion(hitPos1, 3.0f);
        fb1.discard();

        ClientPowerLookup.InferredPowerEntry initial = ClientPowerLookup.getOwnerInference(ProjectileOwner.PLAYER);
        if (initial == null || Math.abs(initial.power() - 3.0f) > 0.01f) {
            throw fail("Failed to initialize player power inference");
        }
        long initialTimestamp = initial.timestamp();

        // 2. Registering a new shot of the same owner type touches and refreshes the timestamp
        LargeFireball fb2 = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        FireballInferenceTracker.registerFireballLocation(fb2, new Vec3(12.0, 64.0, 12.0), ProjectileOwner.PLAYER);

        ClientPowerLookup.InferredPowerEntry refreshed = ClientPowerLookup.getOwnerInference(ProjectileOwner.PLAYER);
        if (refreshed == null || refreshed.timestamp() < initialTimestamp) {
            throw fail("Expected owner inference timestamp to be refreshed upon new shot registration");
        }
        if (Math.abs(refreshed.power() - 3.0f) > 0.01f) {
            throw fail("Power value was altered during TTL touch refresh");
        }

        fb2.discard();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testFireballOwnerPayloadStableEnumSerialization(GameTestHelper context) {
        resetGlobalState();

        // 1. Valid names
        if (ProjectileOwner.fromName("GHAST") != ProjectileOwner.GHAST) {
            throw fail("Expected fromName('GHAST') == GHAST");
        }
        if (ProjectileOwner.fromName("PLAYER") != ProjectileOwner.PLAYER) {
            throw fail("Expected fromName('PLAYER') == PLAYER");
        }
        if (ProjectileOwner.fromName("DISPENSER") != ProjectileOwner.DISPENSER) {
            throw fail("Expected fromName('DISPENSER') == DISPENSER");
        }
        if (ProjectileOwner.fromName("BLAZE") != ProjectileOwner.BLAZE) {
            throw fail("Expected fromName('BLAZE') == BLAZE");
        }

        // 2. Unrecognized or null names safely fallback to UNKNOWN
        if (ProjectileOwner.fromName("NON_EXISTENT_ENUM_VALUE") != ProjectileOwner.UNKNOWN) {
            throw fail("Expected unrecognized name to fallback to UNKNOWN");
        }
        if (ProjectileOwner.fromName(null) != ProjectileOwner.UNKNOWN) {
            throw fail("Expected null name to fallback to UNKNOWN");
        }
        if (ProjectileOwner.fromName("") != ProjectileOwner.UNKNOWN) {
            throw fail("Expected empty name to fallback to UNKNOWN");
        }

        // 3. Payload record getters
        FireballOwnerPayload payload = new FireballOwnerPayload(123, "PLAYER", 456);
        if (payload.entityId() != 123 || !"PLAYER".equals(payload.ownerName()) || payload.ownerEntityId() != 456) {
            throw fail("FireballOwnerPayload record fields corrupted");
        }

        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testClientPowerCacheEncapsulation(GameTestHelper context) {
        resetGlobalState();

        ClientPowerCache.clear();
        if (ClientPowerCache.containsKey(999)) {
            throw fail("Expected containsKey(999) to be false after clear");
        }

        ClientPowerCache.put(999, 2.5f);
        if (!ClientPowerCache.containsKey(999)) {
            throw fail("Expected containsKey(999) to be true after put");
        }
        if (ClientPowerCache.get(999) == null || ClientPowerCache.get(999) != 2.5f) {
            throw fail("Expected get(999) to be 2.5f");
        }

        ClientPowerCache.remove(999);
        if (ClientPowerCache.get(999) != null) {
            throw fail("Expected get(999) to be null after remove");
        }

        ClientPowerCache.clear();
        context.succeed();
    }

    @GameTest(structure = "fabric-gametest-api-v1:empty", maxTicks = 20)
    public void testDispenserPowerInferenceIsolation(GameTestHelper context) {
        resetGlobalState();

        // 1. Simulate an explosion with power 3.5 from a PLAYER
        Vec3 playerHitPos = new Vec3(20.0, 64.0, 20.0);
        LargeFireball playerFb = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        FireballInferenceTracker.registerFireballLocation(playerFb, playerHitPos, ProjectileOwner.PLAYER);
        ExplosionInferenceHandler.onExplosion(playerHitPos, 3.5f);
        playerFb.discard();

        // Player inference should be 3.5
        LargeFireball playerCheck = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ClientOwnerCache.put(playerCheck.getId(), InferenceResult.of(ProjectileOwner.PLAYER, null, InferenceResult.InferenceSource.SERVER_PACKET));
        float playerPower = ClientPowerLookup.getPower(playerCheck);
        if (Math.abs(playerPower - 3.5f) > 0.01f) {
            throw fail("Expected player fireball to resolve inferred power 3.5f, got " + playerPower);
        }
        playerCheck.discard();

        // 2. Dispenser fireball with no custom inference must resolve to vanilla default 1.0f (not poisoned by player's 3.5f)
        LargeFireball dispenserFb = spawnProjectile(context, EntityTypes.FIREBALL, 0.1, false);
        ClientOwnerCache.put(dispenserFb.getId(), InferenceResult.of(ProjectileOwner.DISPENSER, null, InferenceResult.InferenceSource.SERVER_PACKET));
        float dispenserPower = ClientPowerLookup.getPower(dispenserFb);
        if (Math.abs(dispenserPower - 1.0f) > 0.01f) {
            throw fail("Expected dispenser fireball to resolve default power 1.0f without cross-pollution, got " + dispenserPower);
        }
        dispenserFb.discard();

        context.succeed();
    }
}
