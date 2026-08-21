package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.client.tracking.InferenceResult;
import com.simonconrad.fireballpredictor.client.tracking.OwnerInferenceEngine;
import com.simonconrad.fireballpredictor.client.tracking.ServerTrackingRules;
import com.simonconrad.fireballpredictor.client.tracking.TrackedProjectile;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.ServerConfig;
import com.simonconrad.fireballpredictor.tracking.OwnerClassifier;
import com.simonconrad.fireballpredictor.tracking.ProjectileOwner;
import com.simonconrad.fireballpredictor.tracking.TrackingRules;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class OwnerTests extends GameTestBase {

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
}
