package com.simonconrad.fireballpredictor.render;

import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.math.DomeMesh;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

/**
 * World-space rendering of the predicted trajectory ribbon and the shockwave dome.
 * Called from RenderWorldLastEvent, draws with immediate-mode quads (POSITION_COLOR),
 * additive-free translucent blending and depth write disabled - the 1.8.9 equivalent
 * of the original mod's translucent prediction pipeline.
 */
public final class PredictionRenderer {

    private PredictionRenderer() {
    }

    private static final int MAX_TRAIL_ALPHA = 190;
    private static final int MAX_DOME_ALPHA = 110;

    public static void render(Minecraft mc, Map<Integer, FireballPredictorClient.Tracked> tracked, float partialTicks) {
        if (mc == null || mc.theWorld == null || mc.getRenderViewEntity() == null) {
            return;
        }

        net.minecraft.client.renderer.entity.RenderManager renderManager = mc.getRenderManager();
        final double viewerX = renderManager.viewerPosX;
        final double viewerY = renderManager.viewerPosY;
        final double viewerZ = renderManager.viewerPosZ;

        Entity viewEntity = mc.getRenderViewEntity();
        Vec3 camLook = viewEntity.getLook(partialTicks);
        Vec3 camPos = new Vec3(viewerX, viewerY, viewerZ);

        for (FireballPredictorClient.Tracked t : tracked.values()) {
            if (t.prediction == null) {
                continue;
            }
            double distSq = (t.fireball.posX - viewerX) * (t.fireball.posX - viewerX)
                    + (t.fireball.posY - viewerY) * (t.fireball.posY - viewerY)
                    + (t.fireball.posZ - viewerZ) * (t.fireball.posZ - viewerZ);
            if (distSq > 512.0 * 512.0) {
                continue;
            }

            if (ModConfig.renderTrajectory) {
                renderTrail(mc, t, camLook, viewerX, viewerY, viewerZ);
            }
            if (ModConfig.renderShockwaveDome && t.dome.quadCount > 0 && t.impactPos != null) {
                renderDome(mc, t, camPos, viewerX, viewerY, viewerZ);
            }
        }
    }

    // ------------------------------------------------------------------ trail

    private static void renderTrail(Minecraft mc, FireballPredictorClient.Tracked t,
                                    Vec3 camLook, double viewerX, double viewerY, double viewerZ) {
        EntityFireball fireball = t.fireball;
        List<Vec3> path = t.prediction.path;
        int totalSteps = path.size() - 1;
        if (totalSteps < 1) {
            return;
        }

        int color = ModConfig.trajectoryColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float baseWidth = ModConfig.trajectoryWidth;

        int elapsed = Math.max(0, fireball.ticksExisted - t.predictionAge);

        setupTranslucent();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        for (int i = Math.max(0, elapsed); i < totalSteps; i++) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);

