package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.math.PredictionData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import com.simonconrad.fireballpredictor.projectile.WarningProjectileType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

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
        com.simonconrad.fireballpredictor.config.VisualTheme theme = config.getThemeFor(fireball);
        float animSpeed = config.themeAnimationSpeed;

        java.awt.Color trajectoryColor = config.getTrajectoryColorFor(fireball);
        java.awt.Color shockwaveColor = config.getShockwaveColorFor(fireball);

        float fade = 1.0f;
        Matrix4f basePose = matrices.last().pose();
        // Theme animations run on game time (seconds) scaled by themeAnimationSpeed; game time is
        // used (not wall clock) so animations pause with the game (pause menu / single-player).
        double gameSeconds = ((world != null ? world.getGameTime() : 0L)
                + client.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0;
        double animSeconds = gameSeconds * Math.max(0.0f, animSpeed);

        TrailRenderState trailState = null;
        if (config.renderTrajectory && data.path() != null && data.path().size() > 1) {
            Matrix4f poseMatrix = new Matrix4f(basePose).translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);
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
                animSeconds,
                fade,
                theme
            );
        }

        DomeRenderState domeState = null;
        float domeRadius = 0.0f;
        if (config.renderShockwaveDome && data.hitResult() != null && data.renderData() != null && !data.renderData().domeQuads().isEmpty()) {
            Vec3 hitPos = data.hitResult().getLocation();
            Matrix4f poseMatrix = new Matrix4f(basePose).translate(
                (float) (hitPos.x - cameraPos.x),
                (float) (hitPos.y - cameraPos.y),
                (float) (hitPos.z - cameraPos.z)
            );

            float pulseFactor = computePulseFactor(animSeconds);
            domeRadius = (float) data.renderData().domeQuads().get(0).p1().length();
            Vec3 trajectoryIntercept = com.simonconrad.fireballpredictor.math.TrajectoryPredictor.computeTrajectoryDomeIntercept(data.path(), hitPos, domeRadius);

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
                config.domeFresnelStrength,
                theme,
                animSeconds,
                trajectoryIntercept
            );
        }

        if (trailState != null || domeState != null) {
            float distSq = (float) camera.position().distanceToSqr(fireball.position());
            PredictionSubmit submit = new PredictionSubmit(distSq, trailState, domeState);
            AABB pathBox = TrajectoryPredictor.calculatePathBoundingBox(data.path(), domeRadius);
            Frustum frustum = camera.getCullFrustum();
            if (pathBox == null || frustum == null || frustum.isVisible(pathBox)) {
                collection.translucentModels.submit(submit);
            }
        }
    }

    /**
     * Dome breathing pulse at 0.5 Hz (period 2 s) — the same rate the dome always used — driven by
     * the same game-time {@code animSeconds} as all other theme animations, so the pulse pauses with
     * the game and freezes at full alpha when {@code themeAnimationSpeed} is 0.
     */
    public static float computePulseFactor(double animSeconds) {
        return com.simonconrad.fireballpredictor.config.VisualTheme.computePulseFactor(animSeconds);
    }
}
