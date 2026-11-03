package me.blockcraft.sternenfront.vehicle;

public final class SpeederMotion {

    private SpeederMotion() { }

    public static double speed(double current, int throttle, double maximum, double acceleration) {
        double wanted = throttle > 0 ? maximum : throttle < 0 ? -maximum * 0.35 : 0;
        return current + Math.clamp(wanted - current, -acceleration, acceleration);
    }
}
