package com.simonconrad.fireballpredictor.projectile;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Projectile category model for HUD impact warning badge icons and progress bar themes.
 *
 * <p>This is the display-side companion of {@link ProjectileKind}: it maps a kind onto the icon,
 * optional custom texture and bar colours used by the impact warning badge. It intentionally uses
 * only side-neutral Minecraft types ({@link Item}, {@link Identifier}, {@link ItemStack}) so it can
 * be loaded headlessly by the GameTest suite.
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

    /** Maps a {@link ProjectileKind} onto its display category. */
    public static WarningProjectileType fromKind(ProjectileKind kind) {
        return switch (kind) {
            case DRAGON_FIREBALL -> DRAGON_FIREBALL;
            case WITHER_SKULL -> WITHER_SKULL;
            case WIND_CHARGE, BREEZE_WIND_CHARGE -> WIND_CHARGE;
            case LARGE_FIREBALL, SMALL_FIREBALL -> FIREBALL;
        };
    }

    public static WarningProjectileType fromProjectile(AbstractHurtingProjectile projectile) {
        ProjectileProfile profile = VanillaProfiles.from(projectile);
        return profile != null ? profile.warningType() : FIREBALL;
    }
}
