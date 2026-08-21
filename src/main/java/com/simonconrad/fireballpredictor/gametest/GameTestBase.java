package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.client.network.ClientPowerCache;
import com.simonconrad.fireballpredictor.client.network.ClientPowerLookup;
import com.simonconrad.fireballpredictor.client.network.FireballInferenceTracker;
import com.simonconrad.fireballpredictor.client.tracking.ClientOwnerCache;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRules;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class GameTestBase {

    // Constants for wall structure and projectile spawning
    protected static final int WALL_X = 2;
    protected static final int WALL_MIN_Y = 1;
    protected static final int WALL_MAX_Y = 5;
    protected static final int WALL_MIN_Z = 1;
    protected static final int WALL_MAX_Z = 5;

    protected static final Vec3 SPAWN_POS = new Vec3(1.5, 3.0, 3.5);
    protected static final Vec3 INITIAL_VELOCITY = new Vec3(0.5, 0.0, 0.0);

    /**
     * Helper to construct a framework-native GameTestAssertException with a Component message.
     */
    protected static GameTestAssertException fail(String message) {
        return new GameTestAssertException(Component.literal(message), 0);
    }

    /**
     * Centralized reset method to be called before stateful tests
     * to guarantee a clean environment and prevent test leakage.
     */
    public static void resetGlobalState() {
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

    protected void buildWall(GameTestHelper context, BlockState state) {
        for (int y = WALL_MIN_Y; y <= WALL_MAX_Y; y++) {
            for (int z = WALL_MIN_Z; z <= WALL_MAX_Z; z++) {
                context.setBlock(new BlockPos(WALL_X, y, z), state);
            }
        }
    }

    protected void buildWall(GameTestHelper context, Block block) {
        buildWall(context, block.defaultBlockState());
    }

    @SuppressWarnings("unchecked")
    protected <T extends AbstractHurtingProjectile> T spawnProjectile(
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

    protected List<BlockPos> getBrokenBlocks(GameTestHelper context, Block originalBlock) {
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

    protected List<BlockPos> getPredictedBrokenBlocks(AbstractHurtingProjectile projectile, GameTestHelper context) {
        TrajectoryPredictor.TrajectoryResult trajResult = TrajectoryPredictor.simulateTrajectory(projectile, context.getLevel());
        PredictionData prediction = TrajectoryPredictor.computePrediction(trajResult, projectile.tickCount);
        return prediction.brokenBlocks();
    }

    protected void assertExplosionDestruction(
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

    protected void assertNoDestruction(
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

    /**
     * Spawns a survival mock player at the given relative position with full HP and no velocity.
     */
    protected Player spawnMockPlayer(GameTestHelper context, Vec3 relativePos) {
        Player player = context.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(context.absoluteVec(relativePos));
        player.setHealth(20.0F);
        player.setAbsorptionAmount(0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        context.getLevel().addFreshEntity(player);
        return player;
    }

    protected ItemStack enchantedStack(ItemStack stack, Holder<Enchantment> enchantment) {
        stack.enchant(enchantment, 4);
        return stack;
    }
}
