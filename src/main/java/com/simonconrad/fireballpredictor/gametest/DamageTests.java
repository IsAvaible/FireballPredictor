package com.simonconrad.fireballpredictor.gametest;

import com.simonconrad.fireballpredictor.math.DamageCalculator;
import com.simonconrad.fireballpredictor.math.DamageCalculator.DamageEstimate;
import com.simonconrad.fireballpredictor.math.ImpactPredictor;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class DamageTests extends GameTestBase {

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
}
