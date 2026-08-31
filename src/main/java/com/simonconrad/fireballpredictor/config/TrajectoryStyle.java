package com.simonconrad.fireballpredictor.config;

/**
 * Visual style of the predicted trajectory ribbon (1.8.9 backport of master's
 * TrajectoryStyle enum).
 *
 * <ul>
 *   <li>{@link #SOLID} - full ribbon: soft outer shroud plus bright core layer.</li>
 *   <li>{@link #DASHED} - like {@link #SOLID} but alternating bright/dark segments.</li>
 *   <li>{@link #CORE_ONLY} - only the narrow bright core layer, 60% width.</li>
 * </ul>
 */
public enum TrajectoryStyle {
    SOLID("solid"),
    DASHED("dashed"),
    CORE_ONLY("core_only");

    private final String key;

    TrajectoryStyle(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** Parses a config string (key or enum name), falling back to {@link #SOLID}. */
    public static TrajectoryStyle byName(String name) {
        if (name != null) {
            String trimmed = name.trim().toLowerCase(java.util.Locale.ROOT);
            for (TrajectoryStyle style : values()) {
                if (style.key.equals(trimmed) || style.name().toLowerCase(java.util.Locale.ROOT).equals(trimmed)) {
                    return style;
                }
            }
        }
        return SOLID;
    }
}
