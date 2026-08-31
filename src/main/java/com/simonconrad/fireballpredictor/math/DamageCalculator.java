package com.simonconrad.fireballpredictor.math;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

/**
 * Client-side replica of the 1.8.9 damage pipeline for explosions and fireball
 * direct hits, mirroring {@code Explosion.doExplosionA},
 * {@code EntityPlayer.attackEntityFrom} -> {@code EntityLivingBase.damageEntity} ->
 * {@code applyPotionDamageCalculations}:
 *
 * <pre>
 *   raw    = (int)((d10*d10 + d10) / 2 * 8 * radius + 1)      with d10 = (1 - dist/radius) * blockDensity
 *   order  = difficulty scaling -> blocking -> armor -> Resistance -> EPF (cap 20) -> absorption
 *   EPF    = per armor piece: floor((6 + level^2) / 3 * typeMult); typeMult: Prot 0.75,
 *            Fire 1.25, Blast 1.5, Projectile 1.5  (worst-case random roll, like the original mod's upper bounds)
 *   knockback = density, reduced by Blast Protection: d - floor(d * maxLevel * 0.15)
 * </pre>
 *
 * Note: in 1.8.9 explosions are difficulty-scaled but NOT armor-bypassing, and the
 * Resistance effect does not reduce explosion knockback (that changed in 1.9+).
 */
public final class DamageCalculator {

    private DamageCalculator() {
    }

    /** Vanilla 1.8.9: blast radius = power * 2. */
    public static final float BLAST_RADIUS_MULTIPLIER = 2.0F;

    /** Damage source behaviour flags, mirroring the 1.8.9 DamageSource subclasses. */
    public enum SourceType {
        /** "explosion" source: difficulty-scaled, armor applies, Blast Protection applies. */
        EXPLOSION(false, true, false, false),
        /** fireball direct hit ("fireball"/"onFire" indirect): armor applies, projectile+fire. */
        DIRECT_FIREBALL(false, false, true, true),
        /** wither skull direct hit with shooter ("mob" damage): armor applies. */
        DIRECT_WITHER_MOB(false, false, false, false),
        /** wither skull direct hit without shooter (magic): bypasses armor. */
        DIRECT_WITHER_MAGIC(true, false, false, false);

        public final boolean bypassArmor;
        public final boolean difficultyScaled;
        public final boolean projectile;
        public final boolean fire;

        SourceType(boolean bypassArmor, boolean difficultyScaled, boolean projectile, boolean fire) {
            this.bypassArmor = bypassArmor;
            this.difficultyScaled = difficultyScaled;
            this.projectile = projectile;
            this.fire = fire;
        }

        boolean explosion() {
            return this == EXPLOSION;
        }
    }

    /** Immutable result of a damage & knockback prediction. */
    public static final class DamageEstimate {
        public final float rawDamage;
        public final float finalDamage;
        public final float heartsLost;
        public final double knockbackBlocksPerSecond;
        public final float seenPercent;
        public final boolean inRange;

        public static final DamageEstimate NONE = new DamageEstimate(0.0F, 0.0F, 0.0F, 0.0, 0.0F, false);

        public DamageEstimate(float rawDamage, float finalDamage, float heartsLost,
                              double knockbackBlocksPerSecond, float seenPercent, boolean inRange) {
            this.rawDamage = rawDamage;
            this.finalDamage = finalDamage;
            this.heartsLost = heartsLost;
            this.knockbackBlocksPerSecond = knockbackBlocksPerSecond;
            this.seenPercent = seenPercent;
            this.inRange = inRange;
        }
    }

    /**
     * Estimates the damage the player would take from an explosion at the given position.
     */
    public static DamageEstimate estimateExplosion(World world, EntityPlayer player, Vec3 explosionPos, float power) {
        if (power <= 0.0F || player == null || explosionPos == null) {
            return DamageEstimate.NONE;
        }

        float radius = power * BLAST_RADIUS_MULTIPLIER;
        double distance = Math.sqrt(player.getDistanceSq(explosionPos.xCoord, explosionPos.yCoord, explosionPos.zCoord));

        if (distance > radius) {
            return DamageEstimate.NONE;
        }

        // Vanilla 1.8.9: World.getBlockDensity (line-of-sight exposure).
        float density = world.getBlockDensity(explosionPos, player.getEntityBoundingBox());

        // vanilla: (int)((d10*d10 + d10) / 2.0 * 8.0 * radius + 1.0)
        double d10 = (1.0 - distance / (double) radius) * density;
        float raw = (float) ((int) ((d10 * d10 + d10) / 2.0 * 8.0 * (double) radius + 1.0));

        float finalDamage = applyPlayerPipeline(raw, player, world, SourceType.EXPLOSION);
        double knockback = computeKnockback(player, d10);

        return new DamageEstimate(raw, finalDamage,
                Math.min(finalDamage, player.getHealth() + player.getAbsorptionAmount()) / 2.0F,
                knockback * 20.0, density, true);
    }

    /**
     * Estimates the damage from the projectile directly colliding with the player
     * (before the follow-up explosion). Vanilla applies the direct hit AND the blast;
     * like the original mod we report the more severe of the two.
     */
    public static DamageEstimate estimateDirectHit(World world, EntityPlayer player, Vec3 hitPos,
                                                   float power, float directDamage, SourceType directType) {
        DamageEstimate direct = applyDirect(player, directDamage, world, directType);

        // The projectile detonates at the hit point: explosion estimate as well.
        DamageEstimate blast = estimateExplosion(world, player, hitPos, power);

        float raw = Math.max(direct.rawDamage, blast.inRange ? blast.rawDamage : 0.0F);
        float total = Math.max(direct.finalDamage, blast.inRange ? blast.finalDamage : 0.0F);
        double knockback = blast.inRange ? blast.knockbackBlocksPerSecond : 0.0;
        float seen = blast.inRange ? blast.seenPercent : 1.0F;

        return new DamageEstimate(raw, total,
                Math.min(total, player.getHealth() + player.getAbsorptionAmount()) / 2.0F,
                knockback, seen, true);
    }

