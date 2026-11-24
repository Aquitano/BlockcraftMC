package me.blockcraft.sternenfront.secret;

import me.blockcraft.sternenfront.arena.SecretSpots;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CourseAttemptTest {

    private final SecretSpots.Course course = new SecretSpots.Course(new BoundingBox(-2, 64, -2, 12, 76, 2),
            65, List.of(0.0, 5.0, 10.0), new Location(null, -1, 68, 0));

    @Test
    public void anEastwardInspectionUsesEveryCheckpointAndMeasuresTheFinish() {
        var attempt = new CourseAttempt(course, 1200);
        assertEquals(KesselRun.Step.NONE, attempt.move(at(-1), true, 0));
        assertEquals(KesselRun.Step.STARTED, attempt.move(at(1), true, 10));
        assertEquals(KesselRun.Step.CHECKPOINT, attempt.move(at(6), true, 40));
        assertEquals(KesselRun.Step.FINISHED, attempt.move(at(11), true, 70));
        assertEquals(60, attempt.run().result());
        assertFalse(attempt.run().running());
        assertNotEquals(Secret.KESSEL.item(), Secret.INSPECTION.item());
    }

    @Test
    public void losingFootEligibilityEndsTheRunAndCannotBridgeMovementOnRemount() {
        var attempt = new CourseAttempt(course, 1200);
        attempt.move(at(-1), true, 0);
        attempt.move(at(1), true, 10);
        assertEquals(KesselRun.Step.FAILED, attempt.move(at(6), false, 20));
        assertEquals(KesselRun.Step.NONE, attempt.move(at(11), true, 30));
        assertFalse(attempt.run().running());
        attempt.move(at(-1), true, 40);
        assertEquals(KesselRun.Step.STARTED, attempt.move(at(1), true, 50));
    }

    @Test
    public void teleportInterruptionDiscardsBothTheClockAndPreviousPosition() {
        var attempt = new CourseAttempt(course, 1200);
        attempt.move(at(-1), true, 0);
        attempt.move(at(1), true, 10);
        assertEquals(KesselRun.Step.FAILED, attempt.interrupt());
        assertEquals(KesselRun.Step.NONE, attempt.move(at(6), true, 20));
        assertEquals(KesselRun.Step.NONE, attempt.move(at(11), true, 30));
        assertFalse(attempt.run().running());
        attempt.interrupt();
        assertEquals(KesselRun.Step.NONE, attempt.move(at(-1), true, 40));
        assertEquals(KesselRun.Step.STARTED, attempt.move(at(1), true, 50));
    }

    @Test
    public void fallingBelowThePlatformsOrLeavingTheLaneEndsTheAttempt() {
        var attempt = new CourseAttempt(course, 1200);
        attempt.move(at(-1), true, 0);
        attempt.move(at(1), true, 10);
        assertEquals(KesselRun.Step.FAILED, attempt.move(new Vector(3, 64.5, 0), true, 20));
        attempt.move(at(-1), true, 30);
        attempt.move(at(1), true, 40);
        assertEquals(KesselRun.Step.FAILED, attempt.move(new Vector(3, 66, 3), true, 50));
    }

    private static Vector at(double x) {
        return new Vector(x, 66, 0);
    }
}
