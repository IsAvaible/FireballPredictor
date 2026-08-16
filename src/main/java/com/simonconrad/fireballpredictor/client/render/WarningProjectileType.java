package com.simonconrad.fireballpredictor.client.render;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Projectile category model for HUD impact warning badge icons and progress bar themes.
 */
public enum WarningProjectileType {
    FIREBALL(Items.FIRE_CHARGE, null, 0xAA1A0B00, 0xFFE67A00),
    WITHER_SKULL(Items.WITHER_SKELETON_SKULL, null, 0xAA141418, 0xFFA0A8B0),
    WIND_CHARGE(Items.WIND_CHARGE, null, 0xAA1C2230, 0xFFCFD6F7),
    DRAGON_FIREBALL(
        Items.DRAGON_HEAD,
        Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon_fireball.png"),
        0xAA1C0A20,
        0xFFC832D4
    );

    private final Item item;
    @Nullable
    private final Identifier customTexture;
    private final int barBackgroundColor;
    private final int barFillColor;
    private ItemStack cachedStack;

    WarningProjectileType(Item item, @Nullable Identifier customTexture, int barBackgroundColor, int barFillColor) {
        this.item = item;
        this.customTexture = customTexture;
        this.barBackgroundColor = barBackgroundColor;
        this.barFillColor = barFillColor;
    }

    public Item item() {
        return item;
    }

    public ItemStack icon() {
        if (cachedStack == null) {
            cachedStack = new ItemStack(item);
        }
        return cachedStack;
    }

    @Nullable
    public Identifier customTexture() {
        return customTexture;
    }

    public int barBackgroundColor() {
        return barBackgroundColor;
    }

    public int barFillColor() {
        return barFillColor;
    }

    public static WarningProjectileType fromProjectile(AbstractHurtingProjectile projectile) {
        if (projectile instanceof AbstractWindCharge) {
            return WIND_CHARGE;
        }
        if (projectile instanceof WitherSkull) {
            return WITHER_SKULL;
        }
        if (projectile instanceof DragonFireball) {
            return DRAGON_FIREBALL;
        }
        return FIREBALL;
    }
}
