package com.simonconrad.fireballpredictor.client.render;

import java.util.Locale;

import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.math.DamageCalculator.DamageEstimate;
import com.simonconrad.fireballpredictor.projectile.WarningProjectileType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * "Cracking fireball hearts" HUD overlay. Registered via
 * {@code HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, ...)} so it paints
 * on top of the vanilla health bar, displaying animated damage crack overlays over the exact
 * heart units predicted to be lost.
 *
 * <p>Uses a pure overlay approach: overlay sprites contain only the luminous crack fissures and
 * subtle heat stress with transparent backgrounds, leaving intact half-units (alpha = 0) and
 * underlying heart shapes/colors untouched. This natively supports any status effect (Wither,
 * Poison, Frozen, Absorption), game mode (Hardcore), and custom resource packs.
 *
 * <p>Damage is allocated in two stages mirroring vanilla:
 * <ul>
 *   <li>Absorption hearts are consumed first, starting from the top absorption point.</li>
 *   <li>Remaining damage consumes current health, starting from the top health point.</li>
 * </ul>
 */
public final class HeartOverlayRenderer {

    private static final int NUM_HEARTS_PER_ROW = 10;
    private static final int HEART_SIZE = 9;
    private static final int HEART_SEPARATION = 8;
    private static final int HEARTS_LEFT_OFFSET = 91;

    private static final Identifier CRACKING_FULL = Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_full");
    private static final Identifier CRACKING_HALF = Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_half");
    private static final Identifier CRACKING_HALF_RIGHT = Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_half_right");
    private static final Identifier CRACKING_FULL_BLINKING = Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_full_blinking");
    private static final Identifier CRACKING_HALF_BLINKING = Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_half_blinking");
    private static final Identifier CRACKING_HALF_RIGHT_BLINKING = Identifier.fromNamespaceAndPath("fireballpredictor", "hud/heart/cracking_half_right_blinking");

    private static final int TEXT_COLOR = 0xFFE67A00;

    private HeartOverlayRenderer() {
    }

    /**
     * Draws the cracking hearts and the damage/knockback readout for the currently selected threat.
     * No-op unless the threat is in range, config toggles are enabled and the player is alive.
     */
    public static void render(GuiGraphicsExtractor graphics, Minecraft client, boolean active, DamageEstimate estimate) {
        render(graphics, client, active, estimate, WarningProjectileType.FIREBALL);
    }

    public static void render(GuiGraphicsExtractor graphics, Minecraft client, boolean active, DamageEstimate estimate, WarningProjectileType type) {
        if (!active || estimate == null || !estimate.inRange()) {
            return;
        }
        Player player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        ModConfig config = ModConfig.instance();
        boolean drawHearts = config.renderDamageHeartsOverlay;
        boolean drawText = config.showKnockbackEstimator;
        if (!drawHearts && !drawText) {
            return;
        }

        float finalDamage = estimate.finalDamage();
        double knockback = estimate.knockbackBlocksPerSecond();
        if (finalDamage <= 0.0F && knockback <= 0.0) {
            return;
        }

        if (drawHearts && finalDamage > 0.0F) {
            drawCrackedHearts(graphics, player, finalDamage);
        }
        if (drawText && (finalDamage > 0.0F || knockback > 0.0)) {
            drawDamageText(graphics, client, estimate, type);
        }
    }

    private static void drawCrackedHearts(GuiGraphicsExtractor graphics, Player player, float finalDamage) {
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        float maxHealth = player.getMaxHealth();

        int displayHealth = Mth.ceil(health);
        int absorbHeartsRaw = Mth.ceil(absorption);

        int healthSlots = Mth.ceil(Math.max(maxHealth, displayHealth) / 2.0F);
        int absorbSlots = Mth.ceil(absorbHeartsRaw / 2.0F);
        int totalHearts = healthSlots + absorbSlots;
        if (totalHearts <= 0) {
            return;
        }

        int heartRows = Mth.ceil((Math.max(maxHealth, displayHealth) + absorbHeartsRaw) / 2.0F / NUM_HEARTS_PER_ROW);
        int rowSpacing = Math.max(3, NUM_HEARTS_PER_ROW - (heartRows - 2));

        // Damage allocation: absorption first, then health
        float damageToAbs = Math.min(finalDamage, absorption);
        float damageToHp = Math.min(finalDamage - damageToAbs, health);

        float remAbs = absorption - damageToAbs;
        float remHp = health - damageToHp;

        int left = graphics.guiWidth() / 2 - HEARTS_LEFT_OFFSET;
        int top = graphics.guiHeight() - 39;

        long gameTime = player.level().getGameTime();
        boolean blinking = (gameTime % 6L) < 3L;

        // 1. Health heart slots (indices 0 .. healthSlots - 1)
        renderHeartSlots(graphics, 0, healthSlots, remHp, health, left, top, rowSpacing, blinking);

        // 2. Absorption heart slots (placed after healthSlots, indices healthSlots .. totalHearts - 1)
        renderHeartSlots(graphics, healthSlots, absorbSlots, remAbs, absorption, left, top, rowSpacing, blinking);
    }

