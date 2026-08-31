package com.simonconrad.fireballpredictor.client.gui.preview;

/**
 * Parabolic flight path for trajectory previews.
 *
 * <p>The arc is anchored so that {@code yAt(1) == impactY}. The apex (highest point)
 * occurs at normalised time {@code apexT}, with the remaining descent following a
 * quadratic curve.
 */
final class Arc {

    private final float x0;
    private final float x1;
    private final float apexT;
    private final float apexY;
    private final float k;

    /**
     * @param x0      left edge, in panel pixels
     * @param x1      right edge, in panel pixels
     * @param apexY   y-coordinate at the arc's apex
     * @param impactY y-coordinate at {@code t = 1} (the ground line)
     * @param apexT   normalised [0,1] position of the apex along the arc
     */
    Arc(float x0, float x1, float apexY, float impactY, float apexT) {
        this.x0 = x0;
        this.x1 = x1;
        this.apexY = apexY;
        this.apexT = apexT;
        float dt = 1.0f - apexT;
        this.k = (impactY - apexY) / (dt * dt);
    }

    /** X-coordinate at normalised time {@code t} (0 = left, 1 = right). */
    float xAt(float t) {
        return x0 + t * (x1 - x0);
    }

    /** Inverse: normalised time {@code t} for a given pixel x. */
    float tAtX(float px) {
        return (px - x0) / (x1 - x0);
    }

    /** Y-coordinate at normalised time {@code t}. */
    float yAt(float t) {
        float d = t - apexT;
        return apexY + k * d * d;
    }

    /** dy/dx in pixels per pixel. */
    float slopeAt(float t) {
        return (2.0f * k * (t - apexT)) / (x1 - x0);
    }
}
