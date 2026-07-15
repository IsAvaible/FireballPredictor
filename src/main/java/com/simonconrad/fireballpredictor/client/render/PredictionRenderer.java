package com.simonconrad.fireballpredictor.client.render;

import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import com.mojang.blaze3d.systems.RenderSystem;
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

    public static void renderImpactWarningBadge(DrawContext context, MinecraftClient client, boolean visible, float progress) {
        if (!visible || client.player == null) {
            return;
        }

        if (client.world == null) {
            return;
        }

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();
        int badgeWidth = 20;
        int badgeHeight = 20;
        int margin = 8;
        int windowWidth = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();
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

        context.drawTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("textures/gui/sprites/hud/effect_background.png"), x, y, 0.0f, 0.0f, size, size, 20, 20);

        context.drawItem(WARNING_ICON, x + 2, y + 2);

        int barX = x + 2;
        int barY = y + size - 2;
        int barWidth = 15;
        int barHeight = 1;
        int filledWidth = Math.max(1, Math.round(MathHelper.clamp(progress, 0.0f, 1.0f) * barWidth));

        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xAA1A0B00);
        context.fill(barX, barY, barX + filledWidth, barY + barHeight, 0xFFE67A00);
        context.fill(barX, barY, barX + barWidth, barY + 1, 0x55FFFFFF);
    }

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Camera camera, ClientWorld world, PredictionData data, ExplosiveProjectileEntity fireball) {
        Vec3d cameraPos = camera.getCameraPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        Vec3d camLook = Vec3d.fromPolar(pitch, yaw);

        int elapsedTicks = Math.max(0, fireball.age - data.predictionAge);

        // Render Trajectory Ribbon
        if (com.simonconrad.fireballpredictor.config.ModConfig.instance().renderTrajectory && data.path != null && data.path.size() > 1) {
            VertexConsumer consumer = vertexConsumers.getBuffer(FIREBALL_TRAIL);
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            
            float width = 0.5f;
            int r = 255;
            int g = 128;
            int b = 0;

            for (int i = elapsedTicks; i < data.path.size() - 1; i++) {
                Vec3d p1 = data.path.get(i);
                Vec3d p2 = data.path.get(i + 1);
                
                Vec3d dir = p2.subtract(p1).normalize();
                Vec3d right = dir.crossProduct(camLook).normalize().multiply(width);
                
                if (right.lengthSquared() < 0.001) {
                    right = new Vec3d(0, 1, 0).crossProduct(dir).normalize().multiply(width);
                }

                Vec3d p1L = p1.add(right);
                Vec3d p1R = p1.subtract(right);
                Vec3d p2L = p2.add(right);
                Vec3d p2R = p2.subtract(right);
                
                // Fade out the trajectory as it approaches the end to blend into the dome
                float progress1 = (float) i / (data.path.size() - 1);
                float progress2 = (float) (i + 1) / (data.path.size() - 1);
                
                // End alpha matches dome max alpha (approx 60), start alpha is 200
                int centerAlpha1 = (int) (200 - (140 * Math.pow(progress1, 2)));
                int centerAlpha2 = (int) (200 - (140 * Math.pow(progress2, 2)));
                int edgeAlpha = 0;

                // Quad 1: Left to Center
                consumer.vertex(positionMatrix, (float)p1L.x, (float)p1L.y, (float)p1L.z).color(r, g, b, edgeAlpha);
                consumer.vertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, centerAlpha1);
                consumer.vertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, centerAlpha2);
                consumer.vertex(positionMatrix, (float)p2L.x, (float)p2L.y, (float)p2L.z).color(r, g, b, edgeAlpha);

                // Quad 2: Center to Right
                consumer.vertex(positionMatrix, (float)p1.x, (float)p1.y, (float)p1.z).color(r, g, b, centerAlpha1);
                consumer.vertex(positionMatrix, (float)p1R.x, (float)p1R.y, (float)p1R.z).color(r, g, b, edgeAlpha);
                consumer.vertex(positionMatrix, (float)p2R.x, (float)p2R.y, (float)p2R.z).color(r, g, b, edgeAlpha);
                consumer.vertex(positionMatrix, (float)p2.x, (float)p2.y, (float)p2.z).color(r, g, b, centerAlpha2);
            }
            matrices.pop();
        }

        // Render Shockwave Dome
        if (com.simonconrad.fireballpredictor.config.ModConfig.instance().renderShockwaveDome && data.hitResult != null && data.renderData != null && !data.renderData.domeQuads().isEmpty()) {
            Vec3d hitPos = data.hitResult.getPos();
            VertexConsumer consumer = vertexConsumers.getBuffer(SHOCKWAVE_DOME);
            
            matrices.push();
            matrices.translate(hitPos.x - cameraPos.x, hitPos.y - cameraPos.y, hitPos.z - cameraPos.z);
            Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
            int r = 255;
            int g = 128;
            int b = 0;

            for (PredictionRenderData.DomeQuad quad : data.renderData.domeQuads()) {
                consumer.vertex(positionMatrix, (float) quad.p1().x, (float) quad.p1().y, (float) quad.p1().z).color(r, g, b, quad.alpha1());
                consumer.vertex(positionMatrix, (float) quad.p2().x, (float) quad.p2().y, (float) quad.p2().z).color(r, g, b, quad.alpha1());
                consumer.vertex(positionMatrix, (float) quad.p3().x, (float) quad.p3().y, (float) quad.p3().z).color(r, g, b, quad.alpha2());
                consumer.vertex(positionMatrix, (float) quad.p4().x, (float) quad.p4().y, (float) quad.p4().z).color(r, g, b, quad.alpha2());
            }
            matrices.pop();
        }
    }
}
