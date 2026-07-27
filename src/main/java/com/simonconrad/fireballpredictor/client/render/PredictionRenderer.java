package com.simonconrad.fireballpredictor.client.render;

import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;

import net.minecraft.client.render.*;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import org.joml.Matrix4f;

public class PredictionRenderer {

    private static final RenderLayer FIREBALL_TRAIL = net.minecraft.client.render.RenderLayers.lightning();
    private static final RenderLayer SHOCKWAVE_DOME = net.minecraft.client.render.RenderLayers.lightning();
    private static final ItemStack WARNING_ICON = new ItemStack(Items.FIRE_CHARGE);
    private static final ItemStack WIND_CHARGE_WARNING_ICON = new ItemStack(Items.WIND_CHARGE);

    public static void renderImpactWarningBadge(DrawContext context, MinecraftClient client, boolean visible, float progress, boolean isWindCharge) {
        if (!visible || client.player == null) {
            return;
        }

        if (client.world == null) {
            return;
        }

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();
        if (!config.renderImpactWarning) {
            return;
        }
        int badgeWidth = 20;
        int badgeHeight = 20;
        int margin = 8;
        int windowWidth = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();
        ImpactWarningBadgeAnchor anchor = config.impactWarningBadgeAnchor == null ? ImpactWarningBadgeAnchor.TOP_LEFT : config.impactWarningBadgeAnchor;

        int x = switch (anchor) {
            case TOP_RIGHT, BOTTOM_RIGHT -> windowWidth - badgeWidth - margin;
            case TOP_CENTER, BOTTOM_CENTER -> (windowWidth - badgeWidth) / 2;
            default -> margin;
        } + config.impactWarningBadgeOffsetX;

        int y = switch (anchor) {
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> windowHeight - badgeHeight - margin;
            default -> margin;
        } + config.impactWarningBadgeOffsetY;

        int size = 20;

        context.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("textures/gui/sprites/hud/effect_background.png"), x, y, 0.0f, 0.0f, size, size, 20, 20);

        ItemStack icon = isWindCharge ? WIND_CHARGE_WARNING_ICON : WARNING_ICON;
        context.drawItem(icon, x + 2, y + 2);

        int barX = x + 2;
        int barY = y + size - 2;
        int barWidth = 15;
        int barHeight = 1;
        int filledWidth = Math.max(1, Math.round(MathHelper.clamp(progress, 0.0f, 1.0f) * barWidth));

