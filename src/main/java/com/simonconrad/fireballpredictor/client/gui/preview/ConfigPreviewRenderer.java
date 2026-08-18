package com.simonconrad.fireballpredictor.client.gui.preview;

import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
import com.simonconrad.fireballpredictor.config.VisualTheme;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.config.v2.api.ConfigField;
import dev.isxander.yacl3.config.v2.api.autogen.CustomImage;
import dev.isxander.yacl3.config.v2.api.autogen.OptionAccess;
import dev.isxander.yacl3.gui.image.ImageRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.Color;
import java.util.concurrent.CompletableFuture;

/**
 * Live schematic preview drawn in the YACL3 description side-panel while editing visuals.
 *
 * <p>Each instance is bound to an {@link OptionAccess} so every frame reads {@code pendingValue()}
 * from related options — color, width, style, pulse, anchors, etc. update immediately without
 * requiring the focused option itself to change.
 *
 * <p>All drawing is immediate-mode and delegated to modular per-mode renderers. Everything is
 * clipped to the schematic's inner rectangle via {@link Painter}.
 */
public final class ConfigPreviewRenderer implements ImageRenderer {

    public enum Mode {
        /** Visual Theme preview (renders thematic trajectory arc and impact shockwave). */
        THEME,
        /** Fireball / wither ribbon schematic. */
        TRAJECTORY,
        /** Wind-charge ribbon schematic (uses wind-charge color options). */
        TRAJECTORY_WIND,
        /** Shockwave dome + optional block-highlight grid. */
        SHOCKWAVE,
        /** Wind-charge shockwave schematic. */
        SHOCKWAVE_WIND,
        /** Miniature HUD frame with impact-warning badge placement. */
        HUD,
        /** Global tracking master overview. */
        TRACK_MASTER,
        /** Hostile-mob tracking master overview. */
        TRACK_MOB_MASTER,
        /** Other sources master overview. */
        TRACK_OTHER_MASTER,
        /** Single lock-on: fireball. */
        TRACK_FIREBALL(TrackingRenderer.Target.FIREBALL, "trackFireballs", ParentGroup.NONE),
        /** Single lock-on: wither skull. */
        TRACK_WITHER(TrackingRenderer.Target.WITHER, "trackWitherSkulls", ParentGroup.NONE),
        /** Single lock-on: wind charge. */
        TRACK_WIND(TrackingRenderer.Target.WIND, "trackWindCharges", ParentGroup.NONE),
        /** Single lock-on: blaze fireball. */
        TRACK_BLAZE(TrackingRenderer.Target.BLAZE, "trackBlazeFireballs", ParentGroup.MOB),
        /** Single lock-on: ghast fireball. */
        TRACK_GHAST(TrackingRenderer.Target.GHAST, "trackGhastFireballs", ParentGroup.MOB),
        /** Single lock-on: ender dragon fireball. */
        TRACK_DRAGON(TrackingRenderer.Target.DRAGON, "trackEnderDragonFireballs", ParentGroup.MOB),
        /** Single lock-on: wither mob. */
        TRACK_WITHER_MOB(TrackingRenderer.Target.WITHER, "trackWitherMob", ParentGroup.MOB),
        /** Single lock-on: player-owned projectile. */
        TRACK_PLAYER(TrackingRenderer.Target.PLAYER, "trackPlayerProjectiles", ParentGroup.OTHER),
        /** Single lock-on: dispenser projectile. */
        TRACK_DISPENSER(TrackingRenderer.Target.DISPENSER, "trackDispenserProjectiles", ParentGroup.OTHER),
        /** Single lock-on: command / unmatched projectile. */
        TRACK_COMMAND(TrackingRenderer.Target.COMMAND, "trackCommandProjectiles", ParentGroup.OTHER),
        /** Health bar cracking hearts overlay preview. */
        DAMAGE_HEARTS,
        /** Damage and knockback numerical readout preview. */
        KNOCKBACK_ESTIMATOR;

        final TrackingRenderer.Target target;
        final String configField;
        final ParentGroup parentGroup;

        Mode() {
            this(null, null, ParentGroup.NONE);
        }

        Mode(TrackingRenderer.Target target, String configField, ParentGroup parentGroup) {
            this.target = target;
            this.configField = configField;
            this.parentGroup = parentGroup;
        }
    }

    private enum ParentGroup {
        NONE, MOB, OTHER
    }

    // ---- Layout constants ---------------------------------------------------

