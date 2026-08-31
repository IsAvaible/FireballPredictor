package com.simonconrad.fireballpredictor.hud;

import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.math.DamageCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;

/**
 * HUD overlays: the impact warning badge (icon + countdown bar + damage readout)
 * and the cracking hearts overlay painted over the vanilla health bar.
 */
public final class HudRenderer {

    private HudRenderer() {
    }

    private static final ResourceLocation CRACKING_FULL =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_full.png");
    private static final ResourceLocation CRACKING_HALF =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_half.png");
    private static final ResourceLocation CRACKING_HALF_RIGHT =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_half_right.png");
    private static final ResourceLocation CRACKING_HALF_ABSORBING_RIGHT =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_half_absorbing_right.png");
    private static final ResourceLocation CRACKING_FULL_BLINKING =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_full_blinking.png");
    private static final ResourceLocation CRACKING_HALF_BLINKING =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_half_blinking.png");
    private static final ResourceLocation CRACKING_HALF_RIGHT_BLINKING =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_half_right_blinking.png");
    private static final ResourceLocation CRACKING_HALF_ABSORBING_RIGHT_BLINKING =
            new ResourceLocation("fireballpredictor", "textures/hud/heart/cracking_half_absorbing_right_blinking.png");

    private static final int NUM_HEARTS_PER_ROW = 10;
    private static final int HEART_SIZE = 9;
    private static final int HEART_SEPARATION = 8;

    // ---- warning types -------------------------------------------------------

    public static final int TYPE_FIREBALL = 0;
    public static final int TYPE_WITHER_SKULL = 1;
    public static final int TYPE_DRAGON_FIREBALL = 2;

    private static ItemStack iconFor(int type) {
        switch (type) {
            case TYPE_WITHER_SKULL:
                return new ItemStack(Items.skull, 1, 1);
            default:
                return new ItemStack(Items.fire_charge);
        }
    }

    private static int fillColorFor(int type) {
        switch (type) {
            case TYPE_WITHER_SKULL:
                return 0xFFA0A8B0;
            case TYPE_DRAGON_FIREBALL:
                return 0xFFC832D4;
            default:
                return 0xFFE67A00;
        }
    }

    // ---- impact warning badge + damage readout ------------------------------

    public static void renderBadge(Minecraft mc, FireballPredictorClient state, ScaledResolution res) {
        if (!ModConfig.renderImpactWarning && !ModConfig.renderDamageText) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        boolean drawBadge = ModConfig.renderImpactWarning && state.impactWarningVisible;
        boolean drawText = ModConfig.renderDamageText && state.damageOverlayActive;
        if (!drawBadge && !drawText) {
            return;
        }

        int x = 8 + ModConfig.badgeOffsetX;
        int y = 8 + ModConfig.badgeOffsetY;
        int size = 20;
        int type = drawBadge ? state.impactWarningType : state.currentEstimateType;
        int fill = fillColorFor(type);

        if (drawBadge) {
            Gui.drawRect(x, y, x + size, y + size, 0xC8000000);
            Gui.drawRect(x, y, x + size, y + 1, 0x66FFFFFF);
            Gui.drawRect(x, y + size - 1, x + size, y + size, 0x66FFFFFF);
            Gui.drawRect(x, y, x + 1, y + size, 0x66FFFFFF);
            Gui.drawRect(x + size - 1, y, x + size, y + size, 0x66FFFFFF);

            RenderItem renderItem = mc.getRenderItem();
            RenderHelper.enableGUIStandardItemLighting();
            renderItem.renderItemAndEffectIntoGUI(iconFor(type), x + 2, y + 2);
            RenderHelper.disableStandardItemLighting();

            // countdown progress bar
            int barX = x + 2;
            int barY = y + size - 4;
            int barWidth = size - 4;
            int filled = Math.max(1, Math.round(MathHelper.clamp_float(state.impactWarningProgress, 0.0F, 1.0F) * barWidth));
            Gui.drawRect(barX, barY, barX + barWidth, barY + 2, 0xAA1A0B00);
            Gui.drawRect(barX, barY, barX + filled, barY + 2, fill);
        }

        if (drawText) {
            DamageCalculator.DamageEstimate estimate = state.currentEstimate;
            String text;
            if (estimate.finalDamage > 0.0F && estimate.knockbackBlocksPerSecond > 0.0) {
                text = String.format(Locale.ROOT, "-%.1f\u2764  %.1fb/s",
                        estimate.heartsLost, estimate.knockbackBlocksPerSecond);
            } else if (estimate.finalDamage > 0.0F) {
                text = String.format(Locale.ROOT, "-%.1f\u2764", estimate.heartsLost);
            } else {
                text = String.format(Locale.ROOT, "%.1fb/s", estimate.knockbackBlocksPerSecond);
            }
            mc.fontRendererObj.drawStringWithShadow(text, x + size + 5, y + 6, fill);
        }
    }