        if (isWindCharge) {
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xAA1C2230);
            context.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFFCFD6F7);
            context.fill(barX, barY, barX + barWidth, barY + 1, 0x55FFFFFF);
        } else {
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xAA1A0B00);
            context.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFFE67A00);
            context.fill(barX, barY, barX + barWidth, barY + 1, 0x55FFFFFF);
        }
    }

    private static Vec3d safeNormalize(Vec3d v, Vec3d fallback) {
        double lenSq = v.lengthSquared();
        if (lenSq > 1e-7) {
            return v.multiply(1.0 / Math.sqrt(lenSq));
        }
        return fallback;
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Camera camera, ClientWorld world, PredictionData data, ExplosiveProjectileEntity fireball) {
        Vec3d cameraPos = camera.getCameraPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        Vec3d camLook = Vec3d.fromPolar(pitch, yaw);

        int elapsedTicks = Math.max(0, fireball.age - data.predictionAge);

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();
        boolean isWindCharge = fireball instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity;

        java.awt.Color trajectoryColor = isWindCharge ? config.windChargeTrajectoryColor : config.trajectoryColor;
        java.awt.Color shockwaveColor = isWindCharge ? config.windChargeShockwaveColor : config.shockwaveColor;

        // Render Trajectory Ribbon
        if (config.renderTrajectory && data.path != null && data.path.size() > 1) {
            VertexConsumer consumer = vertexConsumers.getBuffer(FIREBALL_TRAIL);
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            
            float baseWidth = config.trajectoryWidth;
            int r = trajectoryColor.getRed();
            int g = trajectoryColor.getGreen();
            int b = trajectoryColor.getBlue();

            int totalPathSteps = data.path.size() - 1;
            int totalRenderSteps = totalPathSteps - elapsedTicks;
            float startBlendSteps = 1.0f; // Quick 1-tick transition (~1/3 of previous 3-tick duration)

            // Parse visual style & render toggles once outside the loop
            TrajectoryStyle style = config.trajectoryStyle == null ? TrajectoryStyle.SOLID : config.trajectoryStyle;
            boolean isDashed = style == TrajectoryStyle.DASHED;
            boolean isCoreOnly = style == TrajectoryStyle.CORE_ONLY;
            boolean drawCore = config.renderCoreGlow || isCoreOnly;
            boolean drawShroud = !isCoreOnly;

            // In-game world time for game-pause-safe animation
            float animTime = (float) (world.getTime() % 24000L);
            float pulseSpeed = 0.1f + (1.0f - MathHelper.clamp((float) totalRenderSteps / 30.0f, 0.0f, 1.0f)) * 0.1f;

            for (int i = elapsedTicks; i < totalPathSteps; i++) {
                Vec3d p1 = (i == elapsedTicks) ? fireball.getEntityPos() : data.path.get(i);
                Vec3d p2 = data.path.get(i + 1);
                
                Vec3d rawDir = p2.subtract(p1);
                if (rawDir.lengthSquared() < 0.000001) {
                    rawDir = (i + 2 < data.path.size()) ? data.path.get(i + 2).subtract(p1) : new Vec3d(0, 0, 1);
                }
                Vec3d dir = safeNormalize(rawDir, new Vec3d(0, 0, 1));

                // Compute start blend factor for p1 and p2 (ramping quickly over 1 tick)
                int stepFromStart1 = i - elapsedTicks;
                int stepFromStart2 = i + 1 - elapsedTicks;

                float startFactor1 = MathHelper.clamp((float) stepFromStart1 / startBlendSteps, 0.0f, 1.0f);
                float startFactor2 = MathHelper.clamp((float) stepFromStart2 / startBlendSteps, 0.0f, 1.0f);

                float blend1 = startFactor1 * startFactor1 * (3.0f - 2.0f * startFactor1);
                float blend2 = startFactor2 * startFactor2 * (3.0f - 2.0f * startFactor2);

                // Start at 40% width and 30% alpha at the projectile core, expanding quickly to 100%
                float widthBlend1 = 0.4f + 0.6f * blend1;
                float widthBlend2 = 0.4f + 0.6f * blend2;

                float alphaBlend1 = 0.3f + 0.7f * blend1;
                float alphaBlend2 = 0.3f + 0.7f * blend2;

                // Progress along total trajectory path
                float progress1 = (float) i / totalPathSteps;
                float progress2 = (float) (i + 1) / totalPathSteps;

                // Dynamic target taper: taper slightly inward near impact (last 20% of path) for high landing readability
                float endTaper1 = progress1 > 0.8f ? 1.0f - (progress1 - 0.8f) * 2.0f : 1.0f;
                float endTaper2 = progress2 > 0.8f ? 1.0f - (progress2 - 0.8f) * 2.0f : 1.0f;

                float width1 = baseWidth * widthBlend1 * endTaper1;
                float width2 = baseWidth * widthBlend2 * endTaper2;

                // Pulse factor based on in-game world time
                float pulse1 = config.enableRibbonPulse ? (0.85f + 0.15f * (float) Math.sin(animTime * pulseSpeed - progress1 * 6.0f)) : 1.0f;
                float pulse2 = config.enableRibbonPulse ? (0.85f + 0.15f * (float) Math.sin(animTime * pulseSpeed - progress2 * 6.0f)) : 1.0f;

                // Dashed style mask
                float dash1 = isDashed ? ((i % 3 < 2) ? 1.0f : 0.15f) : 1.0f;
                float dash2 = isDashed ? (((i + 1) % 3 < 2) ? 1.0f : 0.15f) : 1.0f;

                // End alpha matches dome max alpha (approx 60), start base center alpha is 200
                int baseCenterAlpha1 = (int) (200 - (140 * Math.pow(progress1, 2)));
                int baseCenterAlpha2 = (int) (200 - (140 * Math.pow(progress2, 2)));

                int centerAlpha1 = (int) MathHelper.clamp(baseCenterAlpha1 * alphaBlend1 * pulse1 * dash1, 0, 255);
                int centerAlpha2 = (int) MathHelper.clamp(baseCenterAlpha2 * alphaBlend2 * pulse2 * dash2, 0, 255);
                int edgeAlpha = 0;

                // Calculate fail-safe billboarding right direction once per segment
                Vec3d perp = dir.crossProduct(camLook);
                if (perp.lengthSquared() < 0.001) {
                    perp = dir.crossProduct(new Vec3d(0, 1, 0));
                }
                if (perp.lengthSquared() < 0.001) {
                    perp = dir.crossProduct(new Vec3d(1, 0, 0));
                }
                Vec3d rightDir = safeNormalize(perp, new Vec3d(1, 0, 0));

                // Pass 1: Outer Shroud
                if (drawShroud) {
                    Vec3d right1 = rightDir.multiply(width1);
                    Vec3d right2 = rightDir.multiply(width2);

                    Vec3d p1L = p1.add(right1);
                    Vec3d p1R = p1.subtract(right1);
                    Vec3d p2L = p2.add(right2);
                    Vec3d p2R = p2.subtract(right2);

                    consumer.vertex(positionMatrix, (float)p1L.x, (float)p1L.y, (float)p1L.z).color(r, g, b, edgeAlpha);
                    consumer.vertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, centerAlpha1);
                    consumer.vertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, centerAlpha2);
                    consumer.vertex(positionMatrix, (float)p2L.x, (float)p2L.y, (float)p2L.z).color(r, g, b, edgeAlpha);

                    consumer.vertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, centerAlpha1);
                    consumer.vertex(positionMatrix, (float)p1R.x, (float)p1R.y, (float)p1R.z).color(r, g, b, edgeAlpha);
                    consumer.vertex(positionMatrix, (float)p2R.x, (float)p2R.y, (float)p2R.z).color(r, g, b, edgeAlpha);
                    consumer.vertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, centerAlpha2);
                }

                // Pass 2: Inner Core Layer
                if (drawCore) {
                    float coreWidthRatio = isCoreOnly ? 0.6f : 0.35f;
                    float coreWidth1 = width1 * coreWidthRatio;
                    float coreWidth2 = width2 * coreWidthRatio;

                    int pass2R = isCoreOnly ? r : Math.min(255, r + (int)((255 - r) * 0.35f));
                    int pass2G = isCoreOnly ? g : Math.min(255, g + (int)((255 - g) * 0.35f));
                    int pass2B = isCoreOnly ? b : Math.min(255, b + (int)((255 - b) * 0.35f));

                    Vec3d coreRight1 = rightDir.multiply(coreWidth1);
                    Vec3d coreRight2 = rightDir.multiply(coreWidth2);

                    Vec3d cp1L = p1.add(coreRight1);
                    Vec3d cp1R = p1.subtract(coreRight1);
                    Vec3d cp2L = p2.add(coreRight2);
                    Vec3d cp2R = p2.subtract(coreRight2);

                    int coreAlphaCenter1 = isCoreOnly ? centerAlpha1 : MathHelper.clamp((int)(centerAlpha1 * 1.25f), 0, 255);
                    int coreAlphaCenter2 = isCoreOnly ? centerAlpha2 : MathHelper.clamp((int)(centerAlpha2 * 1.25f), 0, 255);
                    int coreAlphaEdge1 = isCoreOnly ? 0 : (int)(centerAlpha1 * 0.4f);
                    int coreAlphaEdge2 = isCoreOnly ? 0 : (int)(centerAlpha2 * 0.4f);

                    consumer.vertex(positionMatrix, (float)cp1L.x, (float)cp1L.y, (float)cp1L.z).color(pass2R, pass2G, pass2B, coreAlphaEdge1);
                    consumer.vertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).color(pass2R, pass2G, pass2B, coreAlphaCenter1);
                    consumer.vertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).color(pass2R, pass2G, pass2B, coreAlphaCenter2);
                    consumer.vertex(positionMatrix, (float)cp2L.x, (float)cp2L.y, (float)cp2L.z).color(pass2R, pass2G, pass2B, coreAlphaEdge2);

                    consumer.vertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).color(pass2R, pass2G, pass2B, coreAlphaCenter1);
                    consumer.vertex(positionMatrix, (float)cp1R.x, (float)cp1R.y, (float)cp1R.z).color(pass2R, pass2G, pass2B, coreAlphaEdge1);
                    consumer.vertex(positionMatrix, (float)cp2R.x, (float)cp2R.y, (float)cp2R.z).color(pass2R, pass2G, pass2B, coreAlphaEdge2);
                    consumer.vertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).color(pass2R, pass2G, pass2B, coreAlphaCenter2);
                }
            }
            matrices.pop();
        }

        // Render Shockwave Dome
        if (config.renderShockwaveDome && data.hitResult != null && data.renderData != null && !data.renderData.domeQuads().isEmpty()) {
            Vec3d hitPos = data.hitResult.getPos();
            VertexConsumer consumer = vertexConsumers.getBuffer(SHOCKWAVE_DOME);
            
            matrices.push();
            matrices.translate(hitPos.x - cameraPos.x, hitPos.y - cameraPos.y, hitPos.z - cameraPos.z);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            
            int r = shockwaveColor.getRed();
            int g = shockwaveColor.getGreen();
            int b = shockwaveColor.getBlue();

            // Calculate pulsing factor over time (2-second duration cycle)
            long time = System.currentTimeMillis();
            double angle = (time % 2000) / 2000.0 * 2.0 * Math.PI;
            float pulseFactor = 0.8f + 0.2f * (float) Math.sin(angle);

            for (PredictionRenderData.DomeQuad quad : data.renderData.domeQuads()) {
                int alpha1 = Math.min(255, Math.max(0, (int) (quad.alpha1() * pulseFactor)));
                int alpha2 = Math.min(255, Math.max(0, (int) (quad.alpha2() * pulseFactor)));
                
                consumer.vertex(positionMatrix, (float) quad.p1().x, (float) quad.p1().y, (float) quad.p1().z).color(r, g, b, alpha1);
                consumer.vertex(positionMatrix, (float) quad.p2().x, (float) quad.p2().y, (float) quad.p2().z).color(r, g, b, alpha1);
                consumer.vertex(positionMatrix, (float) quad.p3().x, (float) quad.p3().y, (float) quad.p3().z).color(r, g, b, alpha2);
                consumer.vertex(positionMatrix, (float) quad.p4().x, (float) quad.p4().y, (float) quad.p4().z).color(r, g, b, alpha2);
            }
            matrices.pop();
        }
    }
}