    private static final float PANEL_ASPECT = 0.72f;
    /** Squatter panel for the compact tracking toggles. */
    private static final float TRACKING_ASPECT = 0.42f;
    /** Slightly taller for master overviews with chip rows. */
    private static final float MASTER_ASPECT = 0.48f;
    /** Compact aspect for damage estimator preview panels. */
    private static final float DAMAGE_ASPECT = 0.46f;

    // ---- Instance state -----------------------------------------------------

    private final Mode mode;
    private final OptionAccess access;

    private ConfigPreviewRenderer(Mode mode, OptionAccess access) {
        this.mode = mode;
        this.access = access;
    }

    public static ConfigPreviewRenderer of(Mode mode, OptionAccess access) {
        return new ConfigPreviewRenderer(mode, access);
    }

    // ---- ImageRenderer ------------------------------------------------------

    @Override
    public int render(GuiGraphicsExtractor graphics, int x, int y,
                      int renderWidth, float tickDelta) {
        int width = Math.max(32, renderWidth);
        float aspect = switch (mode) {
            case TRACK_MASTER, TRACK_MOB_MASTER, TRACK_OTHER_MASTER -> MASTER_ASPECT;
            case TRACK_FIREBALL, TRACK_WITHER, TRACK_WIND, TRACK_BLAZE, TRACK_GHAST,
                 TRACK_DRAGON, TRACK_WITHER_MOB, TRACK_PLAYER, TRACK_DISPENSER, TRACK_COMMAND -> TRACKING_ASPECT;
            case DAMAGE_HEARTS, KNOCKBACK_ESTIMATOR -> DAMAGE_ASPECT;
            default -> PANEL_ASPECT;
        };
        int height = Math.max(40, Math.round(width * aspect));

        int pad = Math.max(4, width / 24);
        int innerX = x + pad;
        int innerY = y + pad;
        int innerW = width - pad * 2;
        int innerH = height - pad * 2;

        Painter p = new Painter(graphics, innerX, innerY, innerX + innerW, innerY + innerH);

        switch (mode) {
            case THEME, TRAJECTORY, TRAJECTORY_WIND -> renderTrajectory(p, innerX, innerY, innerW, innerH);
            case SHOCKWAVE, SHOCKWAVE_WIND -> renderShockwave(p, innerX, innerY, innerW, innerH);
            case HUD -> renderHud(p, innerX, innerY, innerW, innerH);
            case TRACK_MASTER -> renderTrackMaster(p, innerX, innerY, innerW, innerH);
            case TRACK_MOB_MASTER -> renderTrackMobMaster(p, innerX, innerY, innerW, innerH);
            case TRACK_OTHER_MASTER -> renderTrackOtherMaster(p, innerX, innerY, innerW, innerH);
            case TRACK_FIREBALL, TRACK_WITHER, TRACK_WIND, TRACK_BLAZE, TRACK_GHAST,
                 TRACK_DRAGON, TRACK_WITHER_MOB, TRACK_PLAYER, TRACK_DISPENSER, TRACK_COMMAND
                    -> renderTracking(p, innerX, innerY, innerW, innerH);
            case DAMAGE_HEARTS -> renderDamageHearts(p, innerX, innerY, innerW, innerH);
            case KNOCKBACK_ESTIMATOR -> renderKnockbackEstimator(p, innerX, innerY, innerW, innerH);
        }

        return height;
    }

    @Override
    public void close() {
        // Nothing to free — pure immediate-mode GUI drawing.
    }

    // ---- Mode dispatchers ---------------------------------------------------

    private void renderTrajectory(Painter p, int x, int y, int w, int h) {
        boolean wind = mode == Mode.TRAJECTORY_WIND;
        boolean isThemeMode = mode == Mode.THEME;
        VisualTheme theme = pendingEnum("visualTheme", VisualTheme.DEFAULT);
        float animSpeed = pendingFloat("themeAnimationSpeed", 1.0f);

        TrajectoryRenderer.render(p, x, y, w, h, wind,
                isThemeMode || pendingBool("renderTrajectory", true),
                wind
                        ? pendingColor("windChargeTrajectoryColor", new Color(255, 255, 255))
                        : pendingColor("trajectoryColor", new Color(255, 128, 0)),
                pendingFloat("trajectoryWidth", 0.5f),
                pendingEnum("trajectoryStyle", TrajectoryStyle.SOLID),
                pendingBool("renderCoreGlow", true),
                pendingBool("enableRibbonPulse", true),
                theme,
                animSpeed);
    }

