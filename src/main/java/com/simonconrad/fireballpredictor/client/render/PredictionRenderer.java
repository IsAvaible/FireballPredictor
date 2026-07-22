package com.simonconrad.fireballpredictor.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simonconrad.fireballpredictor.math.PredictionData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
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
      private static final RenderPipeline PREDICTION_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("fireballpredictor", "pipeline/prediction_translucent"))
            .build()
    );

    static final RenderType FIREBALL_TRAIL = RenderType.create(
        "fireball_trail",
        RenderSetup.builder(RenderPipelines.LIGHTNING).createRenderSetup()
    );
    static final RenderType SHOCKWAVE_DOME = RenderType.create(
        "shockwave_dome",
        RenderSetup.builder(PREDICTION_PIPELINE).createRenderSetup()
    );
    
    private static final ItemStack WARNING_ICON = new ItemStack(Items.FIRE_CHARGE);
    private static final ItemStack WIND_CHARGE_WARNING_ICON = new ItemStack(Items.WIND_CHARGE);

    public static void renderImpactWarningBadge(GuiGraphicsExtractor context, Minecraft client, boolean visible, float progress, boolean isWindCharge) {
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

        ItemStack icon = isWindCharge ? WIND_CHARGE_WARNING_ICON : WARNING_ICON;
        context.item(icon, x + 2, y + 2);

        int barX = x + 2;
        int barY = y + size - 2;
        int barWidth = 15;
        int barHeight = 1;
        int filledWidth = Math.max(1, Math.round(Mth.clamp(progress, 0.0f, 1.0f) * barWidth));

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

    public static void render(PoseStack matrices, SubmitNodeCollector submitNodeCollector, Camera camera, ClientLevel world, PredictionData data, AbstractHurtingProjectile fireball) {
        if (!(submitNodeCollector instanceof SubmitNodeStorage storage)) {
            return;
        }
        SubmitNodeCollection collection = storage.order(0);

        Vec3 cameraPos = camera.position();
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        Vec3 camLook = Vec3.directionFromRotation(pitch, yaw);

        int elapsedTicks = Math.max(0, fireball.tickCount - data.predictionAge);

        com.simonconrad.fireballpredictor.config.ModConfig config = com.simonconrad.fireballpredictor.config.ModConfig.instance();
        boolean isWindCharge = fireball instanceof net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;

        java.awt.Color trajectoryColor = isWindCharge ? config.windChargeTrajectoryColor : config.trajectoryColor;
        java.awt.Color shockwaveColor = isWindCharge ? config.windChargeShockwaveColor : config.shockwaveColor;

        TrailRenderState trailState = null;
        if (config.renderTrajectory && data.path != null && data.path.size() > 1) {
            matrices.pushPose();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            Matrix4f poseMatrix = new Matrix4f(matrices.last().pose());
            matrices.popPose();

            trailState = new TrailRenderState(
                data.path,
                elapsedTicks,
                config.trajectoryWidth,
                trajectoryColor.getRed(),
                trajectoryColor.getGreen(),
                trajectoryColor.getBlue(),
                camLook,
                poseMatrix
            );
        }

        DomeRenderState domeState = null;
        if (config.renderShockwaveDome && data.hitResult != null && data.renderData != null && !data.renderData.domeQuads().isEmpty()) {
            Vec3 hitPos = data.hitResult.getLocation();
            
            matrices.pushPose();
            matrices.translate(hitPos.x - cameraPos.x, hitPos.y - cameraPos.y, hitPos.z - cameraPos.z);
            Matrix4f poseMatrix = new Matrix4f(matrices.last().pose());
            matrices.popPose();

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
                poseMatrix
            );
        }

        if (trailState != null || domeState != null) {
            float distSq = (float) camera.position().distanceToSqr(fireball.position());
            PredictionSubmit submit = new PredictionSubmit(distSq, trailState, domeState);
            collection.translucentModels.submit(submit);
        }
    }
}
