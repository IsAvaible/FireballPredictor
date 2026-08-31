package com.simonconrad.fireballpredictor.client.render;

import java.util.Locale;

import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.math.DamageCalculator.DamageEstimate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import com.simonconrad.fireballpredictor.client.gui.SpriteBlitter;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.player.PlayerEntity;

/**
 * "Cracking fireball hearts" HUD overlay. Registered via
 * {@code HudElementRegistry.attachElementAfter(VanillaHudElements.HEALTH_BAR, ...)} so it paints
 * on top of the vanilla health bar, replacing the exact hearts the player is predicted to lose.
 *
 * <p>Damage is allocated in two stages mirroring vanilla:
 * <ul>
 *   <li>Absorption hearts are consumed first, starting from the top absorption point.</li>
 *   <li>Remaining damage consumes current health, starting from the top health point.</li>
 * </ul>
 * Overlapping half-heart units per slot are evaluated independently so health slots, absorption
 * slots, odd health/damage values, and partial health loss render accurately.
 */
public final class HeartOverlayRenderer {

    private static final int NUM_HEARTS_PER_ROW = 10;
    private static final int HEART_SIZE = 9;
    private static final int HEART_SEPARATION = 8;
    private static final int HEARTS_LEFT_OFFSET = 91;

    private static final Identifier CRACKING_FULL = Identifier.of("fireballpredictor", "hud/heart/cracking_full");
    private static final Identifier CRACKING_HALF = Identifier.of("fireballpredictor", "hud/heart/cracking_half");
    private static final Identifier CRACKING_HALF_RIGHT = Identifier.of("fireballpredictor", "hud/heart/cracking_half_right");
    private static final Identifier CRACKING_FULL_BLINKING = Identifier.of("fireballpredictor", "hud/heart/cracking_full_blinking");
    private static final Identifier CRACKING_HALF_BLINKING = Identifier.of("fireballpredictor", "hud/heart/cracking_half_blinking");
    private static final Identifier CRACKING_HALF_RIGHT_BLINKING = Identifier.of("fireballpredictor", "hud/heart/cracking_half_right_blinking");

    private static final int TEXT_COLOR = 0xFFE67A00;

    private HeartOverlayRenderer() {
    }

    /**
     * Draws the cracking hearts and the damage/knockback readout for the currently selected threat.
     * No-op unless the threat is in range, config toggles are enabled and the player is alive.
     */
    public static void render(DrawContext graphics, MinecraftClient client, boolean active, DamageEstimate estimate) {
        if (!active || estimate == null || !estimate.inRange()) {
            return;
        }
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        ModConfig config = ModConfig.instance();
        boolean drawHearts = config.renderDamageHeartsOverlay;
        boolean drawText = config.showKnockbackEstimator;
        if (!drawHearts && !drawText) {
            return;
        }

        float finalDamage = estimate.finalDamage();
        if (finalDamage <= 0.0F) {
            return;
        }

        if (drawHearts) {
            drawCrackedHearts(graphics, player, finalDamage);
        }
        if (drawText) {
            drawDamageText(graphics, client, estimate);
        }
    }

