package com.simonconrad.fireballpredictor.client.render;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simonconrad.fireballpredictor.client.FireballPredictorClient;
import com.simonconrad.fireballpredictor.config.ModConfig;
import com.simonconrad.fireballpredictor.config.ProjectileVisualTheme;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import com.simonconrad.fireballpredictor.math.ImpactPredictor;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
        PredictionRenderData renderData,
        List<BlockPos> brokenBlocks
    ) {}

    public enum ThemeTarget {
        GLOBAL("global", "fireballpredictor.command.preview.target.global", "fireballpredictor.command.preview.target.global_hover", "fireballpredictor.command.preview.target_name.global", ChatFormatting.GREEN),
        FIREBALL("fireball", "fireballpredictor.command.preview.target.fireball", "fireballpredictor.command.preview.target.fireball_hover", "fireballpredictor.command.preview.target_name.fireball", ChatFormatting.GOLD),
        WIND_CHARGE("wind_charge", "fireballpredictor.command.preview.target.wind_charge", "fireballpredictor.command.preview.target.wind_charge_hover", "fireballpredictor.command.preview.target_name.wind_charge", ChatFormatting.AQUA),
        WITHER_SKULL("wither_skull", "fireballpredictor.command.preview.target.wither_skull", "fireballpredictor.command.preview.target.wither_skull_hover", "fireballpredictor.command.preview.target_name.wither_skull", ChatFormatting.GRAY),
        DRAGON_FIREBALL("dragon_fireball", "fireballpredictor.command.preview.target.dragon_fireball", "fireballpredictor.command.preview.target.dragon_fireball_hover", "fireballpredictor.command.preview.target_name.dragon_fireball", ChatFormatting.LIGHT_PURPLE);

        private final String key;
        private final String buttonKey;
        private final String hoverKey;
        private final String nameKey;
        private final ChatFormatting color;

        ThemeTarget(String key, String buttonKey, String hoverKey, String nameKey, ChatFormatting color) {
            this.key = key;
            this.buttonKey = buttonKey;
            this.hoverKey = hoverKey;
            this.nameKey = nameKey;
            this.color = color;
        }

        public String getKey() { return key; }
        public String getButtonKey() { return buttonKey; }
        public String getHoverKey() { return hoverKey; }
        public String getNameKey() { return nameKey; }
        public ChatFormatting getColor() { return color; }

        public static ThemeTarget fromKey(String key) {
            if (key == null || key.isEmpty()) return null;
            for (ThemeTarget target : values()) {
                if (target.key.equalsIgnoreCase(key) || target.name().equalsIgnoreCase(key)) {
                    return target;
                }
            }
            return null;
        }

        public void apply(VisualTheme theme, Player player) {
            if (theme == null) return;
            deleteLastPromptMessage();
            ModConfig config = ModConfig.instance();
            ProjectileVisualTheme pTheme = ProjectileVisualTheme.fromVisualTheme(theme);

            switch (this) {
                case GLOBAL -> config.visualTheme = theme;
                case FIREBALL -> config.fireballVisualTheme = pTheme;
                case WIND_CHARGE -> config.windChargeVisualTheme = pTheme;
                case WITHER_SKULL -> config.witherSkullVisualTheme = pTheme;
                case DRAGON_FIREBALL -> config.dragonFireballVisualTheme = pTheme;
            }
            ModConfig.save();

            if (player != null) {
                Component targetName = Component.translatable(nameKey).withStyle(color);
                Component themeName = theme.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
                player.sendSystemMessage(Component.translatable(KEY_THEME_SET_TARGET, targetName, themeName));
            }
        }

        public Component buildButton(VisualTheme theme, Component themeDisplayName) {
            return Component.translatable(buttonKey)
                    .withStyle(Style.EMPTY
                            .withColor(color)
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent.RunCommand("/fppreview set " + theme.getKey() + " " + key))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.translatable(hoverKey, themeDisplayName)
                            )));
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static final String KEY_TOGGLE_HOVER = "fireballpredictor.command.preview.toggle_hover";
    public static final String KEY_UNKNOWN_THEME = "fireballpredictor.command.preview.unknown_theme";
    public static final String KEY_UNKNOWN_TARGET = "fireballpredictor.command.preview.unknown_target";
    public static final String KEY_THEME_SET = "fireballpredictor.command.preview.theme_set";
    public static final String KEY_THEME_SET_TARGET = "fireballpredictor.command.preview.theme_set_target";
    public static final String KEY_CONFIRM = "fireballpredictor.command.preview.confirm";
    public static final String KEY_CONFIRM_HOVER = "fireballpredictor.command.preview.confirm_hover";
    public static final String KEY_CONFIRM_PROMPT = "fireballpredictor.command.preview.confirm_prompt";
    public static final String KEY_PROMPT_HEADER = "fireballpredictor.command.preview.prompt_header";
    public static final String KEY_ENABLED = "fireballpredictor.command.preview.enabled";
    public static final String KEY_CLEARED = "fireballpredictor.command.preview.cleared";
    public static final String KEY_SERVER_HINT = "fireballpredictor.command.preview.server_hint";

    public static Component previewCommandLink() {
        return Component.literal("/fppreview").withStyle(Style.EMPTY
                .withColor(ChatFormatting.YELLOW)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand("/fppreview"))
                .withHoverEvent(new HoverEvent.ShowText(Component.translatable(KEY_TOGGLE_HOVER))));
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var themeArg = ClientCommands.argument("theme", StringArgumentType.greedyString())
                .suggests((context, builder) -> {
                    for (VisualTheme theme : VisualTheme.values()) {
                        builder.suggest(theme.getKey());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    String input = StringArgumentType.getString(context, "theme");
                    applyThemeByCommandInput(input, context.getSource().getClient().player);
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

    public static void applyThemeByCommandInput(String input, Player player) {
        if (input == null || input.trim().isEmpty()) return;
        String[] parts = input.trim().split("\\s+");
        String themeKey = parts[0];
        String targetKey = parts.length > 1 ? parts[1] : "global";
        applyThemeByKeyAndTarget(themeKey, targetKey, player);
    }

    public static void applyThemeByKey(String key, Player player) {
        applyThemeByKeyAndTarget(key, "global", player);
    }

    public static void applyThemeByKeyAndTarget(String themeKey, String targetKey, Player player) {
        if (themeKey == null || themeKey.isEmpty()) return;
        VisualTheme selectedTheme = null;
        for (VisualTheme theme : VisualTheme.values()) {
            if (theme.getKey().equalsIgnoreCase(themeKey) || theme.name().equalsIgnoreCase(themeKey)) {
                selectedTheme = theme;
                break;
            }
        }
        if (selectedTheme == null) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable(KEY_UNKNOWN_THEME, themeKey));
            }
            return;
        }

        ThemeTarget target = ThemeTarget.fromKey(targetKey);
        if (target == null) {
            if (player != null) {
                player.sendSystemMessage(Component.translatable(KEY_UNKNOWN_TARGET, targetKey));
            }
            return;
        }

        target.apply(selectedTheme, player);
    }

    public static void setTheme(VisualTheme theme, Player player) {
        if (theme == null) return;
        ThemeTarget.GLOBAL.apply(theme, player);
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
     * If the player is looking at a theme's dome, trajectory, or nameplate, sends a multi-target
     * prompt message with clickable buttons to set that theme globally or for a specific projectile.
     *
     * @return true if a theme was targeted and prompt was sent
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

        MutableComponent msg = Component.translatable(KEY_PROMPT_HEADER,
                targeted.displayName().copy().withStyle(ChatFormatting.YELLOW));

        for (ThemeTarget target : ThemeTarget.values()) {
            msg.append(" ").append(target.buildButton(targeted.theme(), targeted.displayName()));
        }

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

        MutableComponent msg = Component.translatable(KEY_ENABLED,
                Component.literal(String.valueOf(TRACKS.size())).withStyle(ChatFormatting.GOLD),
                previewCommandLink());

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
            MutableComponent msg = Component.translatable(KEY_CLEARED, previewCommandLink());

            player.sendSystemMessage(msg);
        }
    }

    private static boolean isEnabledMessage(Component component) {
        return hasTranslationKey(component, KEY_ENABLED);
    }

    private static boolean isClearedMessage(Component component) {
        return hasTranslationKey(component, KEY_CLEARED);
    }

    private static boolean hasTranslationKey(Component component, String key) {
        if (component == null || key == null) {
            return false;
        }
        if (component.getContents() instanceof TranslatableContents contents && key.equals(contents.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (hasTranslationKey(sibling, key)) {
                return true;
            }
        }
        return false;
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

            List<BlockPos> brokenBlocks = null;
            if (player.level() != null) {
                brokenBlocks = ImpactPredictor.predictBrokenBlocks(1.3f, false, false, hitPos, player.level());
            }

            TRACKS.add(new Track(theme, theme.getDisplayName(), startPos, hitPos, path, domeMesh, brokenBlocks));
        }
    }

    public static void tick(Minecraft client) {
        if (!isActive() || TRACKS.isEmpty() || client == null || client.level == null || client.isPaused()) {
            return;
        }

        if (!ModConfig.instance().renderParticleAccents) {
            return;
        }

        RandomSource random = client.level.getRandom();
        ClientLevel level = client.level;

        for (Track track : TRACKS) {
            if (random.nextInt(3) == 0) {
                spawnBottomParticle(level, track, random);
            }
        }
    }

    private static void spawnBottomParticle(ClientLevel level, Track track, RandomSource random) {
        List<BlockPos> brokenBlocks = track.brokenBlocks();
        boolean spawnedOnBlock = false;
        if (brokenBlocks != null && !brokenBlocks.isEmpty()) {
            BlockPos randomPos = brokenBlocks.get(random.nextInt(brokenBlocks.size()));
            if (!level.getBlockState(randomPos).isAir()) {
                double px = randomPos.getX() + random.nextDouble();
                double py = randomPos.getY() + 1.1;
                double pz = randomPos.getZ() + random.nextDouble();
                ParticleOptions effect = FireballPredictorClient.getThematicParticle(track.theme(), random);
                level.addParticle(effect, px, py, pz, 0, 0.05, 0);
                spawnedOnBlock = true;
            }
        }

        if (!spawnedOnBlock) {
            double r = Math.sqrt(random.nextDouble()) * 2.6;
            double theta = random.nextDouble() * 2 * Math.PI;
            double px = track.hitPos().x + r * Math.cos(theta);
            double py = track.hitPos().y + 0.05;
            double pz = track.hitPos().z + r * Math.sin(theta);
            ParticleOptions effect = FireballPredictorClient.getThematicParticle(track.theme(), random);
            level.addParticle(effect, px, py, pz, 0, 0.05, 0);
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
