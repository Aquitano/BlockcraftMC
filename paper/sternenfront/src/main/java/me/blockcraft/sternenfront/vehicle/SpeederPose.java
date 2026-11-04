package me.blockcraft.sternenfront.vehicle;

import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** How the model leans, kept apart from where the craft is. Angles are degrees: bank right and nose up are positive. */
record SpeederPose(double bank, double pitch) {

    static final SpeederPose LEVEL = new SpeederPose(0, 0);

    private static final double FULL_CLIMB_PER_TICK = 0.25;
    private static final double ANGLE_STEP = 0.5;
    private static final double LIFT_STEP = 0.005;

    /** {@code turning} and {@code surge} run from -1 to 1, {@code climb} is the shown height gained this tick. */
    SpeederPose next(double turning, double surge, double climb, SpeederConfig.Presentation presentation) {
        double wantedPitch = surge * presentation.surgePitchDegrees()
                + Math.clamp(climb / FULL_CLIMB_PER_TICK, -1, 1) * presentation.climbPitchDegrees();
        double share = presentation.smoothing();
        return new SpeederPose(bank + (turning * presentation.bankDegrees() - bank) * share,
                pitch + (wantedPitch - pitch) * share);
    }

    /** Rounded, so that a pose that barely moved compares equal and does not restart the interpolation. */
    Transformation transformation(double lift, double pivot) {
        var rotation = new Quaternionf()
                .rotateX((float) -Math.toRadians(rounded(pitch, ANGLE_STEP)))
                .rotateZ((float) Math.toRadians(rounded(bank, ANGLE_STEP)));
        var pivotPoint = new Vector3f(0, (float) pivot, 0);
        var translation = new Vector3f(0, (float) rounded(lift, LIFT_STEP), 0).add(pivotPoint)
                .sub(rotation.transform(new Vector3f(pivotPoint)));
        // Minecraft's item-display renderer adds a half turn after this transformation; this one cancels it.
        return new Transformation(translation, rotation, new Vector3f(1), new Quaternionf().rotationY((float) Math.PI));
    }

    private static double rounded(double value, double step) {
        return Math.round(value / step) * step;
    }
}
