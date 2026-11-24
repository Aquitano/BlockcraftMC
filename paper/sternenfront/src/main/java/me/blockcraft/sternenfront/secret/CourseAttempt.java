package me.blockcraft.sternenfront.secret;

import me.blockcraft.sternenfront.arena.SecretSpots;
import org.bukkit.util.Vector;

final class CourseAttempt {

    private final SecretSpots.Course course;
    private final KesselRun run;
    private Vector last;

    CourseAttempt(SecretSpots.Course course, long limitTicks) {
        this.course = course;
        this.run = new KesselRun(course.gates(), limitTicks);
    }

    KesselRun.Step move(Vector at, boolean eligible, long tick) {
        if (!eligible) {
            return interrupt();
        }
        var from = last;
        last = at.clone();
        if (from == null) {
            return KesselRun.Step.NONE;
        }
        return run.move(from.getX(), at.getX(), course.lane().contains(at) && at.getY() >= course.pitY(), tick);
    }

    KesselRun.Step interrupt() {
        last = null;
        return run.interrupt();
    }

    KesselRun run() {
        return run;
    }
}
