package com.simonconrad.fireballpredictor.math;

import com.simonconrad.fireballpredictor.mixin.CompositeLootItemConditionAccessor;
import java.util.List;
import java.util.Optional;

import net.minecraft.advancements.predicates.TagPredicate;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.AnyOfCondition;
import net.minecraft.world.level.storage.loot.predicates.DamageSourceCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Client-safe replica of Minecraft 26.2's explosion damage &amp; knockback pipeline
 * ({@code ServerExplosion.hurtEntities}, {@code ExplosionDamageCalculator.getEntityDamageAmount},
 * {@code LivingEntity.actuallyHurt} and the enchantment {@code damage_protection} effect pipeline).
 *
 * <p>The class deliberately contains <b>no client-only classes</b> (no {@code LocalPlayer},
 * no GUI types) so it can be exercised headlessly by the GameTest suite. All state comes from
 * the {@code Player}/{@code Level} arguments, and all heavy raycasting is deterministic.
 *
 * <p>Threading note: {@link #getSeenPercent(Level, Vec3, Entity)} calls {@code level.clip(...)},
 * which is <b>not</b> thread-safe and must only run on the main thread.
 */
public final class DamageCalculator {

    /** Vanilla {@code LargeFireball.onHitEntity} direct-hit damage (26.2: 6.0 via "minecraft:fireball"). */
    public static final float DIRECT_HIT_DAMAGE = 6.0F;
    public static final float LARGE_FIREBALL_DIRECT_HIT_DAMAGE = 6.0F;
    public static final float SMALL_FIREBALL_DIRECT_HIT_DAMAGE = 5.0F;
    public static final float WITHER_SKULL_DIRECT_HIT_DAMAGE = 8.0F;

    /** Blast radius multiplier: r = power * 2 (vanilla {@code ServerExplosion.hurtEntities}). */
    public static final float BLAST_RADIUS_MULTIPLIER = 2.0F;

    /** Soft cap applied by {@code CombatRules.getDamageAfterMagicAbsorb}. */
    public static final float MAX_EPF = 20.0F;

    private DamageCalculator() {
    }

    /**
     * Prediction of the damage &amp; knockback a player would take from an explosion detonating at
     * {@code explosionPos} with the given power.
     *
     * @param explosionPos     predicted detonation point (the mod uses the trajectory raycast hit location)
     * @param explosionPower   explosion power (0 or below yields an out-of-range estimate, mirroring the
     *                         vanilla {@code radius <= 1.0E-5F} early-out)
     * @param player           the affected player
     * @param level            the level used for the line-of-sight raycast (must be the main thread)
     * @param explosionSource  the {@code DamageSource} the blast would use (e.g.
     *                         {@code level.damageSources().explosion(fireball, owner)})
     */
    public static DamageEstimate calculate(
            Vec3 explosionPos, float explosionPower, Player player, Level level, DamageSource explosionSource) {
        if (explosionPower <= 0.0F) {
            // Vanilla: ServerExplosion.hurtEntities() returns immediately when radius <= 1.0E-5F.
            // SmallFireballs / DragonFireballs resolve to power 0 and deal NO explosion damage.
            return DamageEstimate.NONE;
        }

        float radius = explosionPower * BLAST_RADIUS_MULTIPLIER;
        double distance = Math.sqrt(player.distanceToSqr(explosionPos));
        if (distance > radius) {
            return DamageEstimate.NONE;
        }

        float seenPercent = getSeenPercent(level, explosionPos, player);
        return calculateInternal(explosionPos, explosionPower, radius, distance, player, explosionSource, seenPercent);
    }

    /**
     * Prediction of damage &amp; knockback using an existing line-of-sight exposure factor.
     * Allows callers to avoid re-raycasting when entity and hit positions have not moved.
     */
    public static DamageEstimate calculateFromSeenPercent(
            Vec3 explosionPos, float explosionPower, Player player, DamageSource explosionSource, float seenPercent) {
        if (explosionPower <= 0.0F) {
            return DamageEstimate.NONE;
        }

        float radius = explosionPower * BLAST_RADIUS_MULTIPLIER;
        double distance = Math.sqrt(player.distanceToSqr(explosionPos));
        if (distance > radius) {
            return DamageEstimate.NONE;
        }

        return calculateInternal(explosionPos, explosionPower, radius, distance, player, explosionSource, seenPercent);
    }

    private static DamageEstimate calculateInternal(
            Vec3 explosionPos, float explosionPower, float radius, double distance, Player player, DamageSource explosionSource, float seenPercent) {
        float impact = (1.0F - (float) distance / radius) * seenPercent;
        // Vanilla ExplosionDamageCalculator.getEntityDamageAmount:
        float rawDamage = (impact * impact + impact) / 2.0F * 7.0F * radius + 1.0F;

        float finalDamage = computeFinalDamage(rawDamage, player, explosionSource);
        double knockback = computeKnockback(distance, radius, player, seenPercent, 1.0F);

        return new DamageEstimate(rawDamage, finalDamage,
                Math.min(finalDamage, player.getHealth() + player.getAbsorptionAmount()) / 2.0F,
                knockback * 20.0, seenPercent, true);
    }

    /**
     * Prediction for the direct-hit case (projectile collides with the player itself):
     * vanilla onHitEntity deals direct hit damage and the projectile additionally detonates
     * at the hit point.
     *
     * @param hitPos     predicted collision point (≈ player position)
     * @param projectile the hurting projectile (LargeFireball, SmallFireball, WitherSkull, etc.)
     * @param owner      the projectile owner (may be null)
     */
    public static DamageEstimate calculateDirectHit(
            Vec3 hitPos, float explosionPower, Player player, Level level, AbstractHurtingProjectile projectile, @Nullable Entity owner) {
        float seenPercent = getSeenPercent(level, hitPos, player);
        return calculateDirectHitFromSeenPercent(hitPos, explosionPower, player, level, projectile, owner, seenPercent);
    }

    /**
     * Direct-hit prediction using an existing line-of-sight exposure factor.
     */
    public static DamageEstimate calculateDirectHitFromSeenPercent(
            Vec3 hitPos, float explosionPower, Player player, Level level, AbstractHurtingProjectile projectile, @Nullable Entity owner, float seenPercent) {
        if (projectile instanceof net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge) {
            return DamageEstimate.NONE;
        }

        float directBaseDamage = 0.0F;
        DamageSource directSource = null;

        if (projectile instanceof LargeFireball largeFireball) {
            directBaseDamage = LARGE_FIREBALL_DIRECT_HIT_DAMAGE;
            directSource = level.damageSources().fireball(largeFireball, owner);
        } else if (projectile instanceof SmallFireball smallFireball) {
            directBaseDamage = SMALL_FIREBALL_DIRECT_HIT_DAMAGE;
            directSource = level.damageSources().fireball(smallFireball, owner);
        } else if (projectile instanceof WitherSkull witherSkull) {
            directBaseDamage = WITHER_SKULL_DIRECT_HIT_DAMAGE;
            directSource = level.damageSources().witherSkull(witherSkull, owner);
        } else if (projectile instanceof Fireball fireball) {
            directBaseDamage = DIRECT_HIT_DAMAGE;
            directSource = level.damageSources().fireball(fireball, owner);
        }

        float directFinal = directSource != null ? computeFinalDamage(directBaseDamage, player, directSource) : 0.0F;

        DamageSource blastSource = level.damageSources().explosion(projectile, owner);
        DamageEstimate blast = calculateFromSeenPercent(hitPos, explosionPower, player, blastSource, seenPercent);

        float raw = Math.max(directBaseDamage, blast.inRange() ? blast.rawDamage() : 0.0F);
        float total = Math.max(directFinal, blast.inRange() ? blast.finalDamage() : 0.0F);
        float hearts = Math.min(total, player.getHealth() + player.getAbsorptionAmount()) / 2.0F;
        double knockback = blast.inRange() ? blast.knockbackBlocksPerSecond() : 0.0;
        float seen = blast.inRange() ? blast.seenPercent() : seenPercent;

        return new DamageEstimate(raw, total, hearts, knockback, seen, true);
    }

    /**
     * Applies the full vanilla 26.2 mitigation pipeline to raw damage:
     * armor ({@code CombatRules.getDamageAfterAbsorb}) → Resistance effect → enchantment
     * damage protection ({@code CombatRules.getDamageAfterMagicAbsorb}), honouring the
     * {@code BYPASSES_ARMOR} / {@code BYPASSES_EFFECTS} / {@code BYPASSES_RESISTANCE} /
     * {@code BYPASSES_ENCHANTMENTS} damage-type tags.
     */
    public static float computeFinalDamage(float rawDamage, Player player, DamageSource source) {
        if (player.getAbilities().invulnerable || player.isSpectator()) {
            return 0.0F;
        }

        float damage = rawDamage;

        // Difficulty scaling (vanilla: Player.hurtServer)
        if (source.scalesWithDifficulty() && player.level() != null) {
            net.minecraft.world.Difficulty difficulty = player.level().getDifficulty();
            if (difficulty == net.minecraft.world.Difficulty.PEACEFUL) {
                return 0.0F;
            } else if (difficulty == net.minecraft.world.Difficulty.EASY) {
                damage = Math.min(damage / 2.0F + 1.0F, damage);
            } else if (difficulty == net.minecraft.world.Difficulty.HARD) {
                damage = damage * 3.0F / 2.0F;
            }
        }

        // 1. Armor (vanilla: LivingEntity.getDamageAfterArmorAbsorb)
        if (!source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            damage = CombatRules.getDamageAfterAbsorb(player, damage, source,
                    player.getArmorValue(),
                    (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        }

        // 2. Resistance effect (vanilla: first half of LivingEntity.getDamageAfterMagicAbsorb)
        if (!source.is(DamageTypeTags.BYPASSES_EFFECTS)
                && player.hasEffect(MobEffects.RESISTANCE)
                && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            int reduction = 25 - (player.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5;
            damage = Math.max(damage * reduction / 25.0F, 0.0F);
        }

        if (damage <= 0.0F) {
            return 0.0F;
        }

        // 3. Enchantments (vanilla: second half of getDamageAfterMagicAbsorb).
        //    EnchantmentHelper.getDamageProtection returns 0 on the client (it requires a
        //    ServerLevel), so this is a faithful client-side reimplementation.
        if (!source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            float epf = getEnchantmentProtection(player, source);
            if (epf > 0.0F) {
                damage = CombatRules.getDamageAfterMagicAbsorb(damage, epf);
            }
        }

        return Math.max(damage, 0.0F);
    }

    /**
     * Client-side EPF evaluation equivalent to vanilla
     * {@code EnchantmentHelper.getDamageProtection(ServerLevel, LivingEntity, DamageSource)}.
     *
     * <p>Iterates the player's armor slots, reads every enchantment's data-driven
     * {@code minecraft:damage_protection} effect ({@link EnchantmentEffectComponents#DAMAGE_PROTECTION}),
     * matches the effect's loot-condition requirements against the damage source and sums the
     * resulting value-effect EPF (vanilla data: Protection +1/level, Blast Protection +2/level).
     * The sum is later soft-capped at {@link #MAX_EPF} by {@code CombatRules.getDamageAfterMagicAbsorb}.
     *
     * <p>Condition evaluation covers the condition shapes used by the base game and most packs
     * ({@link DamageSourceCondition} with tag predicates, {@link AllOfCondition},
     * {@link AnyOfCondition}, {@link InvertedLootItemCondition}); unknown condition types are
     * conservatively treated as <b>not</b> matching, so protection is never overstated.
     */
    public static float getEnchantmentProtection(Player player, DamageSource source) {
        float epf = 0.0F;
        // Mirrors vanilla EnchantmentHelper.runIterationOnEquipment: all six EquipmentSlot.VALUES.
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = player.getItemBySlot(slot);
            ItemEnchantments enchantments = stack.getEnchantments();
            if (enchantments.isEmpty()) {
                continue;
            }
            for (Holder<Enchantment> holder : enchantments.keySet()) {
                int level = enchantments.getLevel(holder);
                List<ConditionalEffect<EnchantmentValueEffect>> effects =
                        holder.value().getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION);
                if (effects.isEmpty()) {
                    continue;
                }
                for (ConditionalEffect<EnchantmentValueEffect> conditional : effects) {
                    if (matchesCondition(conditional.requirements(), source)) {
                        epf += conditional.effect().process(level, player.getRandom(), 0.0F);
                    }
                }
            }
        }
        return epf;
    }

    /**
     * Exact replica of vanilla {@code ServerExplosion.getSeenPercent(Vec3, Entity)}: raycasts a
     * deterministic grid over the entity's bounding box toward the explosion center and returns the
     * fraction of rays that reach it unobstructed. Deterministic (no RNG), so it is directly
     * assertable in GameTests. Must run on the main thread.
     */
    public static float getSeenPercent(Level level, Vec3 center, Entity entity) {
        return net.minecraft.world.level.ServerExplosion.getSeenPercent(center, entity);
    }

    /**
     * Vanilla 26.2 knockback impulse magnitude (blocks/tick of the {@code entity.push(...)} delta):
     * {@code (1 - d/r) * seenPercent * knockbackMultiplier * (1 - EXPLOSION_KNOCKBACK_RESISTANCE)}.
     * The direction in vanilla is the normalized {@code (entity.getEyePosition() - center)} vector
     * (away from the blast); this method returns the scalar magnitude only.
     */
    public static double computeKnockback(
            Vec3 explosionPos, float explosionPower, Player player, float seenPercent, float knockbackMultiplier) {
        float radius = explosionPower * BLAST_RADIUS_MULTIPLIER;
        double distance = Math.sqrt(player.distanceToSqr(explosionPos));
        return computeKnockback(distance, radius, player, seenPercent, knockbackMultiplier);
    }

    public static double computeKnockback(
            double distance, float radius, Player player, float seenPercent, float knockbackMultiplier) {
        double knockbackResistance = player.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
        return (1.0 - distance / radius) * seenPercent * knockbackMultiplier * (1.0 - knockbackResistance);
    }

    private static boolean matchesCondition(Optional<LootItemCondition> requirements, DamageSource source) {
        return requirements.isEmpty() || matchesCondition(requirements.get(), source);
    }

    private static boolean matchesCondition(LootItemCondition condition, DamageSource source) {
        if (condition instanceof DamageSourceCondition damageSourceCondition) {
            // The base-game condition shape: a DamageSourcePredicate with tag predicates.
            Optional<net.minecraft.advancements.predicates.DamageSourcePredicate> predicate =
                    damageSourceCondition.predicate();
            if (predicate.isEmpty()) {
                return true;
            }
            for (TagPredicate<DamageType> tagPredicate : predicate.get().tags()) {
                if (!tagPredicate.matches(source.typeHolder())) {
                    return false;
                }
            }
            // Entity predicates / isDirect are not evaluated client-side; tag checks above are the
            // decisive part for every vanilla damage-protection enchantment.
            return true;
        }
        if (condition instanceof InvertedLootItemCondition inverted) {
            return !matchesCondition(inverted.term(), source);
        }
        if (condition instanceof AllOfCondition allOf) {
            for (LootItemCondition term : ((CompositeLootItemConditionAccessor) allOf).getTerms()) {
                if (!matchesCondition(term, source)) {
                    return false;
                }
            }
            return true;
        }
        if (condition instanceof AnyOfCondition anyOf) {
            for (LootItemCondition term : ((CompositeLootItemConditionAccessor) anyOf).getTerms()) {
                if (matchesCondition(term, source)) {
                    return true;
                }
            }
            return false;
        }
        // Unknown condition shape: assume it does not match so we never overstate protection.
        return false;
    }

    /**
     * Immutable result of a damage &amp; knockback prediction.
     *
     * @param rawDamage                pre-mitigation explosion damage (vanilla getEntityDamageAmount)
     * @param finalDamage              damage after armor, enchantments and Resistance
     * @param heartsLost               predicted hearts (finalDamage/2), clamped to health + absorption
     * @param knockbackBlocksPerSecond predicted initial knockback speed (impulse magnitude * 20)
     * @param seenPercent              line-of-sight exposure 0.0..1.0
     * @param inRange                  true when the player is within the blast radius and power &gt; 0
     */
    public record DamageEstimate(
            float rawDamage,
            float finalDamage,
            float heartsLost,
            double knockbackBlocksPerSecond,
            float seenPercent,
            boolean inRange) {

        /** Singleton for "no meaningful threat" (out of range, zero power, etc.). */
        public static final DamageEstimate NONE = new DamageEstimate(0.0F, 0.0F, 0.0F, 0.0, 0.0F, false);
    }
}
