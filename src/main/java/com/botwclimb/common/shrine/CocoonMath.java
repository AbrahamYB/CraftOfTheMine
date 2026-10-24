package com.botwclimb.common.shrine;

/**
 * The shape of the mud cocoon: an ellipsoid round the rider, cut into {@link #PETALS} petals that all hinge at its lowest point.
 * Opened by an angle each petal tips outward like a flower; at zero they close into the smooth ellipsoid. Pure maths, so it can
 * be tested without a window. Positions are blocks above the rider's feet (y up).
 */
public final class CocoonMath {
    public static final int PETALS = 8;

    private CocoonMath() {}

    /** The strips are this far apart in texture height, and climb this much over one trip round (see tools/gen_cocoon.py). */
    public static final double STRIP_PERIOD = 0.125, STRIP_SLOPE = 0.5;

    /**
     * A point of the cocoon after its petal has opened.
     *
     * @param phi    0 at the bottom pole to pi at the top
     * @param lam    longitude of the point
     * @param petal  longitude of the middle of the petal it belongs to
     * @param open   how far the petal has tipped outward, in radians (0 closed, pi/2 flat)
     * @param cy     height of the ellipsoid's middle
     * @param ridge  how far the wrapped strips stand out of the surface, as a share of the radius (0 for a plain ellipsoid)
     * @param out    receives x, y, z
     */
    public static void point(double phi, double lam, double petal, double open, double rx, double ry, double rz, double cy, double ridge, double[] out) {
        double s = 1 + ridge * Math.sin(phi) * strip(phi, lam); // nothing at the poles, so the petals still meet there
        double x = s * rx * Math.sin(phi) * Math.cos(lam);
        double y = ry * (1 - s * Math.cos(phi)); // from the hinge, the lowest point
        double z = s * rz * Math.sin(phi) * Math.sin(lam);
        rotate(x, y, z, petal, open, out);
        out[1] += cy - ry;
    }

    /** 0..1 across the wrapped strips: 1 on the middle of a strip, 0 in the gap between two. It matches the painted texture. */
    public static double strip(double phi, double lam) {
        double b = (phi / Math.PI - STRIP_SLOPE * lam / (2 * Math.PI)) / STRIP_PERIOD;
        return 0.5 - 0.5 * Math.cos(2 * Math.PI * b);
    }

    /** The outward surface normal at that point (for smooth lighting), found from the surface itself so the strips light up too. */
    public static void normal(double phi, double lam, double petal, double open, double rx, double ry, double rz, double ridge, double[] out) {
        double e = 1e-3;
        double[] a = new double[3], b = new double[3], c = new double[3], d = new double[3];
        point(phi + e, lam, petal, open, rx, ry, rz, 0, ridge, a);
        point(phi - e, lam, petal, open, rx, ry, rz, 0, ridge, b);
        point(phi, lam + e, petal, open, rx, ry, rz, 0, ridge, c);
        point(phi, lam - e, petal, open, rx, ry, rz, 0, ridge, d);
        double ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2], vx = c[0] - d[0], vy = c[1] - d[1], vz = c[2] - d[2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-12) { // at a pole the longitude lines meet and have no direction: use the plain ellipsoid's
            nx = Math.sin(phi) * Math.cos(lam) / rx;
            ny = -Math.cos(phi) / ry;
            nz = Math.sin(phi) * Math.sin(lam) / rz;
            len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            rotate(nx / len, ny / len, nz / len, petal, open, out);
            return;
        }
        out[0] = nx / len;
        out[1] = ny / len;
        out[2] = nz / len;
    }

    /** Turns a vector about the horizontal axis at right angles to the petal's direction, so that up tips toward the petal's side. */
    private static void rotate(double x, double y, double z, double petal, double open, double[] out) {
        double ax = -Math.sin(petal), az = Math.cos(petal); // the axis, horizontal
        double cos = Math.cos(-open), sin = Math.sin(-open);
        double dot = ax * x + az * z;
        double cx = -az * y, cy = az * x - ax * z, cz = ax * y; // axis x vector (the axis has no y part)
        out[0] = x * cos + cx * sin + ax * dot * (1 - cos);
        out[1] = y * cos + cy * sin;
        out[2] = z * cos + cz * sin + az * dot * (1 - cos);
    }
}