    // ---- cracking hearts -----------------------------------------------------

    public static void renderHearts(Minecraft mc, FireballPredictorClient state, ScaledResolution res) {
        if (!ModConfig.renderHeartsOverlay || !state.damageOverlayActive) {
            return;
        }
        DamageCalculator.DamageEstimate estimate = state.currentEstimate;
        if (estimate.finalDamage <= 0.0F) {
            return;
        }
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            return;
        }

        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        float maxHealth = player.getMaxHealth();

        int displayHealth = MathHelper.ceiling_float_int(health);
        int healthSlots = MathHelper.ceiling_float_int(Math.max(maxHealth, displayHealth) / 2.0F);
        int absorbSlots = MathHelper.ceiling_float_int(MathHelper.ceiling_float_int(absorption) / 2.0F);

        // Damage allocation: absorption hearts are consumed first, then health.
        float damageToAbs = Math.min(estimate.finalDamage, absorption);
        float damageToHp = Math.min(estimate.finalDamage - damageToAbs, health);
        float remAbs = absorption - damageToAbs;
        float remHp = health - damageToHp;

        // Same layout as 1.8.9 GuiIngame.renderHealth.
        int rows = MathHelper.ceiling_float_int(
                (Math.max(maxHealth, displayHealth) + MathHelper.ceiling_float_int(absorption)) / 2.0F / NUM_HEARTS_PER_ROW);
        int rowSpacing = Math.max(3, NUM_HEARTS_PER_ROW - (rows - 2));

        int left = res.getScaledWidth() / 2 - 91;
        int top = res.getScaledHeight() - 39;

        boolean blinking = mc.theWorld.getTotalWorldTime() % 6 < 3;

        renderSlots(mc, 0, healthSlots, remHp, health, left, top, rowSpacing, blinking, false);
        renderSlots(mc, healthSlots, absorbSlots, remAbs, absorption, left, top, rowSpacing, blinking, true);

        // Rebind the vanilla GUI atlas so subsequent HUD elements draw correctly.
        mc.getTextureManager().bindTexture(Gui.icons);
    }

    private static void renderSlots(Minecraft mc, int startSlot, int count,
                                    float remValue, float maxValue,
                                    int left, int top, int rowSpacing, boolean blinking, boolean absorbing) {
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

            ResourceLocation sprite;
            if (leftLost && rightLost) {
                sprite = blinking ? CRACKING_FULL_BLINKING : CRACKING_FULL;
            } else if (rightLost) {
                sprite = absorbing
                        ? (blinking ? CRACKING_HALF_ABSORBING_RIGHT_BLINKING : CRACKING_HALF_ABSORBING_RIGHT)
                        : (blinking ? CRACKING_HALF_RIGHT_BLINKING : CRACKING_HALF_RIGHT);
            } else {
                sprite = blinking ? CRACKING_HALF_BLINKING : CRACKING_HALF;
            }

            mc.getTextureManager().bindTexture(sprite);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, HEART_SIZE, HEART_SIZE, HEART_SIZE, HEART_SIZE);
        }
    }
}
