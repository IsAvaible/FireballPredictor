package com.simonconrad.fireballpredictor.client.render;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import com.simonconrad.fireballpredictor.math.PredictionRenderData;
import com.simonconrad.fireballpredictor.math.TrajectoryPredictor;
import com.simonconrad.fireballpredictor.mixin.ChatComponentAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game interactive gallery showcasing all {@link VisualTheme} options
 * arranged in a circular exhibition around the player with straight trajectory flight ribbons,
 * tooltips, and shockwave blast domes in 3D world space.
 *
 * <p>Available in both development and production client environments via {@code /fppreview},
 * {@code /fireballpredictor preview}, or the button in the visual theme configuration screen.
 */
@Environment(EnvType.CLIENT)
public final class ThemePreviewGallery {

    private static boolean active = false;
    private static final List<Track> TRACKS = new ArrayList<>();

    public record Track(
        VisualTheme theme,
        Component displayName,
        Vec3 startPos,
        Vec3 hitPos,
        List<Vec3> path,
        PredictionRenderData renderData
    ) {}

    public static boolean isActive() {
        return active;
    }

    public static Component previewCommandLink() {
        return Component.literal("/fppreview").withStyle(Style.EMPTY
                .withColor(ChatFormatting.YELLOW)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/fppreview"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to toggle Theme Preview Gallery"))));
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var themeArg = ClientCommands.argument("theme", StringArgumentType.word())
                .suggests((context, builder) -> {
                    for (VisualTheme theme : VisualTheme.values()) {
                        builder.suggest(theme.getKey());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    String themeKey = StringArgumentType.getString(context, "theme");
                    applyThemeByKey(themeKey, context.getSource().getClient().player);
                    return 1;
                });

            var root = ClientCommands.literal("fppreview");
            var previewSub = ClientCommands.literal("preview");
            attachPreviewSubcommands(root, themeArg);
            attachPreviewSubcommands(previewSub, themeArg);

            dispatcher.register(root);
            dispatcher.register(ClientCommands.literal("fireballpredictor").then(previewSub));
        });
    }

    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, T>> void attachPreviewSubcommands(
            T builder,
            com.mojang.brigadier.builder.ArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource, ?> themeArg
    ) {
        builder.executes(context -> {
            toggle(context.getSource().getClient().player);
            return 1;
        })
        .then(ClientCommands.literal("on").executes(context -> {
            enable(context.getSource().getClient().player);
            return 1;
        }))
        .then(ClientCommands.literal("off").executes(context -> {
            disable(context.getSource().getClient().player);
            return 1;
        }))
        .then(ClientCommands.literal("clear").executes(context -> {
            disable(context.getSource().getClient().player);
            return 1;
        }))
        .then(ClientCommands.literal("set").then(themeArg))
        .then(ClientCommands.literal("select").then(themeArg));
    }

    private static net.minecraft.network.chat.MessageSignature lastPromptSignature = null;

    /**
     * Monotonic serial for the deterministic pseudo-signature of the confirmation prompt message.
     * The client never verifies player message signatures, so the bytes only need to be stable and
     * unique so the prompt can be correlated and removed from the chat again.
     */
    private static int promptSerial = 0;

    public static void applyThemeByKey(String key, Player player) {
        if (key == null || key.isEmpty()) return;
        for (VisualTheme theme : VisualTheme.values()) {
            if (theme.getKey().equalsIgnoreCase(key) || theme.name().equalsIgnoreCase(key)) {
                setTheme(theme, player);
                return;
            }
        }
        if (player != null) {
            player.sendSystemMessage(Component.literal("§6[Fireball Predictor]§c Unknown visual theme: " + key));
        }
    }

    public static void setTheme(VisualTheme theme, Player player) {
        if (theme == null) return;
        deleteLastPromptMessage();
        ModConfig config = ModConfig.instance();
        config.visualTheme = theme;
        ModConfig.save();
        if (player != null) {
            player.sendSystemMessage(Component.literal("§6[Fireball Predictor]§a Active visual theme set to ")
                    .append(theme.getDisplayName().copy().withStyle(ChatFormatting.GOLD))
                    .append("§a!"));
        }
    }

    public static void deleteLastPromptMessage() {
        if (lastPromptSignature == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui != null && client.gui.hud != null) {
            ChatComponent chat = client.gui.hud.getChat();
            if (chat instanceof ChatComponentAccessor accessor) {
                List<GuiMessage> messages = accessor.fireballpredictor$getAllMessages();
                boolean removed = messages.removeIf(msg -> lastPromptSignature.equals(msg.signature()));
                if (removed) {
                    accessor.fireballpredictor$refreshTrimmedMessages();
                }
            }
        }
        lastPromptSignature = null;
    }

    /**
     * Intercepts player left-click interactions when the preview gallery is active.
     * If the player is looking at a theme's dome, trajectory, or nameplate, sends a confirmation
     * chat message with a clickable [Confirm] link to set that theme.
     *
     * @return true if a theme was targeted and confirmation prompt was sent
     */
    public static boolean handleLeftClick() {
        if (!isActive() || TRACKS.isEmpty()) {
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        net.minecraft.world.entity.Entity cameraEntity = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
        if (client.player == null || cameraEntity == null) {
            return false;
        }

        Vec3 eyePos = cameraEntity.getEyePosition(1.0f);
        Vec3 lookVec = cameraEntity.getViewVector(1.0f);

        Track targeted = findTargetedTrack(eyePos, lookVec);
        if (targeted == null) {
            return false;
        }

        // Clean up any previous unconfirmed prompt message to keep chat clean
        deleteLastPromptMessage();

        byte[] sigBytes = new byte[MessageSignature.BYTES];
        int serial = ++promptSerial;
        for (int i = 0; i < sigBytes.length; i++) {
            sigBytes[i] = (byte) (serial ^ (i * 31 + 7));
        }
        lastPromptSignature = new MessageSignature(sigBytes);

        Component confirmButton = Component.literal("[Confirm]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent.RunCommand("/fppreview set " + targeted.theme().getKey()))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("Click to set active visual theme to ").append(targeted.displayName())
                        )));

        MutableComponent msg = Component.literal("§6[Fireball Predictor]§f Set active theme to ")
                .append(targeted.displayName().copy().withStyle(ChatFormatting.YELLOW))
                .append("? ")
                .append(confirmButton);

        if (client.gui != null && client.gui.hud != null) {
            client.gui.hud.getChat().addPlayerMessage(msg, lastPromptSignature, null);
        } else {
            client.player.sendSystemMessage(msg);
        }
        return true;
    }

    /**
     * Finds the gallery track intersecting the line-of-sight ray from {@code rayOrigin} along {@code rayDir}.
     */
    public static Track findTargetedTrack(Vec3 rayOrigin, Vec3 rayDir) {
        if (TRACKS.isEmpty()) return null;

        Track bestTrack = null;
        double bestDistSq = Double.MAX_VALUE;

        double dxzSq = rayDir.x * rayDir.x + rayDir.z * rayDir.z;

        for (Track track : TRACKS) {
            Vec3 hitPos = track.hitPos();
            Vec3 startPos = track.startPos();

            double deltaX = hitPos.x - rayOrigin.x;
            double deltaZ = hitPos.z - rayOrigin.z;

            // 1. Check ray proximity to vertical cylinder around dome and ribbon column
            if (dxzSq > 1e-6) {
                double t = (deltaX * rayDir.x + deltaZ * rayDir.z) / dxzSq;
                if (t > 0.0 && t <= 60.0) {
                    double rayX = rayOrigin.x + t * rayDir.x;
                    double rayY = rayOrigin.y + t * rayDir.y;
                    double rayZ = rayOrigin.z + t * rayDir.z;

                    double horizDistSq = (rayX - hitPos.x) * (rayX - hitPos.x) + (rayZ - hitPos.z) * (rayZ - hitPos.z);
                    double minY = Math.min(hitPos.y, startPos.y) - 1.0;
                    double maxY = Math.max(hitPos.y, startPos.y) + 2.0;

                    // Within cylindrical radius around dome and ribbon (2.5 blocks)
                    if (horizDistSq <= 2.5 * 2.5 && rayY >= minY && rayY <= maxY) {
                        if (horizDistSq < bestDistSq) {
                            bestDistSq = horizDistSq;
                            bestTrack = track;
                        }
                    }
                }
            }

            // 2. Check spherical proximity to floating nameplate tooltip
            Vec3 tagPos = startPos.add(0.0, 0.4, 0.0);
            Vec3 toTag = tagPos.subtract(rayOrigin);
            double tTag = toTag.dot(rayDir);
            if (tTag > 0.0 && tTag <= 60.0) {
                Vec3 closest = rayOrigin.add(rayDir.scale(tTag));
                double distToTagSq = closest.distanceToSqr(tagPos);
                if (distToTagSq <= 2.0 * 2.0) {
                    if (distToTagSq < bestDistSq) {
                        bestDistSq = distToTagSq;
                        bestTrack = track;
                    }
                }
            }
        }

        return bestTrack;
    }

    public static void toggle(Player player) {
        if (active) {
            disable(player);
        } else {
            enable(player);
        }
    }

    public static void enable(Player player) {
        if (player == null) return;
        active = true;
        rebuildTracks(player);

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui != null && client.gui.hud != null) {
            ChatComponent chat = client.gui.hud.getChat();
            if (chat instanceof ChatComponentAccessor accessor) {
                List<GuiMessage> messages = accessor.fireballpredictor$getAllMessages();
                if (!messages.isEmpty() && isClearedMessage(messages.get(0).content())) {
                    messages.remove(0);
                    accessor.fireballpredictor$refreshTrimmedMessages();
                }
            }
        }

        MutableComponent msg = Component.literal("§6[Fireball Predictor]§a Theme preview gallery enabled! §f(All ")
                .append(Component.literal(String.valueOf(TRACKS.size())).withStyle(ChatFormatting.GOLD))
                .append(" visual themes in a circle). Click a theme to select it. Run ")
                .append(previewCommandLink())
                .append(" to toggle off.");

        player.sendSystemMessage(msg);
    }

    public static void disable(Player player) {
        active = false;
        TRACKS.clear();
        deleteLastPromptMessage();

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui != null && client.gui.hud != null) {
            ChatComponent chat = client.gui.hud.getChat();
            if (chat instanceof ChatComponentAccessor accessor) {
                List<GuiMessage> messages = accessor.fireballpredictor$getAllMessages();
                if (!messages.isEmpty() && isEnabledMessage(messages.get(0).content())) {
                    messages.remove(0);
                    accessor.fireballpredictor$refreshTrimmedMessages();
                }
            }
        }

        if (player != null) {
            MutableComponent msg = Component.literal("§6[Fireball Predictor]§7 Theme preview gallery cleared. Run ")
                    .append(previewCommandLink())
                    .append("§7 to re-enable.");

            player.sendSystemMessage(msg);
        }
    }

    private static boolean isEnabledMessage(Component component) {
        if (component == null) return false;
        String text = component.getString();
        return text.contains("[Fireball Predictor]") && text.contains("Theme preview gallery enabled!");
    }

    private static boolean isClearedMessage(Component component) {
        if (component == null) return false;
        String text = component.getString();
        return text.contains("[Fireball Predictor]") && text.contains("Theme preview gallery cleared.");
    }

    private static void rebuildTracks(Player player) {
        TRACKS.clear();
        Vec3 origin = player.position();
        float yaw = player.getYRot();

        VisualTheme[] themes = VisualTheme.values();
        int count = themes.length;
        if (count == 0) return;

        // Dynamic radius calculation: ensure at least chord/arc spacing of 6.5 blocks between adjacent themes
        // chord = 2 * R * sin(pi / count) >= spacing  ==>  R >= spacing / (2 * sin(pi / count))
        double spacing = 6.5;
        double minRadius = 12.0;
        double dynamicRadius = spacing / (2.0 * Math.sin(Math.PI / count));
        double radius = Math.max(minRadius, dynamicRadius);

        PredictionRenderData domeMesh = TrajectoryPredictor.createRenderData(1.3f);
        double baseRad = Math.toRadians(yaw);

        for (int i = 0; i < count; i++) {
            VisualTheme theme = themes[i];

            // Distribute clockwise starting directly in front of the player (i = 0 is straight ahead)
            double angleOffset = (2.0 * Math.PI * i) / count;
            double angle = baseRad - angleOffset;

            // In Minecraft: yaw 0 is +Z, 90 is -X, 180 is -Z, 270 is +X
            // dirX = -sin(angle), dirZ = cos(angle)
            double dx = -Math.sin(angle);
            double dz = Math.cos(angle);

            Vec3 hitPos = origin.add(dx * radius, 0.0, dz * radius);
            Vec3 startPos = hitPos.add(0.0, 8.0, 0.0);

            // Straight linear trajectory path (no bending)
            List<Vec3> path = new ArrayList<>(32);
            int steps = 30;
            for (int s = 0; s <= steps; s++) {
                float t = (float) s / steps;
                double px = Mth.lerp(t, startPos.x, hitPos.x);
                double py = Mth.lerp(t, startPos.y, hitPos.y);
                double pz = Mth.lerp(t, startPos.z, hitPos.z);
                path.add(new Vec3(px, py, pz));
            }

            TRACKS.add(new Track(theme, theme.getDisplayName(), startPos, hitPos, path, domeMesh));
        }
    }

    public static void render(PoseStack matrices, SubmitNodeCollector submitNodeCollector, Camera camera, ClientLevel world) {
        if (!isActive() || TRACKS.isEmpty() || !(submitNodeCollector instanceof SubmitNodeStorage storage)) {
            return;
        }

        SubmitNodeCollection collection = storage.order(0);
        Minecraft client = Minecraft.getInstance();
        Vec3 cameraPos = camera.position();
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        Vec3 camLook = Vec3.directionFromRotation(pitch, yaw);

        ModConfig config = ModConfig.instance();
        float animSpeed = config.themeAnimationSpeed;
        double animTime = (((world != null ? world.getGameTime() : 0L) + client.getDeltaTracker().getGameTimeDeltaPartialTick(true)) / 20.0) * Math.max(0.0f, animSpeed);
        float pulseFactor = PredictionRenderer.computePulseFactor(animTime);

        TrajectoryStyle style = config.trajectoryStyle == null ? TrajectoryStyle.SOLID : config.trajectoryStyle;
        float width = config.trajectoryWidth;
        float fresnel = config.domeFresnelStrength;

        // Fill the camera render state as completely as possible. The name-tag submit path only
        // consumes `orientation` (verified against the 26.2 bytecode), but a fully initialised state
        // is defensive against other consumers and future versions. projectionMatrix is
        // intentionally left null: nothing in the name-tag path reads it, and a stale or wrong
        // projection would be worse than none.
        CameraRenderState camState = new CameraRenderState();
        camState.initialized = true;
        camState.pos = camera.position();
        camState.blockPos = net.minecraft.core.BlockPos.containing(camState.pos);
        camState.xRot = camera.xRot();
        camState.yRot = camera.yRot();
        camState.orientation = camera.rotation();
        camState.viewRotationMatrix = new Matrix4f().rotation(camera.rotation());
        camState.fogType = net.minecraft.world.level.material.FogType.NONE;

        // Trail pose is identical for all tracks (world origin relative to camera)
        Matrix4f basePose = matrices.last().pose();
        Matrix4f trailPose = new Matrix4f(basePose).translate((float) -cameraPos.x, (float) -cameraPos.y, (float) -cameraPos.z);

        for (Track track : TRACKS) {
            // 1. Trajectory straight trail submit
            TrailRenderState trailState = new TrailRenderState(
                track.path(),
                0,
                width,
                255, 128, 0,
                camLook,
                trailPose,
                style,
                true,
                config.enableRibbonPulse,
                animTime,
                1.0f,
                track.theme()
            );

            // 2. Shockwave dome submit
            Vec3 hitPos = track.hitPos();
            Matrix4f domePose = new Matrix4f(basePose).translate(
                (float) (hitPos.x - cameraPos.x),
                (float) (hitPos.y - cameraPos.y),
                (float) (hitPos.z - cameraPos.z)
            );

            DomeRenderState domeState = new DomeRenderState(
                hitPos,
                track.renderData().domeQuads(),
                255, 128, 0,
                pulseFactor,
                domePose,
                1.0f,
                cameraPos,
                fresnel,
                track.theme(),
                animTime
            );

            float distSq = (float) cameraPos.distanceToSqr(hitPos);
            collection.translucentModels.submit(new PredictionSubmit(distSq, trailState, domeState));

            // 3. Nameplate tooltip at the top of the trajectory path
            Vec3 tagRelPos = track.startPos().subtract(cameraPos).add(0.0, 0.4, 0.0);
            submitNodeCollector.submitNameTag(
                matrices,
                tagRelPos,
                0,
                track.displayName(),
                false,
                0xF000F0,
                camState
            );
        }
    }
}
