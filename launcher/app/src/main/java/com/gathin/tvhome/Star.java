package com.gathin.tvhome;

/** A five-pointed star as ten vertices, pure maths so it can be tested off the device. */
final class Star {

    /** Inner to outer radius of a regular five-pointed star (the flag's star): sin 18 / sin 54. */
    static final float INNER_RATIO = 0.381966f;

    private Star() {}

    /**
     * The star's outline as {x0, y0, x1, y1, ...}: ten points, starting with the top point and going clockwise
     * (y down), alternating outer and inner. {@code turnDeg} turns the whole star clockwise.
     */
    static float[] points(float cx, float cy, float outer, float inner, float turnDeg) {
        return points(new float[20], cx, cy, outer, inner, turnDeg);
    }

    /** {@link #points(float, float, float, float, float)} into {@code p} (20 floats), for a draw that allocates nothing. */
    static float[] points(float[] p, float cx, float cy, float outer, float inner, float turnDeg) {
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + 36 * i + turnDeg);
            float r = (i & 1) == 0 ? outer : inner;
            p[2 * i] = cx + r * (float) Math.cos(a);
            p[2 * i + 1] = cy + r * (float) Math.sin(a);
        }
        return p;
    }

    /**
     * The Vietnamese flag fitted into a w x h box: {left, top, width, height, starRadius}. The flag is 3:2, as wide
     * as fits, centred; the star's points lie on a circle whose radius is a fifth of the flag's length.
     */
    static float[] flag(float w, float h, float fill) {
        return flag(new float[5], w, h, fill);
    }

    /** {@link #flag(float, float, float)} into {@code out} (5 floats). */
    static float[] flag(float[] out, float w, float h, float fill) {
        float fw = Math.min(w * fill, h * fill * 1.5f);
        float fh = fw / 1.5f;
        out[0] = (w - fw) / 2f;
        out[1] = (h - fh) / 2f;
        out[2] = fw;
        out[3] = fh;
        out[4] = fw / 5f;
        return out;
    }
}
