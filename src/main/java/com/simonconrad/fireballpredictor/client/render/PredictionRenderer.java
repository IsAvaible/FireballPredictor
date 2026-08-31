package com.simonconrad.fireballpredictor.client.render;

import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.math.PredictionData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class PredictionRenderer {

    /**
     * Trail and dome deliberately share ONE RenderLayer, so they share one buffer and the order in which
     * PredictionFeatureRenderer emits them is the order they are blended in (dome first, trail on top).
     *
     * The layer behind it ({@link PredictionPipelines#PREDICTION}) is mod-owned and mirrors the 26.2
     * prediction pipeline; shader pack compatibility is handled in {@code IrisCompat}.
     * See {@link PredictionPipelines} for the full explanation of the layer state.
     */
    public static final net.minecraft.client.render.RenderLayer PREDICTION_GEOMETRY = PredictionPipelines.PREDICTION;

    /**
     * Alpha ceilings. With alpha blending (instead of an additive blend) the same numeric alpha looks a
     * lot more solid, and the dome is drawn twice per pixel (no back-face culling), so the effective
     * coverage is 1-(1-a)^2. Capping keeps the cracking overlay of covered blocks readable.
     */
    static final int MAX_TRAIL_ALPHA = 190;
    static final int MAX_DOME_ALPHA = 110;

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

        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, net.minecraft.util.Identifier.ofVanilla("textures/gui/sprites/hud/effect_background.png"), x, y, 0.0f, 0.0f, size, size, 20, 20);

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

    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Camera camera, ClientWorld world, PredictionData data, ExplosiveProjectileEntity fireball) {
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPos = camera.getCameraPos();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();
        Vec3d camLook = Vec3d.fromPolar(pitch, yaw);
        int elapsedTicks = Math.max(0, fireball.age - data.predictionAge);

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();

        boolean isWindCharge = fireball instanceof net.minecraft.entity.projectile.AbstractWindChargeEntity;
        java.awt.Color trajectoryColor = isWindCharge ? config.windChargeTrajectoryColor : config.trajectoryColor;
        java.awt.Color shockwaveColor = isWindCharge ? config.windChargeShockwaveColor : config.shockwaveColor;

        float fade = 1.0f;

        TrailRenderState trailState = null;
        if (config.renderTrajectory && data.path != null && data.path.size() > 1) {
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f poseMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
            matrices.pop();

            double animTime = (world != null ? world.getTime() : 0L) + (client.getRenderTickCounter() != null
                    ? client.getRenderTickCounter().getTickProgress(true) : 0.0f);
            TrajectoryStyle style = config.trajectoryStyle == null ? TrajectoryStyle.SOLID : config.trajectoryStyle;

            trailState = new TrailRenderState(
                data.path,
                elapsedTicks,
                config.trajectoryWidth,
                trajectoryColor.getRed(),
                trajectoryColor.getGreen(),
                trajectoryColor.getBlue(),
                camLook,
                poseMatrix,
                style,
                config.renderCoreGlow,
                config.enableRibbonPulse,
                animTime,
                fade
            );
        }

        DomeRenderState domeState = null;
        if (config.renderShockwaveDome && data.hitResult != null && data.renderData != null && !data.renderData.domeQuads().isEmpty()) {
            Vec3d hitPos = data.hitResult.getPos();

            matrices.push();
            matrices.translate(hitPos.x - cameraPos.x, hitPos.y - cameraPos.y, hitPos.z - cameraPos.z);
            Matrix4f poseMatrix = new Matrix4f(matrices.peek().getPositionMatrix());
            matrices.pop();

            long time = System.currentTimeMillis();
            double angle = (time % 2000) / 2000.0 * 2.0 * Math.PI;
            float pulseFactor = 0.8f + 0.2f * (float) Math.sin(angle);

            domeState = new DomeRenderState(
                hitPos,
                data.renderData.domeQuads(),
                shockwaveColor.getRed(),
                shockwaveColor.getGreen(),
                shockwaveColor.getBlue(),
                pulseFactor,
                poseMatrix,
                fade,
                cameraPos,
                config.domeFresnelStrength
            );
        }

        if (trailState != null || domeState != null) {
            float distSq = (float) camera.getCameraPos().squaredDistanceTo(fireball.getEntityPos());
            PredictionSubmit submit = new PredictionSubmit(distSq, trailState, domeState);
            VertexConsumer consumer = vertexConsumers.getBuffer(PREDICTION_GEOMETRY);
            PredictionFeatureRenderer.render(consumer, List.of(submit));
        }
    }
}