            double dx = p2.xCoord - p1.xCoord;
            double dy = p2.yCoord - p1.yCoord;
            double dz = p2.zCoord - p1.zCoord;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0E-7) {
                continue;
            }
            dx /= len;
            dy /= len;
            dz /= len;

            // Perpendicular to the camera direction (billboard ribbon).
            double px = dy * camLook.zCoord - dz * camLook.yCoord;
            double py = dz * camLook.xCoord - dx * camLook.zCoord;
            double pz = dx * camLook.yCoord - dy * camLook.xCoord;
            double plen = Math.sqrt(px * px + py * py + pz * pz);
            if (plen < 0.001) {
                // Camera looks along the path: fall back to a horizontal perpendicular.
                px = dz;
                py = 0.0;
                pz = -dx;
                plen = Math.sqrt(px * px + py * py + pz * pz);
            }
            if (plen < 0.001) {
                px = 1.0;
                py = 0.0;
                pz = 0.0;
                plen = 1.0;
            }
            px /= plen;
            py /= plen;
            pz /= plen;

            float blend1 = Math.min(1.0F, (float) (i - elapsed));
            float blend2 = Math.min(1.0F, (float) (i + 1 - elapsed));
            float widthBlend1 = 0.4F + 0.6F * blend1;
            float widthBlend2 = 0.4F + 0.6F * blend2;
            float alphaBlend1 = 0.3F + 0.7F * blend1;
            float alphaBlend2 = 0.3F + 0.7F * blend2;

            float progress1 = (float) i / totalSteps;
            float progress2 = (float) (i + 1) / totalSteps;
            float endTaper1 = progress1 > 0.8F ? 1.0F - (progress1 - 0.8F) * 2.0F : 1.0F;
            float endTaper2 = progress2 > 0.8F ? 1.0F - (progress2 - 0.8F) * 2.0F : 1.0F;

            float width1 = baseWidth * widthBlend1 * endTaper1;
            float width2 = baseWidth * widthBlend2 * endTaper2;

            int centerAlpha1 = clampAlpha((int) ((200.0 - 140.0 * progress1 * progress1) * alphaBlend1));
            int centerAlpha2 = clampAlpha((int) ((200.0 - 140.0 * progress2 * progress2) * alphaBlend2));
            int edgeAlpha = 0;

            double r1x = px * width1, r1y = py * width1, r1z = pz * width1;
            double r2x = px * width2, r2y = py * width2, r2z = pz * width2;

            // Two quads: [p1+r1, p1, p2, p2+r2] and [p1, p1-r1, p2-r2, p2]
            // (center vertices get the bright center alpha, outer ones fade to 0).
            wr.pos(p1.xCoord + r1x, p1.yCoord + r1y, p1.zCoord + r1z).color(r, g, b, edgeAlpha).endVertex();
            wr.pos(p1.xCoord, p1.yCoord, p1.zCoord).color(r, g, b, centerAlpha1).endVertex();
            wr.pos(p2.xCoord, p2.yCoord, p2.zCoord).color(r, g, b, centerAlpha2).endVertex();
            wr.pos(p2.xCoord + r2x, p2.yCoord + r2y, p2.zCoord + r2z).color(r, g, b, edgeAlpha).endVertex();

            wr.pos(p1.xCoord, p1.yCoord, p1.zCoord).color(r, g, b, centerAlpha1).endVertex();
            wr.pos(p1.xCoord - r1x, p1.yCoord - r1y, p1.zCoord - r1z).color(r, g, b, edgeAlpha).endVertex();
            wr.pos(p2.xCoord - r2x, p2.yCoord - r2y, p2.zCoord - r2z).color(r, g, b, edgeAlpha).endVertex();
            wr.pos(p2.xCoord, p2.yCoord, p2.zCoord).color(r, g, b, centerAlpha2).endVertex();
        }

        tessellator.draw();
        restoreTranslucent();
    }

    // ------------------------------------------------------------------- dome

    private static void renderDome(Minecraft mc, FireballPredictorClient.Tracked t,
                                   Vec3 camPos, double viewerX, double viewerY, double viewerZ) {
        DomeMesh mesh = t.dome;
        int color = ModConfig.domeColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        double cx = t.impactPos.xCoord;
        double cy = t.impactPos.yCoord;
        double cz = t.impactPos.zCoord;

        setupTranslucent();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        float[] vertices = mesh.vertices;
        float[] alphas = mesh.alphas;

        for (int q = 0; q < mesh.quadCount; q++) {
            int base = q * 12;
            int abase = q * 4;

            for (int v = 0; v < 4; v++) {
                int off = base + v * 3;
                float vx = vertices[off];
                float vy = vertices[off + 1];
                float vz = vertices[off + 2];

                // Fresnel-style rim: quads facing away from the camera get boosted alpha.
                double wx = cx + vx, wy = cy + vy, wz = cz + vz;
                double nl = Math.sqrt((double) vx * vx + (double) vy * vy + (double) vz * vz);
                double ndx = vx / nl, ndy = vy / nl, ndz = vz / nl;
                double vdx = wx - camPos.xCoord, vdy = wy - camPos.yCoord, vdz = wz - camPos.zCoord;
                double vdl = Math.sqrt(vdx * vdx + vdy * vdy + vdz * vdz);
                if (vdl < 1.0E-4) {
                    vdl = 1.0;
                }
                double ndv = (ndx * vdx + ndy * vdy + ndz * vdz) / vdl;
                if (ndv < 0.0) {
                    ndv = -ndv;
                }
                double fresnel = 0.35 + 0.65 * (1.0 - ndv) * (1.0 - ndv);
                int alpha = (int) Math.min(MAX_DOME_ALPHA, alphas[abase + v] * fresnel);
                if (alpha < 0) {
                    alpha = 0;
                }

                wr.pos(cx + vx, cy + vy, cz + vz).color(r, g, b, alpha).endVertex();
            }
        }

        tessellator.draw();
        restoreTranslucent();
    }

    // ---------------------------------------------------------------- gl state

    private static void setupTranslucent() {
        GlStateManager.pushMatrix();
        net.minecraft.client.renderer.entity.RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        GlStateManager.translate(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
    }

    private static void restoreTranslucent() {
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static int clampAlpha(int alpha) {
        if (alpha < 0) {
            return 0;
        }
        return Math.min(alpha, MAX_TRAIL_ALPHA);
    }
}
