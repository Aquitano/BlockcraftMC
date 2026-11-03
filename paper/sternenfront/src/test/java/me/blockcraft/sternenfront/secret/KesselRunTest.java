package me.blockcraft.sternenfront.secret;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class KesselRunTest {

    private static final List<Double> WESTWARD = List.of(-25.5, -34.5, -54.5, -60.5);

    @Test
    public void theClockRunsFromTheStartThroughEveryCheckpointToTheFinish() {
        var run = new KesselRun(WESTWARD, 1200);

        assertEquals(KesselRun.Step.STARTED, run.move(-25.2, -25.8, true, 100));
        assertEquals(KesselRun.Step.CHECKPOINT, run.move(-34.3, -34.9, true, 130));
        assertEquals(KesselRun.Step.CHECKPOINT, run.move(-54, -55, true, 170));
        assertEquals(KesselRun.Step.FINISHED, run.move(-60, -61, true, 196));

        assertEquals(96, run.result());
        assertFalse(run.running());
    }

    @Test
    public void aSkippedCheckpointKeepsTheFinishShut() {
        var run = new KesselRun(WESTWARD, 1200);
        run.move(-25, -26, true, 0);
        run.move(-34, -35, true, 20);

        assertEquals(KesselRun.Step.NONE, run.move(-60, -61, true, 60));
        assertEquals(2, run.passed());
    }

    @Test
    public void thePitEndsTheRunAndTheStartGateStartsAfresh() {
        var run = new KesselRun(WESTWARD, 1200);
        run.move(-25, -26, true, 0);

        assertEquals(KesselRun.Step.FAILED, run.move(-30, -30.5, false, 30));
        assertEquals(KesselRun.Step.NONE, run.move(-30.5, -31, true, 31));
        assertEquals(KesselRun.Step.NONE, run.move(-26, -25, true, 80));
        assertEquals(KesselRun.Step.STARTED, run.move(-25, -26, true, 90));
        assertEquals(10, run.elapsed(100));
    }

    @Test
    public void aRunPastTheLimitFails() {
        var run = new KesselRun(WESTWARD, 200);
        run.move(-25, -26, true, 0);

        assertEquals(KesselRun.Step.FAILED, run.move(-30, -30.5, true, 201));
    }
}
