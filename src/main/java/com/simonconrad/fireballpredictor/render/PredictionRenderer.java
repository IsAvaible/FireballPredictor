package com.simonconrad.fireballpredictor.render;

import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.math.DomeMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
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
        Vec3 camPos = new Vec3(viewerX, viewerY, viewerZ);

        // Game-time driven animation clock (seconds); pauses with the game like master.
        double animSeconds = (mc.theWorld.getTotalWorldTime() + partialTicks) / 20.0;

        // Pass 1: shockwave domes (drawn first so trajectories stay readable on top).
        if (ModConfig.renderShockwaveDome) {
            for (FireballPredictorClient.Tracked t : tracked.values()) {
                if (t.prediction != null && t.dome.quadCount > 0 && t.impactPos != null) {
                    renderDome(t, camPos);
                }
            }
        }

        // Pass 2: trajectory ribbons.
        if (ModConfig.renderTrajectory) {
            for (FireballPredictorClient.Tracked t : tracked.values()) {
                if (t.prediction == null || !t.anchorValid) {
                    continue;
                }
                double distSq = (t.anchorX - viewerX) * (t.anchorX - viewerX)
                        + (t.anchorY - viewerY) * (t.anchorY - viewerY)
                        + (t.anchorZ - viewerZ) * (t.anchorZ - viewerZ);
                if (distSq > 512.0 * 512.0) {
                    continue;
                }
                renderTrail(t, animSeconds);
            }
        }
    }

    // ------------------------------------------------------------------ trail

    private static final float[] COS8 = new float[8];
    private static final float[] SIN8 = new float[8];

    static {
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4.0;
            COS8[i] = (float) Math.cos(angle);
            SIN8[i] = (float) Math.sin(angle);
        }
    }

    private static final class BeamNode {
        double x, y, z;
        double ux, uy, uz; // orthonormal basis vector U
        double wx, wy, wz; // orthonormal basis vector W
        float radius;
        int shroudAlpha;
        int coreAlpha;
    }

    /**
     * Renders the predicted trajectory as a 3D volumetric cylindrical energy beam:
     *
     * <ul>
     *   <li><b>3D Cylindrical Geometry (8-sided)</b> - eliminates 2D flat-tape billboard artifacts,
     *       maintaining consistent round volume and thickness from all viewing angles and elevations.</li>
     *   <li><b>Rotation-Minimizing Frame (RMF)</b> - parallel transport ensures zero twisting along
     *       curved flight paths.</li>
     *   <li><b>Catmull-Rom Spline Sub-stepping</b> - smooth continuous curvature along the flight path.</li>
     *   <li><b>Two-Pass Volumetric Shading</b> - soft outer shroud cylinder envelope plus a concentrated
     *       inner core beam.</li>
     * </ul>
     */
    private static void renderTrail(FireballPredictorClient.Tracked t, double animSeconds) {
        EntityFireball fireball = t.fireball;
        List<Vec3> path = t.prediction.path;
        int totalSteps = path.size() - 1;
        if (totalSteps < 1) {
            return;
        }

        int elapsed = Math.max(0, fireball.ticksExisted - t.predictionAge);
        if (elapsed >= totalSteps) {
            return;
        }

        int color = ModConfig.trajectoryColor;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float baseWidth = ModConfig.trajectoryWidth;

        TrajectoryStyle style = ModConfig.trajectoryStyle == null ? TrajectoryStyle.SOLID : ModConfig.trajectoryStyle;
        boolean isCoreOnly = style == TrajectoryStyle.CORE_ONLY;
        boolean drawCore = ModConfig.renderCoreGlow || isCoreOnly;
        boolean drawShroud = !isCoreOnly;

        double pulseSpeed = 0.45 * 20.0;

        // 1. Generate smooth spline samples along the trajectory path.
        List<BeamNode> nodes = new ArrayList<BeamNode>();

        for (int i = elapsed; i < totalSteps; i++) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);

            Vec3 p0 = i > 0 ? path.get(i - 1)
                    : new Vec3(2.0 * p1.xCoord - p2.xCoord, 2.0 * p1.yCoord - p2.yCoord, 2.0 * p1.zCoord - p2.zCoord);
            Vec3 p3 = (i + 2 <= totalSteps) ? path.get(i + 2)
                    : new Vec3(2.0 * p2.xCoord - p1.xCoord, 2.0 * p2.yCoord - p1.yCoord, 2.0 * p2.zCoord - p1.zCoord);

            double dx = p2.xCoord - p1.xCoord;
            double dy = p2.yCoord - p1.yCoord;
            double dz = p2.zCoord - p1.zCoord;
            double segDist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            int subSteps = Math.max(2, Math.min(8, (int) Math.ceil(segDist / 0.20)));
            boolean isLastSeg = (i == totalSteps - 1);
            int maxS = isLastSeg ? subSteps : subSteps - 1;

            for (int s = 0; s <= maxS; s++) {
                double u = (double) s / (double) subSteps;
                double u2 = u * u;
                double u3 = u2 * u;

                double sx = catmullRom(p0.xCoord, p1.xCoord, p2.xCoord, p3.xCoord, u, u2, u3);
                double sy = catmullRom(p0.yCoord, p1.yCoord, p2.yCoord, p3.yCoord, u, u2, u3);
                double sz = catmullRom(p0.zCoord, p1.zCoord, p2.zCoord, p3.zCoord, u, u2, u3);

                float stepFraction = (float) (i - elapsed + u);
                float progress = Math.min(1.0F, (float) (i + u) / (float) totalSteps);

                float blend = Math.min(1.0F, stepFraction);
                float widthBlend = 0.4F + 0.6F * blend;
                float alphaBlend = 0.3F + 0.7F * blend;
                float endTaper = progress > 0.8F ? 1.0F - (progress - 0.8F) * 2.0F : 1.0F;
                if (endTaper < 0.0F) {
                    endTaper = 0.0F;
                }

                float radius = (baseWidth * 0.5F) * widthBlend * endTaper;
                float pulse = ModConfig.enableRibbonPulse
                        ? 0.85F + 0.15F * (float) Math.sin(animSeconds * pulseSpeed - progress * 6.0F)
                        : 1.0F;

                int baseShroudAlpha = (int) (105.0F - 60.0F * progress * progress);
                int shroudAlpha = MathHelper.clamp_int((int) (baseShroudAlpha * alphaBlend * pulse), 0, 105);

                int baseCoreAlpha = (int) (185.0F - 100.0F * progress * progress);
                int coreAlpha = MathHelper.clamp_int((int) (baseCoreAlpha * alphaBlend * pulse), 0, 185);

                BeamNode node = new BeamNode();
                node.x = sx;
                node.y = sy;
                node.z = sz;
                node.radius = radius;
                node.shroudAlpha = shroudAlpha;
                node.coreAlpha = coreAlpha;
                nodes.add(node);
            }
        }

        int nodeCount = nodes.size();
        if (nodeCount < 2) {
            return;
        }

        // 2. Compute Rotation-Minimizing Frame (RMF) along the beam.
        for (int m = 0; m < nodeCount; m++) {
            BeamNode cur = nodes.get(m);
            double tx, ty, tz;
            if (m == 0) {
                BeamNode next = nodes.get(1);
                tx = next.x - cur.x;
                ty = next.y - cur.y;
                tz = next.z - cur.z;
            } else if (m == nodeCount - 1) {
                BeamNode prev = nodes.get(m - 1);
                tx = cur.x - prev.x;
                ty = cur.y - prev.y;
                tz = cur.z - prev.z;
            } else {
                BeamNode next = nodes.get(m + 1);
                BeamNode prev = nodes.get(m - 1);
                tx = next.x - prev.x;
                ty = next.y - prev.y;
                tz = next.z - prev.z;
            }

            double tLen = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tLen > 1.0E-7) {
                tx /= tLen;
                ty /= tLen;
                tz /= tLen;
            } else {
                tx = 1.0;
                ty = 0.0;
                tz = 0.0;
            }

            if (m == 0) {
                // Initialize frame with a stable non-parallel vector
                double ax = Math.abs(ty) > 0.9 ? 1.0 : 0.0;
                double ay = Math.abs(ty) > 0.9 ? 0.0 : 1.0;
                double az = 0.0;

                double ux = ty * az - tz * ay;
                double uy = tz * ax - tx * az;
                double uz = tx * ay - ty * ax;
                double uLen = Math.sqrt(ux * ux + uy * uy + uz * uz);
                if (uLen > 1.0E-7) {
                    ux /= uLen;
                    uy /= uLen;
                    uz /= uLen;
                } else {
                    ux = 1.0;
                    uy = 0.0;
                    uz = 0.0;
                }

                cur.ux = ux;
                cur.uy = uy;
                cur.uz = uz;
                cur.wx = ty * uz - tz * uy;
                cur.wy = tz * ux - tx * uz;
                cur.wz = tx * uy - ty * ux;
            } else {
                BeamNode prev = nodes.get(m - 1);
                // Parallel transport: project previous U onto current normal plane
                double dot = prev.ux * tx + prev.uy * ty + prev.uz * tz;
                double ux = prev.ux - dot * tx;
                double uy = prev.uy - dot * ty;
                double uz = prev.uz - dot * tz;
                double uLen = Math.sqrt(ux * ux + uy * uy + uz * uz);
                if (uLen > 1.0E-7) {
                    ux /= uLen;
                    uy /= uLen;
                    uz /= uLen;
                } else {
                    ux = prev.ux;
                    uy = prev.uy;
                    uz = prev.uz;
                }

                cur.ux = ux;
                cur.uy = uy;
                cur.uz = uz;
                cur.wx = ty * uz - tz * uy;
                cur.wy = tz * ux - tx * uz;
                cur.wz = tx * uy - ty * ux;
            }
        }

        // 3. Emit 3D cylinder quads into the batch.
        setupTranslucent();
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        if (drawShroud) {
            renderCylinder(wr, nodes, r, g, b, 1.0F, false);
        }
        if (drawCore) {
            float coreRatio = isCoreOnly ? 0.6F : 0.35F;
            renderCylinder(wr, nodes, r, g, b, coreRatio, true);
        }

        tessellator.draw();
        restoreTranslucent();
    }

    private static double catmullRom(double p0, double p1, double p2, double p3, double u, double u2, double u3) {
        return 0.5 * ((2.0 * p1) + (-p0 + p2) * u
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * u2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * u3);
    }

    private static void renderCylinder(WorldRenderer wr, List<BeamNode> nodes, int r, int g, int b,
                                       float radiusScale, boolean useCoreAlpha) {
        for (int m = 0; m < nodes.size() - 1; m++) {
            BeamNode n1 = nodes.get(m);
            BeamNode n2 = nodes.get(m + 1);

            double rad1 = n1.radius * radiusScale;
            double rad2 = n2.radius * radiusScale;
            int alpha = useCoreAlpha ? n1.coreAlpha : n1.shroudAlpha;

            for (int k = 0; k < 8; k++) {
                int k2 = (k + 1) % 8;

                double o1x_a = (COS8[k] * n1.ux + SIN8[k] * n1.wx) * rad1;
                double o1y_a = (COS8[k] * n1.uy + SIN8[k] * n1.wy) * rad1;
                double o1z_a = (COS8[k] * n1.uz + SIN8[k] * n1.wz) * rad1;

                double o1x_b = (COS8[k2] * n1.ux + SIN8[k2] * n1.wx) * rad1;
                double o1y_b = (COS8[k2] * n1.uy + SIN8[k2] * n1.wy) * rad1;
                double o1z_b = (COS8[k2] * n1.uz + SIN8[k2] * n1.wz) * rad1;

                double o2x_b = (COS8[k2] * n2.ux + SIN8[k2] * n2.wx) * rad2;
                double o2y_b = (COS8[k2] * n2.uy + SIN8[k2] * n2.wy) * rad2;
                double o2z_b = (COS8[k2] * n2.uz + SIN8[k2] * n2.wz) * rad2;

                double o2x_a = (COS8[k] * n2.ux + SIN8[k] * n2.wx) * rad2;
                double o2y_a = (COS8[k] * n2.uy + SIN8[k] * n2.wy) * rad2;
                double o2z_a = (COS8[k] * n2.uz + SIN8[k] * n2.wz) * rad2;

                wr.pos(n1.x + o1x_a, n1.y + o1y_a, n1.z + o1z_a).color(r, g, b, alpha).endVertex();
                wr.pos(n1.x + o1x_b, n1.y + o1y_b, n1.z + o1z_b).color(r, g, b, alpha).endVertex();
                wr.pos(n2.x + o2x_b, n2.y + o2y_b, n2.z + o2z_b).color(r, g, b, alpha).endVertex();
                wr.pos(n2.x + o2x_a, n2.y + o2y_a, n2.z + o2z_a).color(r, g, b, alpha).endVertex();
            }
        }
    }

    // ------------------------------------------------------------------- dome

    private static void renderDome(FireballPredictorClient.Tracked t, Vec3 camPos) {
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

        double camDistSq = camLocalX * camLocalX + camLocalY * camLocalY + camLocalZ * camLocalZ;
        boolean isInside = camDistSq < (double) (mesh.radius * mesh.radius);

        float strength = MathHelper.clamp_float(ModConfig.domeFresnelStrength, 0.0F, 1.0F);

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

                int profileAlpha = MathHelper.clamp_int((int) alphas[abase + v], 0, MAX_DOME_ALPHA);
                int alpha = fresnelAlpha(vx, vy, vz, profileAlpha, camLocalX, camLocalY, camLocalZ, strength, isInside);

                wr.pos(cx + vx, cy + vy, cz + vz).color(r, g, b, alpha).endVertex();
            }
        }

        tessellator.draw();
        restoreTranslucent();
    }

    /**
     * Bakes the Schlick fresnel term for a single dome vertex into an alpha value:
     *
     * <pre>
     *   F = F0 + (1 - F0) * (1 - |dot(N, V)|)^5
     * </pre>
     *
     * <p>When outside the sphere, surface patches facing the camera become translucent while the
     * silhouette rim glows brightly. When inside the sphere, an ambient shell floor is preserved
     * so that the ceiling (upper third) and walls remain clearly visible.
     */
    private static int fresnelAlpha(float vx, float vy, float vz, int base,
                                    double camLocalX, double camLocalY, double camLocalZ,
                                    float strength, boolean isInside) {
        if (strength <= 0.0F) {
            return base;
        }

        double nLenSq = (double) (vx * vx + vy * vy + vz * vz);
        double invNLen = nLenSq > 1.0E-7 ? 1.0 / Math.sqrt(nLenSq) : 0.0;
        double nx = vx * invNLen;
        double ny = vy * invNLen;
        double nz = vz * invNLen;

        double dx = camLocalX - vx;
        double dy = camLocalY - vy;
        double dz = camLocalZ - vz;
        double vLenSq = dx * dx + dy * dy + dz * dz;
        double invVLen = vLenSq > 1.0E-7 ? 1.0 / Math.sqrt(vLenSq) : 0.0;
        double viewX = dx * invVLen;
        double viewY = dy * invVLen;
        double viewZ = dz * invVLen;

        double dot = nx * viewX + ny * viewY + nz * viewZ;
        float ndv = (float) Math.max(0.0, Math.abs(dot));
        float tt = 1.0F - ndv;
        float fresnel = FRESNEL_F0 + (1.0F - FRESNEL_F0) * tt * tt * tt * tt * tt;

        float factor = isInside ? (0.45F + 0.55F * fresnel) : fresnel;
        float alpha = base * (1.0F - strength + strength * factor) + FRESNEL_RIM_GLOW * strength * factor;

        return MathHelper.clamp_int((int) alpha, 0, MAX_DOME_ALPHA);
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

}
