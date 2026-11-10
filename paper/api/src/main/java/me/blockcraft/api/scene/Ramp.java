package me.blockcraft.api.scene;

/**
 * A move that speeds up along half a cosine, keeps an even pace, and slows down the same way: how far along it is at
 * each moment, so that it covers {@code length} in exactly {@code length} of its clock.
 */
public final class Ramp {

    private Ramp() {
    }

    /** How far along a move of {@code length} is at {@code x}, easing in and out over {@code ramp} at each end. */
    public static double along(double x, double length, double ramp) {
        if (length <= 0 || x >= length) return Math.max(length, 0);
        if (x <= 0) return 0;
        double edge = Math.clamp(ramp, 0, length / 2);
        if (edge == 0) return x;
        double speed = length / (length - edge);
        if (x <= edge) return speed * (x / 2 - edge / (2 * Math.PI) * Math.sin(Math.PI * x / edge));
        if (x < length - edge) return speed * (edge / 2 + x - edge);
        return length - along(length - x, length, ramp);
    }
}
