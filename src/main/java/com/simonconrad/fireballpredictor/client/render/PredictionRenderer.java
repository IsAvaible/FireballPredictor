package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.math.PredictionData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class PredictionRenderer {

    /**
     * Trail and dome deliberately share ONE RenderType, so they share one buffer and the order in which
     * PredictionFeatureRenderer emits them is the order they are blended in (dome first, trail on top).
     *
     * The pipeline behind it ({@link PredictionPipelines#PREDICTION}) is mod-owned and registered
     * with Iris in {@code IrisCompat}, which is what makes it survive a shader pack. See
     * {@link PredictionPipelines} for the full explanation of the pipeline state.
     */
    static final RenderType PREDICTION_GEOMETRY = RenderType.create(
        "fireballpredictor:prediction",
        RenderSetup.builder(PredictionPipelines.PREDICTION).createRenderSetup()
    );

    /**
     * Alpha ceilings. With alpha blending (instead of an additive blend) the same numeric alpha looks a
     * lot more solid, and the dome is drawn twice per pixel (no back-face culling), so the effective
     * coverage is 1-(1-a)^2. Capping keeps the cracking overlay of covered blocks readable.
     */
    static final int MAX_TRAIL_ALPHA = 190;
    static final int MAX_DOME_ALPHA = 110;

    public static void renderImpactWarningBadge(GuiGraphicsExtractor context, Minecraft client, boolean visible, float progress, WarningProjectileType warningType) {
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

        BadgePosition badge = impactBadgePosition(client);
        int x = badge.x();
        int y = badge.y();

        int size = 20;
        context.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/effect_background"), x, y, size, size);

        WarningProjectileType type = warningType == null ? WarningProjectileType.FIREBALL : warningType;
        if (type.customTexture() != null) {
            try {
                context.blit(RenderPipelines.GUI_TEXTURED, type.customTexture(), x + 2, y + 2, 0, 0, 16, 16, 16, 16, 16, 16);
            } catch (RuntimeException | LinkageError ignored) {
                context.item(type.icon(), x + 2, y + 2);
            }
        } else {
            context.item(type.icon(), x + 2, y + 2);
        }

        int barX = x + 2;
        int barY = y + size - 2;
        int barWidth = 15;
        int barHeight = 1;
        int filledWidth = Math.max(1, Math.round(Mth.clamp(progress, 0.0f, 1.0f) * barWidth));

        context.fill(barX, barY, barX + barWidth, barY + barHeight, type.barBackgroundColor());
        context.fill(barX, barY, barX + filledWidth, barY + barHeight, type.barFillColor());
        context.fill(barX, barY, barX + barWidth, barY + 1, 0x55FFFFFF);
    }

    public record BadgePosition(int x, int y) {}

    /**
     * Top-left corner of the impact warning badge on screen, honouring the configured anchor and
     * X/Y offsets. Shared with the damage/knockback HUD readout so both stay visually aligned.
     */
    public static BadgePosition impactBadgePosition(Minecraft client) {
        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();
        int badgeWidth = 20;
        int badgeHeight = 20;
        int margin = 8;
        int windowWidth = client.getWindow().getGuiScaledWidth();
        int windowHeight = client.getWindow().getGuiScaledHeight();

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

        return new BadgePosition(x, y);
    }


    public static void render(PoseStack matrices, SubmitNodeCollector submitNodeCollector, Camera camera, ClientLevel world, PredictionData data, AbstractHurtingProjectile fireball) {
        if (!(submitNodeCollector instanceof SubmitNodeStorage storage)) {
            return;
        }

        SubmitNodeCollection collection = storage.order(0);

        Minecraft client = Minecraft.getInstance();
        Vec3 cameraPos = camera.position();
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        Vec3 camLook = Vec3.directionFromRotation(pitch, yaw);
        int elapsedTicks = Math.max(0, fireball.tickCount - data.predictionAge());

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();

        boolean isWindCharge = fireball instanceof net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
        java.awt.Color trajectoryColor = isWindCharge ? config.windChargeTrajectoryColor : config.trajectoryColor;
        java.awt.Color shockwaveColor = isWindCharge ? config.windChargeShockwaveColor : config.shockwaveColor;

        float fade = 1.0f;

        TrailRenderState trailState = null;
        if (config.renderTrajectory && data.path() != null && data.path().size() > 1) {
            matrices.pushPose();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f poseMatrix = new Matrix4f(matrices.last().pose());
            matrices.popPose();

            double animTime = (world != null ? world.getGameTime() : 0L) + client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            TrajectoryStyle style = config.trajectoryStyle == null ? TrajectoryStyle.SOLID : config.trajectoryStyle;

            trailState = new TrailRenderState(
                data.path(),
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
        if (config.renderShockwaveDome && data.hitResult() != null && data.renderData() != null && !data.renderData().domeQuads().isEmpty()) {
            Vec3 hitPos = data.hitResult().getLocation();

            matrices.pushPose();
            matrices.translate(hitPos.x - cameraPos.x, hitPos.y - cameraPos.y, hitPos.z - cameraPos.z);
            Matrix4f poseMatrix = new Matrix4f(matrices.last().pose());
            matrices.popPose();

            long time = System.currentTimeMillis();
            double angle = (time % 2000) / 2000.0 * 2.0 * Math.PI;
            float pulseFactor = 0.8f + 0.2f * (float) Math.sin(angle);

            domeState = new DomeRenderState(
                hitPos,
                data.renderData().domeQuads(),
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
            float distSq = (float) camera.position().distanceToSqr(fireball.position());
            PredictionSubmit submit = new PredictionSubmit(distSq, trailState, domeState);
            collection.translucentModels.submit(submit);
        }
    }
}
