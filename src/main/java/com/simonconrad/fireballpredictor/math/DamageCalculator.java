package com.simonconrad.fireballpredictor.math;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.enchantment.EnchantmentHelper;
import com.simonconrad.fireballpredictor.mixin.CompositeLootItemConditionAccessor;
import java.util.List;
import java.util.Optional;

import net.minecraft.predicate.TagPredicate;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.effect.EnchantmentEffectEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.world.World;
import net.minecraft.loot.condition.AllOfLootCondition;
import net.minecraft.loot.condition.AnyOfLootCondition;
import net.minecraft.loot.condition.DamageSourcePropertiesLootCondition;
import net.minecraft.loot.condition.InvertedLootCondition;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import org.jetbrains.annotations.Nullable;

/**
 * Client-safe replica of MinecraftClient 26.2's explosion damage &amp; knockback pipeline
 * ({@code ServerExplosion.hurtEntities}, {@code ExplosionBehavior.getEntityDamageAmount},
 * {@code LivingEntity.actuallyHurt} and the enchantment {@code damage_protection} effect pipeline).
 *
 * <p>The class deliberately contains <b>no client-only classes</b> (no {@code ClientPlayerEntity},
 * no GUI types) so it can be exercised headlessly by the GameTest suite. All state comes from
 * the {@code PlayerEntity}/{@code World} arguments, and all heavy raycasting is deterministic.
 *
 * <p>Threading note: {@link #getExposure(World, Vec3d, Entity)} calls {@code level.raycast(...)},
 * which is <b>not</b> thread-safe and must only run on the main thread.
 */
public final class DamageCalculator {

    /** Vanilla {@code FireballEntity.onHitEntity} direct-hit damage (26.2: 6.0 via "minecraft:fireball"). */
    public static final float DIRECT_HIT_DAMAGE = 6.0F;
    public static final float LARGE_FIREBALL_DIRECT_HIT_DAMAGE = 6.0F;
    public static final float SMALL_FIREBALL_DIRECT_HIT_DAMAGE = 5.0F;
    public static final float WITHER_SKULL_DIRECT_HIT_DAMAGE = 8.0F;

    /** Blast radius multiplier: r = power * 2 (vanilla {@code ServerExplosion.hurtEntities}). */
    public static final float BLAST_RADIUS_MULTIPLIER = 2.0F;

