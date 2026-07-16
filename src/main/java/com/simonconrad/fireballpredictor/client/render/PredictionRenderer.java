package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class PredictionRenderer {

    private static final RenderType FIREBALL_TRAIL = RenderType.create(
        "fireball_trail",
        RenderSetup.builder(RenderPipelines.DEBUG_QUADS).createRenderSetup()
    );
    private static final RenderType SHOCKWAVE_DOME = RenderType.create(
        "shockwave_dome",
        RenderSetup.builder(RenderPipelines.DEBUG_QUADS).createRenderSetup()
    );
    
    private static final ItemStack WARNING_ICON = new ItemStack(Items.FIRE_CHARGE);

    public static void renderImpactWarningBadge(GuiGraphicsExtractor context, Minecraft client, boolean visible, float progress) {
        if (!visible || client.player == null) {
            return;
        }

        if (client.level == null) {
            return;
        }

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();
        if (!config.renderImpactWarning) {
            return;
        }
        int badgeWidth = 20;
        int badgeHeight = 20;
        int margin = 8;
        int windowWidth = client.getWindow().getGuiScaledWidth();
        int windowHeight = client.getWindow().getGuiScaledHeight();
        String anchor = config.impactWarningBadgeAnchor == null ? "topleft" : config.impactWarningBadgeAnchor.trim().toLowerCase(java.util.Locale.ROOT);

        int x = switch (anchor) {
            case "topright", "bottomright" -> windowWidth - badgeWidth - margin;
            case "topcenter", "bottomcenter" -> (windowWidth - badgeWidth) / 2;
            default -> margin;
        } + config.impactWarningBadgeOffsetX;

        int y = switch (anchor) {
            case "bottomleft", "bottomcenter", "bottomright" -> windowHeight - badgeHeight - margin;
            default -> margin;
        } + config.impactWarningBadgeOffsetY;

        int size = 20;

        context.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/effect_background"), x, y, size, size);

        context.item(WARNING_ICON, x + 2, y + 2);

        int barX = x + 2;
        int barY = y + size - 2;
        int barWidth = 15;
        int barHeight = 1;
        int filledWidth = Math.max(1, Math.round(Mth.clamp(progress, 0.0f, 1.0f) * barWidth));

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xAA1A0B00);
        context.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFFE67A00);
        context.fill(barX, barY, barX + barWidth, barY + 1, 0x55FFFFFF);
    }

    public static void render(PoseStack matrices, SubmitNodeCollector submitNodeCollector, Camera camera, ClientLevel world, PredictionData data, AbstractHurtingProjectile fireball) {
        Vec3 cameraPos = camera.position();
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        Vec3 camLook = Vec3.directionFromRotation(pitch, yaw);

        int elapsedTicks = Math.max(0, fireball.tickCount - data.predictionAge);

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();

        // Render Trajectory Ribbon
        if (config.renderTrajectory && data.path != null && data.path.size() > 1) {
            matrices.pushPose();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            submitNodeCollector.submitCustomGeometry(matrices, FIREBALL_TRAIL, (pose, consumer) -> {
                Matrix4f positionMatrix = pose.pose();
                
                float width = config.trajectoryWidth;
                int r = config.trajectoryColor.getRed();
                int g = config.trajectoryColor.getGreen();
                int b = config.trajectoryColor.getBlue();

                for (int i = elapsedTicks; i < data.path.size() - 1; i++) {
                    Vec3 p1 = data.path.get(i);
                    Vec3 p2 = data.path.get(i + 1);
                    
                    Vec3 dir = p2.subtract(p1).normalize();
                    Vec3 right = dir.cross(camLook).normalize().scale(width);
                    
                    if (right.lengthSqr() < 0.001) {
                        right = new Vec3(0, 1, 0).cross(dir).normalize().scale(width);
                    }

                    Vec3 p1L = p1.add(right);
                    Vec3 p1R = p1.subtract(right);
                    Vec3 p2L = p2.add(right);
                    Vec3 p2R = p2.subtract(right);
                    
                    float progress1 = (float) i / (data.path.size() - 1);
                    float progress2 = (float) (i + 1) / (data.path.size() - 1);
                    
                    int centerAlpha1 = (int) (200 - (140 * Math.pow(progress1, 2)));
                    int centerAlpha2 = (int) (200 - (140 * Math.pow(progress2, 2)));
                    int edgeAlpha = 0;

                    // Quad 1: Left to Center
                    consumer.addVertex(positionMatrix, (float)p1L.x, (float)p1L.y, (float)p1L.z).setColor(r, g, b, edgeAlpha);
                    consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(r, g, b, centerAlpha1);
                    consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(r, g, b, centerAlpha2);
                    consumer.addVertex(positionMatrix, (float)p2L.x, (float)p2L.y, (float)p2L.z).setColor(r, g, b, edgeAlpha);

                    // Quad 2: Center to Right
                    consumer.addVertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(r, g, b, centerAlpha1);
                    consumer.addVertex(positionMatrix, (float)p1R.x, (float)p1R.y, (float)p1R.z).setColor(r, g, b, edgeAlpha);
                    consumer.addVertex(positionMatrix, (float)p2R.x, (float)p2R.y, (float)p2R.z).setColor(r, g, b, edgeAlpha);
                    consumer.addVertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(r, g, b, centerAlpha2);
                }
            });
            matrices.popPose();
        }

        // Render Shockwave Dome
        if (config.renderShockwaveDome && data.hitResult != null && data.renderData != null && !data.renderData.domeQuads().isEmpty()) {
            Vec3 hitPos = data.hitResult.getLocation();
            
            matrices.pushPose();
            matrices.translate(hitPos.x - cameraPos.x, hitPos.y - cameraPos.y, hitPos.z - cameraPos.z);
            submitNodeCollector.submitCustomGeometry(matrices, SHOCKWAVE_DOME, (pose, consumer) -> {
                Matrix4f positionMatrix = pose.pose();
                
                int r = config.shockwaveColor.getRed();
                int g = config.shockwaveColor.getGreen();
                int b = config.shockwaveColor.getBlue();

                // Calculate pulsing factor over time (2-second duration cycle)
                long time = System.currentTimeMillis();
                double angle = (time % 2000) / 2000.0 * 2.0 * Math.PI;
                float pulseFactor = 0.8f + 0.2f * (float) Math.sin(angle);

                for (PredictionRenderData.DomeQuad quad : data.renderData.domeQuads()) {
                    int alpha1 = Math.min(255, Math.max(0, (int) (quad.alpha1() * pulseFactor)));
                    int alpha2 = Math.min(255, Math.max(0, (int) (quad.alpha2() * pulseFactor)));
                    
                    consumer.addVertex(positionMatrix, (float) quad.p1().x, (float) quad.p1().y, (float) quad.p1().z).setColor(r, g, b, alpha1);
                    consumer.addVertex(positionMatrix, (float) quad.p2().x, (float) quad.p2().y, (float) quad.p2().z).setColor(r, g, b, alpha1);
                    consumer.addVertex(positionMatrix, (float) quad.p3().x, (float) quad.p3().y, (float) quad.p3().z).setColor(r, g, b, alpha2);
                    consumer.addVertex(positionMatrix, (float) quad.p4().x, (float) quad.p4().y, (float) quad.p4().z).setColor(r, g, b, alpha2);
                }
            });
            matrices.popPose();
        }
    }
}
