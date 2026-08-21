package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.FireballEntityAccessor;
import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.client.network.ExplosionInferenceHandler;
import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.math.DamageCalculator;
import com.simonconrad.fireballpredictor.math.DamageCalculator.DamageEstimate;
import com.simonconrad.fireballpredictor.math.ImpactPredictor;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
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
}
