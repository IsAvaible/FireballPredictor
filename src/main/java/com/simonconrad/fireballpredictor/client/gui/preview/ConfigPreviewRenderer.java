package com.simonconrad.fireballpredictor.client.gui.preview;

import com.simonconrad.fireballpredictor.config.ImpactWarningBadgeAnchor;
import com.simonconrad.fireballpredictor.config.TrajectoryStyle;
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
        /** "Is this projectile tracked?" icon badge — wither skull. */
        TRACK_WITHER,
        /** "Is this projectile tracked?" icon badge — wind charge. */
        TRACK_WIND
    }

    // ---- Layout constants ---------------------------------------------------

    private static final float PANEL_ASPECT = 0.72f;
    /** Squatter panel for the compact tracking toggles. */
    private static final float TRACKING_ASPECT = 0.42f;

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
        float aspect = (mode == Mode.TRACK_WITHER || mode == Mode.TRACK_WIND)
                ? TRACKING_ASPECT : PANEL_ASPECT;
        int height = Math.max(40, Math.round(width * aspect));

        int pad = Math.max(4, width / 24);
        int innerX = x + pad;
        int innerY = y + pad;
        int innerW = width - pad * 2;
        int innerH = height - pad * 2;

        Painter p = new Painter(graphics, innerX, innerY, innerX + innerW, innerY + innerH);

        switch (mode) {
            case TRAJECTORY, TRAJECTORY_WIND -> renderTrajectory(p, innerX, innerY, innerW, innerH);
            case SHOCKWAVE, SHOCKWAVE_WIND -> renderShockwave(p, innerX, innerY, innerW, innerH);
            case HUD -> renderHud(p, innerX, innerY, innerW, innerH);
            case TRACK_WITHER, TRACK_WIND -> renderTracking(p, innerX, innerY, innerW, innerH);
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
        TrajectoryRenderer.render(p, x, y, w, h, wind,
                pendingBool("renderTrajectory", true),
                wind
                        ? pendingColor("windChargeTrajectoryColor", new Color(255, 255, 255))
                        : pendingColor("trajectoryColor", new Color(255, 128, 0)),
                pendingFloat("trajectoryWidth", 0.5f),
                pendingEnum("trajectoryStyle", TrajectoryStyle.SOLID),
                pendingBool("renderCoreGlow", true),
                pendingBool("enableRibbonPulse", true));
    }

    private void renderShockwave(Painter p, int x, int y, int w, int h) {
        boolean wind = mode == Mode.SHOCKWAVE_WIND;
        ShockwaveRenderer.render(p, x, y, w, h, wind,
                pendingBool("renderShockwaveDome", true),
                pendingBool("renderBlockHighlights", true),
                wind
                        ? pendingColor("windChargeShockwaveColor", new Color(255, 255, 255))
                        : pendingColor("shockwaveColor", new Color(255, 128, 0)));
    }

    private void renderHud(Painter p, int x, int y, int w, int h) {
        HudRenderer.render(p, x, y, w, h,
                pendingBool("renderImpactWarning", true),
                pendingEnum("impactWarningBadgeAnchor", ImpactWarningBadgeAnchor.TOP_LEFT),
                pendingInt("impactWarningBadgeOffsetX", 0),
                pendingInt("impactWarningBadgeOffsetY", 0));
    }

    private void renderTracking(Painter p, int x, int y, int w, int h) {
        boolean wind = mode == Mode.TRACK_WIND;
        TrackingRenderer.render(p, x, y, w, h, wind,
                pendingBool(wind ? "trackWindCharges" : "trackWitherSkulls", true),
                wind
                        ? pendingColor("windChargeTrajectoryColor", new Color(255, 255, 255))
                        : pendingColor("trajectoryColor", new Color(255, 128, 0)));
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

    public static final class TrackWitherFactory extends ModeFactory {
        public TrackWitherFactory() { super(Mode.TRACK_WITHER); }
    }

    public static final class TrackWindFactory extends ModeFactory {
        public TrackWindFactory() { super(Mode.TRACK_WIND); }
    }
}