    private void renderShockwave(Painter p, int x, int y, int w, int h) {
        boolean wind = mode == Mode.SHOCKWAVE_WIND;
        VisualTheme theme = pendingEnum("visualTheme", VisualTheme.DEFAULT);
        float animSpeed = pendingFloat("themeAnimationSpeed", 1.0f);

        ShockwaveRenderer.render(p, x, y, w, h, wind,
                pendingBool("renderShockwaveDome", true),
                pendingBool("renderBlockHighlights", true),
                wind
                        ? pendingColor("windChargeShockwaveColor", new Color(255, 255, 255))
                        : pendingColor("shockwaveColor", new Color(255, 128, 0)),
                pendingFloat("domeFresnelStrength", 0.3f),
                theme,
                animSpeed);
    }

    private void renderHud(Painter p, int x, int y, int w, int h) {
        HudRenderer.render(p, x, y, w, h,
                pendingBool("renderImpactWarning", true),
                pendingEnum("impactWarningBadgeAnchor", ImpactWarningBadgeAnchor.TOP_LEFT),
                pendingInt("impactWarningBadgeOffsetX", 0),
                pendingInt("impactWarningBadgeOffsetY", 0));
    }

    private void renderTrackMaster(Painter p, int x, int y, int w, int h) {
        boolean master = pendingBool("trackProjectiles", true);
        TrackingRenderer.renderMaster(p, x, y, w, h,
                master,
                pendingBool("trackFireballs", true),
                pendingBool("trackWitherSkulls", true),
                pendingBool("trackWindCharges", true));
    }

    private void renderTrackMobMaster(Painter p, int x, int y, int w, int h) {
        boolean master = pendingBool("trackProjectiles", true);
        boolean mobs = pendingBool("trackMobProjectiles", true) && master;
        TrackingRenderer.renderMobMaster(p, x, y, w, h,
                mobs,
                pendingBool("trackBlazeFireballs", true),
                pendingBool("trackGhastFireballs", true),
                pendingBool("trackEnderDragonFireballs", true),
                pendingBool("trackWitherMob", true));
    }

    private void renderTrackOtherMaster(Painter p, int x, int y, int w, int h) {
        boolean master = pendingBool("trackProjectiles", true);
        boolean others = pendingBool("trackOtherOwnerProjectiles", false) && master;
        TrackingRenderer.renderOtherMaster(p, x, y, w, h,
                others,
                pendingBool("trackPlayerProjectiles", false),
                pendingBool("trackDispenserProjectiles", false),
                pendingBool("trackCommandProjectiles", false));
    }

    private void renderTracking(Painter p, int x, int y, int w, int h) {
        TrackingRenderer.Target target = mode.target != null ? mode.target : TrackingRenderer.Target.FIREBALL;
        String field = mode.configField != null ? mode.configField : "trackFireballs";

        // Honour master chain so the preview greys out when a parent is off
        boolean tracked = pendingBool(field, true) && pendingBool("trackProjectiles", true);
        if (mode.parentGroup == ParentGroup.MOB) {
            tracked = tracked && pendingBool("trackMobProjectiles", true);
        } else if (mode.parentGroup == ParentGroup.OTHER) {
            tracked = tracked && pendingBool("trackOtherOwnerProjectiles", false);
        }

        TrackingRenderer.renderSingle(p, x, y, w, h, target, tracked, target.fallbackColor);
    }

    private void renderDamageHearts(Painter p, int x, int y, int w, int h) {
        DamageEstimatorRenderer.renderHearts(p, x, y, w, h,
                pendingBool("renderDamageHeartsOverlay", true));
    }

    private void renderKnockbackEstimator(Painter p, int x, int y, int w, int h) {
        DamageEstimatorRenderer.renderKnockback(p, x, y, w, h,
                pendingBool("showKnockbackEstimator", true));
    }

    // ---- Pending-value access (YACL live-preview bridge) --------------------

