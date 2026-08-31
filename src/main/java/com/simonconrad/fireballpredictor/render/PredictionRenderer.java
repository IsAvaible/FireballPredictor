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
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Map;

/**
 * World-space rendering of the predicted trajectory ribbon and the shockwave dome.
 * Called from RenderWorldLastEvent, draws with immediate-mode quads (POSITION_COLOR),
 * additive-free translucent blending and depth write disabled - the 1.8.9 equivalent
 * of the original mod's translucent prediction pipeline.
 *
 * <p>Like master's PredictionFeatureRenderer, domes are emitted before trails into the
 * same translucent state, so ribbons blend on top of the blast spheres.
 */
public final class PredictionRenderer {

    private PredictionRenderer() {
    }

    private static final int MAX_TRAIL_ALPHA = 190;
    private static final int MAX_DOME_ALPHA = 110;

    /**
     * Fresnel rim parameters for the shockwave dome (Schlick approximation), identical
     * to master: F = F0 + (1 - F0) * (1 - |dot(N, V)|)^5, plus a fixed rim glow that
     * keeps the silhouette (and with culling disabled the inner/far side of the shell)
     * readable even where the latitude alpha profile fades to zero.
     */
    private static final float FRESNEL_F0 = 0.04F;
    private static final int FRESNEL_RIM_GLOW = 55;

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

        // Game-time driven animation clock (seconds); pauses with the game like master.
        double animSeconds = (mc.theWorld.getTotalWorldTime() + partialTicks) / 20.0;
        float domePulse = computePulseFactor(animSeconds);

        // Pass 1: shockwave domes (drawn first so trajectories stay readable on top).
        if (ModConfig.renderShockwaveDome) {
            for (FireballPredictorClient.Tracked t : tracked.values()) {
                if (t.prediction != null && t.dome.quadCount > 0 && t.impactPos != null) {
                    renderDome(t, camPos, domePulse);
                }
            }
        }

        // Pass 2: trajectory ribbons.
        if (ModConfig.renderTrajectory) {
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
                renderTrail(t, camLook, viewerX, viewerY, viewerZ, animSeconds);
            }
        }
    }

    /**
     * Dome breathing pulse at 0.5 Hz (period 2 s), same rate and curve as master's
     * {@code VisualTheme.computePulseFactor}: 0.8 + 0.2 * sin(2*pi*t / 2s).
     */
    private static float computePulseFactor(double animSeconds) {
        if (animSeconds <= 0.0) {
            return 1.0F;
        }
        double angle = animSeconds * Math.PI; // 2*PI per 2 s
        return 0.8F + 0.2F * (float) Math.sin(angle);
    }

    // ------------------------------------------------------------------ trail

    private static void renderTrail(FireballPredictorClient.Tracked t,
                                    Vec3 camLook, double viewerX, double viewerY, double viewerZ,
                                    double animSeconds) {
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

    private static void renderDome(FireballPredictorClient.Tracked t, Vec3 camPos, float pulseFactor) {
        DomeMesh mesh = t.dome;
        int color = ModConfig.domeColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        double cx = t.impactPos.xCoord;
        double cy = t.impactPos.yCoord;
        double cz = t.impactPos.zCoord;

        // Camera position relative to the dome centre (dome space: centre at origin).
        double camLocalX = camPos.xCoord - cx;
        double camLocalY = camPos.yCoord - cy;
        double camLocalZ = camPos.zCoord - cz;

        float strength = MathHelper.clamp_float(ModConfig.domeFresnelStrength, 0.0F, 1.0F);
        float fade = 1.0F;
        int maxAlpha = Math.round(MAX_DOME_ALPHA * fade);

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

                // Profile alpha: latitude shading * breathing pulse (master's base term).
                int profileAlpha = (int) (alphas[abase + v] * pulseFactor * fade);
                if (profileAlpha < 0) {
                    profileAlpha = 0;
                }
                if (profileAlpha > maxAlpha) {
                    profileAlpha = maxAlpha;
                }

                int alpha = fresnelAlpha(vx, vy, vz, profileAlpha,
                        camLocalX, camLocalY, camLocalZ, strength, maxAlpha, fade);

                wr.pos(cx + vx, cy + vy, cz + vz).color(r, g, b, alpha).endVertex();
            }
        }

        tessellator.draw();
        restoreTranslucent();
    }

    /**
     * Bakes the Schlick fresnel term for a single dome vertex into an alpha value
     * (port of master's PredictionFeatureRenderer.fresnelAlpha):
     *
     * <pre>
     *   F = F0 + (1 - F0) * (1 - dot(N, V))^5
     * </pre>
     *
     * <p>Surface patches facing the camera become transparent while the silhouette rim
     * (grazing angle) is pushed toward the alpha ceiling. Because culling is disabled,
     * the far side of the sphere also receives the full rim term, which reads as the
     * bright shell of the blast when the camera is INSIDE the dome - the backport
     * previously multiplied the latitude profile by a plain fresnel factor without the
     * rim glow, leaving the dome effectively invisible from the inside.
     *
     * @param vx          dome-space vertex position (dome centre at origin; normal = vertex direction)
     * @param base        profile alpha (latitude shading, pulse and fade already applied)
     * @param camLocalXzy camera position relative to the dome centre
     * @param strength    config strength: 0 keeps the legacy latitude profile, 1 applies full fresnel
     * @param maxAlpha    alpha ceiling
     * @param fade        global fade factor
     */
    private static int fresnelAlpha(float vx, float vy, float vz, int base,
                                    double camLocalX, double camLocalY, double camLocalZ,
                                    float strength, int maxAlpha, float fade) {
        if (strength <= 0.0F) {
            return base;
        }

        double nLenSq = (double) (vx * vx) + (double) (vy * vy) + (double) (vz * vz);
        double invNLen = nLenSq > 1.0E-7 ? 1.0 / Math.sqrt(nLenSq) : 0.0;
        double nx = invNLen != 0.0 ? vx * invNLen : 0.0;
        double ny = invNLen != 0.0 ? vy * invNLen : 1.0;
        double nz = invNLen != 0.0 ? vz * invNLen : 0.0;

        double dx = camLocalX - vx;
        double dy = camLocalY - vy;
        double dz = camLocalZ - vz;
        double vLenSq = dx * dx + dy * dy + dz * dz;
        double invVLen = vLenSq > 1.0E-7 ? 1.0 / Math.sqrt(vLenSq) : 0.0;
        double viewX = invVLen != 0.0 ? dx * invVLen : 0.0;
        double viewY = invVLen != 0.0 ? dy * invVLen : 1.0;
        double viewZ = invVLen != 0.0 ? dz * invVLen : 0.0;

        double dot = nx * viewX + ny * viewY + nz * viewZ;
        float ndv = (float) Math.max(0.0, Math.abs(dot));
        // Schlick: F = F0 + (1 - F0) * (1 - ndv)^5 (expanded below).
        float tt = 1.0F - ndv;
        float fresnel = FRESNEL_F0 + (1.0F - FRESNEL_F0) * tt * tt * tt * tt * tt;

        // Blend between the legacy profile (strength 0) and pure fresnel shading (strength 1),
        // then add a fixed rim glow so the silhouette reads even where the latitude profile is 0.
        float alpha = base * (1.0F - strength + strength * fresnel)
                + FRESNEL_RIM_GLOW * fade * strength * fresnel;
        if (alpha < 0.0F) {
            return 0;
        }
        if (alpha > (float) maxAlpha) {
            return maxAlpha;
        }
        return (int) alpha;
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
