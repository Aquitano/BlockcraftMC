package me.blockcraft.sternenfront.game;

import java.util.List;
import java.util.Optional;

/**
 * Which of the pack's crosshair sizes shows a blaster's spread: one size wider for each threshold that the spread, as a
 * multiple of the blaster's own base, has passed. A blaster without spread has no size to show.
 */
record SpreadCrosshair(List<Double> thresholds) {

    static final int SIZES = 5;

    SpreadCrosshair {
        thresholds = thresholds.stream().sorted().limit(SIZES - 1).toList();
    }

    Optional<Integer> size(double base, double spread) {
        if (base <= 0) {
            return Optional.empty();
        }
        return Optional.of((int) thresholds.stream().filter(threshold -> spread / base > threshold).count());
    }
}
