package me.blockcraft.sternenfront.hero;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

final class BlastPattern {

    record Impact(double x, double z) { }

    private BlastPattern() { }

    static List<Impact> plan(int blasts, double radius, RandomGenerator random) {
        var impacts = new ArrayList<Impact>();
        if (blasts > 0) {
            impacts.add(new Impact(0, 0));
        }
        for (int index = 1; index < blasts; index++) {
            impacts.add(new Impact(random.nextDouble(-radius, radius), random.nextDouble(-radius, radius)));
        }
        return List.copyOf(impacts);
    }
}
