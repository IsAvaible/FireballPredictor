package com.simonconrad.fireballpredictor.math;

/**
 * Pre-computed high-resolution hemispherical dome mesh for the impact point.
 * 32 latitude x 48 longitude bands forming the upper shockwave dome (y >= 0).
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
        int latitudeBands = 32;
        int longitudeBands = 48;
        int quadCount = latitudeBands * longitudeBands;

        float[] vertices = new float[quadCount * 4 * 3];
        float[] alphas = new float[quadCount * 4];

        int quad = 0;
        for (int lat = 0; lat < latitudeBands; lat++) {
            // Theta spans [0, PI/2] from apex (y = radius) down to base rim (y = 0)
            float theta1 = (float) (lat * (Math.PI / 2.0) / latitudeBands);
            float theta2 = (float) ((lat + 1) * (Math.PI / 2.0) / latitudeBands);

            float sinTheta1 = (float) Math.sin(theta1);
            float cosTheta1 = (float) Math.cos(theta1);
            float sinTheta2 = (float) Math.sin(theta2);
            float cosTheta2 = (float) Math.cos(theta2);

            // Apex at theta = 0 has baseline alpha floor; base rim at theta = PI/2 peaks at max alpha
            float alpha1 = 82.0F * 0.70F * (0.40F + 0.60F * sinTheta1);
            float alpha2 = 82.0F * 0.70F * (0.40F + 0.60F * sinTheta2);

            for (int lon = 0; lon < longitudeBands; lon++) {
                float phi1 = (float) (lon * 2 * Math.PI / longitudeBands);
                float phi2 = (float) ((lon + 1) * 2 * Math.PI / longitudeBands);

                float sinPhi1 = (float) Math.sin(phi1);
                float cosPhi1 = (float) Math.cos(phi1);
                float sinPhi2 = (float) Math.sin(phi2);
                float cosPhi2 = (float) Math.cos(phi2);

                int base = quad * 12;

                // Spherical dome coordinates: x = r * sin(theta) * cos(phi), y = r * cos(theta), z = r * sin(theta) * sin(phi)
                // v0 (lat1, lon1)
                setVertex(vertices, base, 0, radius, sinTheta1 * cosPhi1, cosTheta1, sinTheta1 * sinPhi1);
                // v1 (lat1, lon2)
                setVertex(vertices, base, 3, radius, sinTheta1 * cosPhi2, cosTheta1, sinTheta1 * sinPhi2);
                // v2 (lat2, lon2)
                setVertex(vertices, base, 6, radius, sinTheta2 * cosPhi2, cosTheta2, sinTheta2 * sinPhi2);
                // v3 (lat2, lon1)
                setVertex(vertices, base, 9, radius, sinTheta2 * cosPhi1, cosTheta2, sinTheta2 * sinPhi1);

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