    /** Soft cap applied by {@code DamageUtil.getDamageAfterMagicAbsorb}. */
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
     *                         {@code level.getDamageSources().explosion(fireball, owner)})
     */
    public static DamageEstimate calculate(
            Vec3d explosionPos, float explosionPower, PlayerEntity player, World level, DamageSource explosionSource) {
        if (explosionPower <= 0.0F) {
            // Vanilla: ServerExplosion.hurtEntities() returns immediately when radius <= 1.0E-5F.
            // SmallFireballs / DragonFireballs resolve to power 0 and deal NO explosion damage.
            return DamageEstimate.NONE;
        }

        float radius = explosionPower * BLAST_RADIUS_MULTIPLIER;
        double distance = Math.sqrt(player.squaredDistanceTo(explosionPos));
        if (distance > radius) {
            return DamageEstimate.NONE;
        }

        float seenPercent = getExposure(level, explosionPos, player);
        return calculateFromSeenPercent(explosionPos, explosionPower, player, explosionSource, seenPercent);
    }

    /**
     * Prediction of damage &amp; knockback using an existing line-of-sight exposure factor.
     * Allows callers to avoid re-raycasting when entity and hit positions have not moved.
     */
    public static DamageEstimate calculateFromSeenPercent(
            Vec3d explosionPos, float explosionPower, PlayerEntity player, DamageSource explosionSource, float seenPercent) {
        if (explosionPower <= 0.0F) {
            return DamageEstimate.NONE;
        }

        float radius = explosionPower * BLAST_RADIUS_MULTIPLIER;
        double distance = Math.sqrt(player.squaredDistanceTo(explosionPos));
        if (distance > radius) {
            return DamageEstimate.NONE;
        }

        float impact = (1.0F - (float) distance / radius) * seenPercent;
        // Vanilla ExplosionBehavior.getEntityDamageAmount:
        float rawDamage = (impact * impact + impact) / 2.0F * 7.0F * radius + 1.0F;

        float finalDamage = computeFinalDamage(rawDamage, player, explosionSource);
        double knockback = computeKnockback(explosionPos, explosionPower, player, seenPercent, 1.0F);

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
     * @param projectile the hurting projectile (FireballEntity, SmallFireballEntity, WitherSkullEntity, etc.)
     * @param owner      the projectile owner (may be null)
     */
    public static DamageEstimate calculateDirectHit(
            Vec3d hitPos, float explosionPower, PlayerEntity player, World level, ExplosiveProjectileEntity projectile, @Nullable Entity owner) {
        float seenPercent = getExposure(level, hitPos, player);
        return calculateDirectHitFromSeenPercent(hitPos, explosionPower, player, level, projectile, owner, seenPercent);
    }

    /**
     * Direct-hit prediction using an existing line-of-sight exposure factor.
     */
    public static DamageEstimate calculateDirectHitFromSeenPercent(
            Vec3d hitPos, float explosionPower, PlayerEntity player, World level, ExplosiveProjectileEntity projectile, @Nullable Entity owner, float seenPercent) {
        if (projectile instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity) {
            return DamageEstimate.NONE;
        }

        float directBaseDamage = 0.0F;
        DamageSource directSource = null;

        if (projectile instanceof FireballEntity largeFireball) {
            directBaseDamage = LARGE_FIREBALL_DIRECT_HIT_DAMAGE;
            directSource = level.getDamageSources().fireball(largeFireball, owner);
        } else if (projectile instanceof SmallFireballEntity smallFireball) {
            directBaseDamage = SMALL_FIREBALL_DIRECT_HIT_DAMAGE;
            directSource = level.getDamageSources().fireball(smallFireball, owner);
        } else if (projectile instanceof WitherSkullEntity witherSkull) {
            directBaseDamage = WITHER_SKULL_DIRECT_HIT_DAMAGE;
            directSource = level.getDamageSources().witherSkull(witherSkull, owner);
        } else if (projectile instanceof AbstractFireballEntity fireball) {
            directBaseDamage = DIRECT_HIT_DAMAGE;
            directSource = level.getDamageSources().fireball(fireball, owner);
        }

        float directFinal = directSource != null ? computeFinalDamage(directBaseDamage, player, directSource) : 0.0F;

        DamageSource blastSource = level.getDamageSources().explosion(projectile, owner);
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
     * armor ({@code DamageUtil.getDamageAfterAbsorb}) → Resistance effect → enchantment
     * damage protection ({@code DamageUtil.getDamageAfterMagicAbsorb}), honouring the
     * {@code BYPASSES_ARMOR} / {@code BYPASSES_EFFECTS} / {@code BYPASSES_RESISTANCE} /
     * {@code BYPASSES_ENCHANTMENTS} damage-type tags.
     */
    public static float computeFinalDamage(float rawDamage, PlayerEntity player, DamageSource source) {
        if (player.getAbilities().invulnerable || player.isSpectator()) {
            return 0.0F;
        }

        float damage = rawDamage;

        // Difficulty scaling (vanilla: PlayerEntity.hurtServer)
        if (source.isScaledWithDifficulty() && player.getEntityWorld() != null) {
            net.minecraft.world.Difficulty difficulty = player.getEntityWorld().getDifficulty();
            if (difficulty == net.minecraft.world.Difficulty.PEACEFUL) {
                return 0.0F;
            } else if (difficulty == net.minecraft.world.Difficulty.EASY) {
                damage = Math.min(damage / 2.0F + 1.0F, damage);
            } else if (difficulty == net.minecraft.world.Difficulty.HARD) {
                damage = damage * 3.0F / 2.0F;
            }
        }

        // 1. Armor (vanilla: LivingEntity.getDamageAfterArmorAbsorb)
        if (!source.isIn(DamageTypeTags.BYPASSES_ARMOR)) {
            damage = DamageUtil.getDamageLeft(player, damage, source,
                    player.getArmor(),
                    (float) player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS));
        }

        // 2. Resistance effect (vanilla: first half of LivingEntity.getDamageAfterMagicAbsorb)
        if (!source.isIn(DamageTypeTags.BYPASSES_EFFECTS)
                && player.hasStatusEffect(StatusEffects.RESISTANCE)
                && !source.isIn(DamageTypeTags.BYPASSES_RESISTANCE)) {
            int reduction = 25 - (player.getStatusEffect(StatusEffects.RESISTANCE).getAmplifier() + 1) * 5;
            damage = Math.max(damage * reduction / 25.0F, 0.0F);
        }

        if (damage <= 0.0F) {
            return 0.0F;
        }

        // 3. Enchantments (vanilla: second half of getDamageAfterMagicAbsorb).
        //    EnchantmentHelper.getDamageProtection returns 0 on the client (it requires a
        //    ServerWorld), so this is a faithful client-side reimplementation.
        if (!source.isIn(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            float epf = getEnchantmentProtection(player, source);
            if (epf > 0.0F) {
                damage = DamageUtil.getInflictedDamage(damage, epf);
            }
        }

        return Math.max(damage, 0.0F);
    }

    /**
     * Client-side EPF evaluation equivalent to vanilla
     * {@code EnchantmentHelper.getDamageProtection(ServerWorld, LivingEntity, DamageSource)}.
     *
     * <p>Iterates the player's armor slots, reads every enchantment's data-driven
     * {@code minecraft:damage_protection} effect ({@link EnchantmentEffectComponents#DAMAGE_PROTECTION}),
     * matches the effect's loot-condition requirements against the damage source and sums the
     * resulting value-effect EPF (vanilla data: Protection +1/level, Blast Protection +2/level).
     * The sum is later soft-capped at {@link #MAX_EPF} by {@code DamageUtil.getDamageAfterMagicAbsorb}.
     *
     * <p>Condition evaluation covers the condition shapes used by the base game and most packs
     * ({@link DamageSourcePropertiesLootCondition} with tag predicates, {@link AllOfLootCondition},
     * {@link AnyOfLootCondition}, {@link InvertedLootCondition}); unknown condition types are
     * conservatively treated as <b>not</b> matching, so protection is never overstated.
     */
    public static float getEnchantmentProtection(PlayerEntity player, DamageSource source) {
        float epf = 0.0F;
        // Mirrors vanilla EnchantmentHelper.runIterationOnEquipment: all six EquipmentSlot.VALUES.
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = player.getEquippedStack(slot);
            ItemEnchantmentsComponent enchantments = EnchantmentHelper.getEnchantments(stack);
            if (enchantments.isEmpty()) {
                continue;
            }
            // 1.21.11 has no enchantment effect components (26.2 API); compute the base-game
            // protection EPF directly with the vanilla 1.21.11 per-enchantment formulas.
            // Pack enchantments without a known protection shape are conservatively skipped,
            // mirroring the 26.2 implementation's unknown-condition handling.
            for (RegistryEntry<Enchantment> holder : enchantments.getEnchantments()) {
                int level = enchantments.getLevel(holder);
                if (holder.matchesKey(Enchantments.PROTECTION)) {
                    epf += level;
                } else if (holder.matchesKey(Enchantments.BLAST_PROTECTION)) {
                    epf += source.isIn(DamageTypeTags.IS_EXPLOSION) ? Math.min(level * 2, 16) : 0;
                } else if (holder.matchesKey(Enchantments.FIRE_PROTECTION)) {
                    epf += source.isIn(DamageTypeTags.IS_FIRE) ? Math.min(level * 2, 16) : 0;
                } else if (holder.matchesKey(Enchantments.PROJECTILE_PROTECTION)) {
                    epf += source.isIn(DamageTypeTags.IS_PROJECTILE) ? Math.min(level * 2, 16) : 0;
                } else if (holder.matchesKey(Enchantments.FEATHER_FALLING)) {
                    epf += source.isIn(DamageTypeTags.IS_FALL) ? Math.min(level * 3, 16) : 0;
                }
            }
        }
        return epf;
    }

    /**
     * Exact replica of vanilla {@code ServerExplosion.getExposure(Vec3d, Entity)}: raycasts a
     * deterministic grid over the entity's bounding box toward the explosion center and returns the
     * fraction of rays that reach it unobstructed. Deterministic (no RNG), so it is directly
     * assertable in GameTests. Must run on the main thread.
     */
    public static float getExposure(World level, Vec3d center, Entity entity) {
        // 1.21.11 has no Explosion.getExposure static (26.2 ServerExplosion.getSeenPercent);
        // this is the vanilla exposure raycast reimplemented against World.raycast.
        Box box = entity.getBoundingBox();
        double d = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double e = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double f = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        if (d <= 0.0 || e <= 0.0 || f <= 0.0) {
            return 0.0f;
        }
        double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;
        int hit = 0;
        int total = 0;
        for (double x = 0.0; x <= 1.0; x += d) {
            for (double y = 0.0; y <= 1.0; y += e) {
                for (double z = 0.0; z <= 1.0; z += f) {
                    double px = MathHelper.lerp(x, box.minX, box.maxX);
                    double py = MathHelper.lerp(y, box.minY, box.maxY);
                    double pz = MathHelper.lerp(z, box.minZ, box.maxZ);
                    Vec3d from = new Vec3d(px + g, py, pz + h);
                    RaycastContext raycastContext = new RaycastContext(from, center,
                            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity);
                    if (level.raycast(raycastContext).getType() == HitResult.Type.MISS) {
                        hit++;
                    }
                    total++;
                }
            }
        }
        return (float) hit / (float) total;
    }

    /**
     * Vanilla 26.2 knockback impulse magnitude (blocks/tick of the {@code entity.push(...)} delta):
     * {@code (1 - d/r) * seenPercent * knockbackMultiplier * (1 - EXPLOSION_KNOCKBACK_RESISTANCE)}.
     * The direction in vanilla is the normalized {@code (entity.getEyePos() - center)} vector
     * (away from the blast); this method returns the scalar magnitude only.
     */
    public static double computeKnockback(
            Vec3d explosionPos, float explosionPower, PlayerEntity player, float seenPercent, float knockbackMultiplier) {
        float radius = explosionPower * BLAST_RADIUS_MULTIPLIER;
        double distance = Math.sqrt(player.squaredDistanceTo(explosionPos));
        double knockbackResistance = player.getAttributeValue(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE);
        return (1.0 - distance / radius) * seenPercent * knockbackMultiplier * (1.0 - knockbackResistance);
    }

    private static boolean matchesCondition(Optional<LootCondition> requirements, DamageSource source) {
        return requirements.isEmpty() || matchesCondition(requirements.get(), source);
    }

    private static boolean matchesCondition(LootCondition condition, DamageSource source) {
        if (condition instanceof DamageSourcePropertiesLootCondition damageSourceCondition) {
            // The base-game condition shape: a DamageSourcePredicate with tag predicates.
            Optional<net.minecraft.predicate.entity.DamageSourcePredicate> predicate =
                    damageSourceCondition.predicate();
            if (predicate.isEmpty()) {
                return true;
            }
            for (TagPredicate<DamageType> tagPredicate : predicate.get().tags()) {
                if (!tagPredicate.test(source.getTypeRegistryEntry())) {
                    return false;
                }
            }
            // Entity predicates / isDirect are not evaluated client-side; tag checks above are the
            // decisive part for every vanilla damage-protection enchantment.
            return true;
        }
        if (condition instanceof InvertedLootCondition inverted) {
            return !matchesCondition(inverted.term(), source);
        }
        if (condition instanceof AllOfLootCondition allOf) {
            for (LootCondition term : ((CompositeLootItemConditionAccessor) allOf).getTerms()) {
                if (!matchesCondition(term, source)) {
                    return false;
                }
            }
            return true;
        }
        if (condition instanceof AnyOfLootCondition anyOf) {
            for (LootCondition term : ((CompositeLootItemConditionAccessor) anyOf).getTerms()) {
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