    @SuppressWarnings("unchecked")
    private <T> T pending(String field, Class<T> type, T fallback) {
        if (access == null) {
            return fallback;
        }
        try {
            Option<?> option = access.getOption(field);
            if (option == null) {
                return fallback;
            }
            Object value = option.pendingValue();
            return type.isInstance(value) ? (T) value : fallback;
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private Color pendingColor(String field, Color fallback) {
        return pending(field, Color.class, fallback);
    }

    private float pendingFloat(String field, float fallback) {
        return pending(field, Number.class, fallback).floatValue();
    }

    private int pendingInt(String field, int fallback) {
        return pending(field, Number.class, fallback).intValue();
    }

    private boolean pendingBool(String field, boolean fallback) {
        return pending(field, Boolean.class, fallback);
    }

    private <E extends Enum<E>> E pendingEnum(String field, E fallback) {
        // getDeclaringClass(), not getClass(): constants with bodies are anonymous subclasses.
        return pending(field, fallback.getDeclaringClass(), fallback);
    }

    // ---- YACL @CustomImage factories (public no-arg ctor required) ----------

    /** Base factory so each mode is a one-liner. */
    private abstract static class ModeFactory implements CustomImage.CustomImageFactory<Object> {
        private final Mode mode;

        protected ModeFactory(Mode mode) {
            this.mode = mode;
        }

        @Override
        public final CompletableFuture<ImageRenderer> createImage(
                Object value, ConfigField<Object> field, OptionAccess access) {
            return CompletableFuture.completedFuture(ConfigPreviewRenderer.of(mode, access));
        }
    }

    public static final class VisualThemeFactory extends ModeFactory {
        public VisualThemeFactory() { super(Mode.THEME); }
    }

    public static final class TrajectoryFactory extends ModeFactory {
        public TrajectoryFactory() { super(Mode.TRAJECTORY); }
    }

    public static final class TrajectoryWindFactory extends ModeFactory {
        public TrajectoryWindFactory() { super(Mode.TRAJECTORY_WIND); }
    }

    public static final class ShockwaveFactory extends ModeFactory {
        public ShockwaveFactory() { super(Mode.SHOCKWAVE); }
    }

    public static final class ShockwaveWindFactory extends ModeFactory {
        public ShockwaveWindFactory() { super(Mode.SHOCKWAVE_WIND); }
    }

    public static final class HudFactory extends ModeFactory {
        public HudFactory() { super(Mode.HUD); }
    }

    public static final class TrackMasterFactory extends ModeFactory {
        public TrackMasterFactory() { super(Mode.TRACK_MASTER); }
    }

    public static final class TrackMobMasterFactory extends ModeFactory {
        public TrackMobMasterFactory() { super(Mode.TRACK_MOB_MASTER); }
    }

    public static final class TrackOtherMasterFactory extends ModeFactory {
        public TrackOtherMasterFactory() { super(Mode.TRACK_OTHER_MASTER); }
    }

    public static final class TrackFireballFactory extends ModeFactory {
        public TrackFireballFactory() { super(Mode.TRACK_FIREBALL); }
    }

    public static final class TrackWitherFactory extends ModeFactory {
        public TrackWitherFactory() { super(Mode.TRACK_WITHER); }
    }

    public static final class TrackWindFactory extends ModeFactory {
        public TrackWindFactory() { super(Mode.TRACK_WIND); }
    }

    public static final class TrackBlazeFactory extends ModeFactory {
        public TrackBlazeFactory() { super(Mode.TRACK_BLAZE); }
    }

    public static final class TrackGhastFactory extends ModeFactory {
        public TrackGhastFactory() { super(Mode.TRACK_GHAST); }
    }

    public static final class TrackDragonFactory extends ModeFactory {
        public TrackDragonFactory() { super(Mode.TRACK_DRAGON); }
    }

    public static final class TrackWitherMobFactory extends ModeFactory {
        public TrackWitherMobFactory() { super(Mode.TRACK_WITHER_MOB); }
    }

    public static final class TrackPlayerFactory extends ModeFactory {
        public TrackPlayerFactory() { super(Mode.TRACK_PLAYER); }
    }

    public static final class TrackDispenserFactory extends ModeFactory {
        public TrackDispenserFactory() { super(Mode.TRACK_DISPENSER); }
    }

    public static final class TrackCommandFactory extends ModeFactory {
        public TrackCommandFactory() { super(Mode.TRACK_COMMAND); }
    }

    public static final class DamageHeartsFactory extends ModeFactory {
        public DamageHeartsFactory() { super(Mode.DAMAGE_HEARTS); }
    }

    public static final class KnockbackEstimatorFactory extends ModeFactory {
        public KnockbackEstimatorFactory() { super(Mode.KNOCKBACK_ESTIMATOR); }
    }
}