    private static void drawCrackedHearts(DrawContext graphics, PlayerEntity player, float finalDamage) {
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        float maxHealth = player.getMaxHealth();

        int displayHealth = MathHelper.ceil(health);
        int absorbHeartsRaw = MathHelper.ceil(absorption);

        int healthSlots = MathHelper.ceil(Math.max(maxHealth, displayHealth) / 2.0F);
        int absorbSlots = MathHelper.ceil(absorbHeartsRaw / 2.0F);
        int totalHearts = healthSlots + absorbSlots;
        if (totalHearts <= 0) {
            return;
        }

        int heartRows = MathHelper.ceil((Math.max(maxHealth, displayHealth) + absorbHeartsRaw) / 2.0F / NUM_HEARTS_PER_ROW);
        int rowSpacing = Math.max(3, NUM_HEARTS_PER_ROW - (heartRows - 2));

        // Damage allocation: absorption first, then health
        float damageToAbs = Math.min(finalDamage, absorption);
        float damageToHp = Math.min(finalDamage - damageToAbs, health);

        float remAbs = absorption - damageToAbs;
        float remHp = health - damageToHp;

        int left = graphics.getScaledWindowWidth() / 2 - HEARTS_LEFT_OFFSET;
        int top = graphics.getScaledWindowHeight() - 39;

        long gameTime = player.getEntityWorld().getTime();
        boolean blinking = (gameTime % 6L) < 3L;

        // 1. Health heart slots (indices 0 .. healthSlots - 1)
        for (int i = 0; i < healthSlots; i++) {
            float hpLeftVal = i * 2.0F;
            float hpRightVal = i * 2.0F + 1.0F;

            boolean leftLost = remHp <= hpLeftVal && hpLeftVal < health;
            boolean rightLost = remHp <= hpRightVal && hpRightVal < health;

            if (!leftLost && !rightLost) {
                continue;
            }

            int row = i / NUM_HEARTS_PER_ROW;
            int col = i % NUM_HEARTS_PER_ROW;
            int x = left + col * HEART_SEPARATION;
            int y = top - row * rowSpacing;

            boolean full = leftLost && rightLost;
            Identifier sprite;
            if (full) {
                sprite = blinking ? CRACKING_FULL_BLINKING : CRACKING_FULL;
            } else if (rightLost) {
                sprite = blinking ? CRACKING_HALF_RIGHT_BLINKING : CRACKING_HALF_RIGHT;
            } else {
                sprite = blinking ? CRACKING_HALF_BLINKING : CRACKING_HALF;
            }

            SpriteBlitter.draw(graphics, sprite, x, y, HEART_SIZE, HEART_SIZE);
        }

        // 2. Absorption heart slots (placed after healthSlots, indices healthSlots .. totalHearts - 1)
        for (int j = 0; j < absorbSlots; j++) {
            float absLeftVal = j * 2.0F;
            float absRightVal = j * 2.0F + 1.0F;

            boolean leftLost = remAbs <= absLeftVal && absLeftVal < absorption;
            boolean rightLost = remAbs <= absRightVal && absRightVal < absorption;

            if (!leftLost && !rightLost) {
                continue;
            }

            int slot = healthSlots + j;
            int row = slot / NUM_HEARTS_PER_ROW;
            int col = slot % NUM_HEARTS_PER_ROW;
            int x = left + col * HEART_SEPARATION;
            int y = top - row * rowSpacing;

            boolean full = leftLost && rightLost;
            Identifier sprite;
            if (full) {
                sprite = blinking ? CRACKING_FULL_BLINKING : CRACKING_FULL;
            } else if (rightLost) {
                sprite = blinking ? CRACKING_HALF_RIGHT_BLINKING : CRACKING_HALF_RIGHT;
            } else {
                sprite = blinking ? CRACKING_HALF_BLINKING : CRACKING_HALF;
            }

            SpriteBlitter.draw(graphics, sprite, x, y, HEART_SIZE, HEART_SIZE);
        }
    }

    private static void drawDamageText(DrawContext graphics, MinecraftClient client, DamageEstimate estimate) {
        TextRenderer font = client.textRenderer;
        if (font == null) {
            return;
        }

        int[] badge = PredictionRenderer.impactBadgePosition(client);
        ImpactWarningBadgeAnchor anchor = ModConfig.instance().impactWarningBadgeAnchor;
        if (anchor == null) {
            anchor = ImpactWarningBadgeAnchor.TOP_LEFT;
        }

        String text = String.format(Locale.ROOT, "-%.1f\u2764  \u26a1%.1fb/s",
                estimate.heartsLost(), estimate.knockbackBlocksPerSecond());

        int textWidth = font.getWidth(text);
        int textX = (anchor == ImpactWarningBadgeAnchor.TOP_RIGHT || anchor == ImpactWarningBadgeAnchor.BOTTOM_RIGHT)
                ? badge[0] - textWidth - 6
                : badge[0] + 24;

        graphics.drawText(font, text, textX, badge[1] + 6, TEXT_COLOR, true);
    }
}