    private static DamageEstimate applyDirect(EntityPlayer player, float damage, World world, SourceType type) {
        float finalDamage = applyPlayerPipeline(damage, player, world, type);
        return new DamageEstimate(damage, finalDamage,
                Math.min(finalDamage, player.getHealth() + player.getAbsorptionAmount()) / 2.0F,
                0.0, 1.0F, damage > 0.0F);
    }

    /**
     * The 1.8.9 pipeline (EntityPlayer.attackEntityFrom + EntityLivingBase.damageEntity +
     * applyPotionDamageCalculations), worst-case EPF roll:
     * creative -> fire resistance -> difficulty -> blocking -> armor -> Resistance -> EPF -> absorption.
     */
    private static float applyPlayerPipeline(float damage, EntityPlayer player, World world, SourceType type) {
        if (damage <= 0.0F || player == null) {
            return 0.0F;
        }
        // Creative/spectator invulnerability.
        if (player.capabilities.disableDamage) {
            return 0.0F;
        }
        if (player.getHealth() <= 0.0F) {
            return 0.0F;
        }
        // Fire resistance vs. fire damage (direct fireball hits set fire).
        if (type.fire && player.isPotionActive(Potion.fireResistance)) {
            return 0.0F;
        }

        float amount = damage;

        // Difficulty scaling (first, as in 1.8.9 EntityPlayer.attackEntityFrom).
        if (type.difficultyScaled) {
            EnumDifficulty difficulty = world.getDifficulty();
            if (difficulty == EnumDifficulty.PEACEFUL) {
                return 0.0F;
            } else if (difficulty == EnumDifficulty.EASY) {
                amount = amount / 2.0F + 1.0F;
            } else if (difficulty == EnumDifficulty.HARD) {
                amount = amount * 3.0F / 2.0F;
            }
        }

        if (amount == 0.0F) {
            return 0.0F;
        }

        // Sword blocking halves incoming damage (not for armor-bypassing sources).
        if (!type.bypassArmor && player.isBlocking() && amount > 0.0F) {
            amount = (1.0F + amount) * 0.5F;
        }

        // Armor.
        if (!type.bypassArmor) {
            int i = 25 - player.getTotalArmorValue();
            amount = amount * (float) i / 25.0F;
        }

        // Resistance potion (explosions are not "absolute" damage).
        if (player.isPotionActive(Potion.resistance)) {
            int i = (player.getActivePotionEffect(Potion.resistance).getAmplifier() + 1) * 5;
            int j = 25 - i;
            amount = amount * (float) j / 25.0F;
        }

        if (amount <= 0.0F) {
            return 0.0F;
        }

        // Enchantment protection (EPF), worst-case roll, capped at 20 (vanilla cap).
        int epf = getProtectionEpf(player, type);
        if (epf > 20) {
            epf = 20;
        }
        if (epf > 0) {
            amount = amount * (float) (25 - epf) / 25.0F;
        }

        return amount;
    }

    /**
     * 1.8.9 EnchantmentProtection.calcModifierDamage summed over the armor slots
     * (EntityPlayer.getInventory() returns the armor inventory):
     * f = (6 + level^2) / 3; Protection x0.75, Fire x1.25, Blast x1.5, Projectile x1.5.
     */
    private static int getProtectionEpf(EntityPlayer player, SourceType type) {
        int epf = 0;
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack != null) {
                epf += stackProtectionEpf(stack, type);
            }
        }
        return epf;
    }

    private static int stackProtectionEpf(ItemStack stack, SourceType type) {
        int epf = 0;
        int level = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
        if (level > 0) {
            epf += MathHelper.floor_float((6 + level * level) / 3.0F * 0.75F);
        }
        if (type.fire) {
            level = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireProtection.effectId, stack);
            if (level > 0) {
                epf += MathHelper.floor_float((6 + level * level) / 3.0F * 1.25F);
            }
        }
        if (type.projectile) {
            level = EnchantmentHelper.getEnchantmentLevel(Enchantment.projectileProtection.effectId, stack);
            if (level > 0) {
                epf += MathHelper.floor_float((6 + level * level) / 3.0F * 1.5F);
            }
        }
        if (type.explosion()) {
            level = EnchantmentHelper.getEnchantmentLevel(Enchantment.blastProtection.effectId, stack);
            if (level > 0) {
                epf += MathHelper.floor_float((6 + level * level) / 3.0F * 1.5F);
            }
        }
        return epf;
    }

    /**
     * Vanilla 1.8.9 explosion knockback impulse magnitude (blocks/tick):
     * density reduced by Blast Protection (EnchantmentProtection.func_92092_a:
     * d - floor(d * maxLevel * 0.15)), applied along (eye position - explosion center).
     * 1.8.9 does NOT apply Resistance to knockback.
     */
    private static double computeKnockback(EntityPlayer player, double density) {
        double d11 = density;
        int maxBlastLevel = 0;
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack != null) {
                maxBlastLevel = Math.max(maxBlastLevel,
                        EnchantmentHelper.getEnchantmentLevel(Enchantment.blastProtection.effectId, stack));
            }
        }
        if (maxBlastLevel > 0) {
            d11 -= (double) MathHelper.floor_double(d11 * (double) (maxBlastLevel * 0.15F));
        }
        return Math.max(d11, 0.0);
    }
}
