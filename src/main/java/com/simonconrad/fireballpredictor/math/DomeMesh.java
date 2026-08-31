package com.simonconrad.fireballpredictor.math;

/**
 * Pre-computed low-poly sphere ("shockwave dome") mesh for the impact point.
 * 20 latitude x 24 longitude bands, alpha profile peaking at the equator,
 * mirroring the original mod's dome geometry.
 */
public final class DomeMesh {

    /** Number of quads: latitudeBands * longitudeBands. */
    public final int quadCount;
    public final float radius;
    /** 4 vertices x 3 coords per quad, relative to the dome center. */
    public final float[] vertices;
    /** Base alpha per vertex (0..255), lat-band profile. */
    public final float[] alphas;

    public static final DomeMesh EMPTY = new DomeMesh(0, 0.0F, new float[0], new float[0]);

    private DomeMesh(int quadCount, float radius, float[] vertices, float[] alphas) {
        this.quadCount = quadCount;
        this.radius = radius;
        this.vertices = vertices;
        this.alphas = alphas;
    }

    /** Builds the dome for an explosion of the given power (radius = power * 2). */
    public static DomeMesh build(float power) {
        if (power <= 0.0F) {
            return EMPTY;
        }

        float radius = power * 2.0F;
        int latitudeBands = 20;
        int longitudeBands = 24;
        int quadCount = latitudeBands * longitudeBands;

        float[] vertices = new float[quadCount * 4 * 3];
        float[] alphas = new float[quadCount * 4];

        int quad = 0;
        for (int lat = 0; lat < latitudeBands; lat++) {
            float theta1 = (float) (lat * Math.PI / latitudeBands);
            float theta2 = (float) ((lat + 1) * Math.PI / latitudeBands);

            float sinTheta1 = (float) Math.sin(theta1);
            float cosTheta1 = (float) Math.cos(theta1);
            float sinTheta2 = (float) Math.sin(theta2);
            float cosTheta2 = (float) Math.cos(theta2);

            float h1 = (float) lat / latitudeBands;
            float h2 = (float) (lat + 1) / latitudeBands;
            // sin(pi*h): 0 at the poles, 1 at the equator -> bright rim, soft poles.
            float alpha1 = 82.0F * 0.70F * (float) Math.sin(Math.PI * h1);
            float alpha2 = 82.0F * 0.70F * (float) Math.sin(Math.PI * h2);

            for (int lon = 0; lon < longitudeBands; lon++) {
                float phi1 = (float) (lon * 2 * Math.PI / longitudeBands);
                float phi2 = (float) ((lon + 1) * 2 * Math.PI / longitudeBands);

                float sinPhi1 = (float) Math.sin(phi1);
                float cosPhi1 = (float) Math.cos(phi1);
                float sinPhi2 = (float) Math.sin(phi2);
                float cosPhi2 = (float) Math.cos(phi2);

                int base = quad * 12;

                // p1 (lat1, lon1), p2 (lat1, lon2), p3 (lat2, lon2), p4 (lat2, lon1)
                setVertex(vertices, base, 0, radius, cosPhi1 * cosTheta1, sinTheta1, sinPhi1 * cosTheta1);
                setVertex(vertices, base, 3, radius, cosPhi2 * cosTheta1, sinTheta1, sinPhi2 * cosTheta1);
                setVertex(vertices, base, 6, radius, cosPhi2 * cosTheta2, sinTheta2, sinPhi2 * cosTheta2);
                setVertex(vertices, base, 9, radius, cosPhi1 * cosTheta2, sinTheta2, sinPhi1 * cosTheta2);

                int abase = quad * 4;
                alphas[abase] = alpha1;
                alphas[abase + 1] = alpha1;
                alphas[abase + 2] = alpha2;
                alphas[abase + 3] = alpha2;

                quad++;
            }
        }

        return new DomeMesh(quadCount, radius, vertices, alphas);
    }

    private static void setVertex(float[] vertices, int base, int off, float radius, float x, float y, float z) {
        vertices[base + off] = radius * x;
        vertices[base + off + 1] = radius * y;
        vertices[base + off + 2] = radius * z;
    }
}