    /**
     * Resolves the appropriate overlay sprite for a heart slot based on which half-units are lost.
     */
    public static Identifier getOverlaySprite(boolean leftLost, boolean rightLost, boolean blinking) {
        if (leftLost && rightLost) {
            return blinking ? CRACKING_FULL_BLINKING : CRACKING_FULL;
        } else if (rightLost) {
            return blinking ? CRACKING_HALF_RIGHT_BLINKING : CRACKING_HALF_RIGHT;
        } else if (leftLost) {
            return blinking ? CRACKING_HALF_BLINKING : CRACKING_HALF;
        }
        return null;
    }

    public static Identifier fullOverlay(boolean blinking) {
        return blinking ? CRACKING_FULL_BLINKING : CRACKING_FULL;
    }

    public static Identifier halfLeftOverlay(boolean blinking) {
        return blinking ? CRACKING_HALF_BLINKING : CRACKING_HALF;
    }

    public static Identifier halfRightOverlay(boolean blinking) {
        return blinking ? CRACKING_HALF_RIGHT_BLINKING : CRACKING_HALF_RIGHT;
    }

    private static void renderHeartSlots(GuiGraphicsExtractor graphics, int startSlot, int count,
                                         float remValue, float maxValue,
                                         int left, int top, int rowSpacing, boolean blinking) {
        for (int i = 0; i < count; i++) {
            float leftVal = i * 2.0F;
            float rightVal = i * 2.0F + 1.0F;

            boolean leftLost = remValue <= leftVal && leftVal < maxValue;
            boolean rightLost = remValue <= rightVal && rightVal < maxValue;

            if (!leftLost && !rightLost) {
                continue;
            }

            int slot = startSlot + i;
            int row = slot / NUM_HEARTS_PER_ROW;
            int col = slot % NUM_HEARTS_PER_ROW;
            int x = left + col * HEART_SEPARATION;
            int y = top - row * rowSpacing;

            if (DynamicHeartOverlayManager.isInitialized()) {
                Identifier dynamicTex = DynamicHeartOverlayManager.getOverlayTexture(leftLost, rightLost, blinking);
                if (dynamicTex != null) {
                    graphics.blit(RenderPipelines.GUI_TEXTURED, dynamicTex, x, y, 0.0F, 0.0F, HEART_SIZE, HEART_SIZE, HEART_SIZE, HEART_SIZE);
                }
            } else {
                Identifier sprite = getOverlaySprite(leftLost, rightLost, blinking);
                if (sprite != null) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, HEART_SIZE, HEART_SIZE);
                }
            }
        }
    }

    private static void drawDamageText(GuiGraphicsExtractor graphics, Minecraft client, DamageEstimate estimate, WarningProjectileType type) {
        Font font = client.font;
        if (font == null) {
            return;
        }

        PredictionRenderer.BadgePosition badge = PredictionRenderer.impactBadgePosition(client);
        ImpactWarningBadgeAnchor anchor = ModConfig.instance().impactWarningBadgeAnchor;
        if (anchor == null) {
            anchor = ImpactWarningBadgeAnchor.TOP_LEFT;
        }

        String text;
        if (estimate.finalDamage() > 0.0F && estimate.knockbackBlocksPerSecond() > 0.0) {
            text = String.format(Locale.ROOT, "-%.1f\u2764  \u26a1%.1fb/s",
                    estimate.heartsLost(), estimate.knockbackBlocksPerSecond());
        } else if (estimate.finalDamage() > 0.0F) {
            text = String.format(Locale.ROOT, "-%.1f\u2764", estimate.heartsLost());
        } else {
            text = String.format(Locale.ROOT, "\u26a1%.1fb/s", estimate.knockbackBlocksPerSecond());
        }

        int textWidth = font.width(text);
        int textX = (anchor == ImpactWarningBadgeAnchor.TOP_RIGHT || anchor == ImpactWarningBadgeAnchor.BOTTOM_RIGHT)
                ? badge.x() - textWidth - 6
                : badge.x() + 24;

        int textColor = type != null ? type.barFillColor() : TEXT_COLOR;
        graphics.text(font, text, textX, badge.y() + 6, textColor, true);
    }
}
