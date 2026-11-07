package me.blockcraft.sternenfront.game;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PoseHistoryTest {

    private static PoseHistory.Pose standingAt(double x) {
        return new PoseHistory.Pose(new BoundingBox(x, 0, 0, x + 0.6, 1.8, 0.6), 1.62);
    }

    @Test
    public void aTickBetweenTwoRecordsLiesBetweenTheirBoxes() {
        var history = new PoseHistory(6, 10, standingAt(0));
        history.record(11, new PoseHistory.Pose(new BoundingBox(1, 0, 0, 1.6, 1.5, 0.6), 1.27));
        var between = history.at(10.25);
        assertEquals(0.25, between.box().getMinX(), 1e-9);
        assertEquals(0.85, between.box().getMaxX(), 1e-9);
        assertEquals(1.725, between.box().getMaxY(), 1e-9);
        assertEquals(1.5325, between.eyeY(), 1e-9);
    }

    @Test
    public void theOldestRecordsAreOverwrittenAndTicksOutsideClampToWhatIsKept() {
        var history = new PoseHistory(3, 10, standingAt(10));
        for (int tick = 11; tick <= 14; tick++) {
            history.record(tick, standingAt(tick));
        }
        assertEquals(12, history.at(5).box().getMinX(), 1e-9);
        assertEquals(14, history.at(20).box().getMinX(), 1e-9);
        assertEquals(13, history.at(13).box().getMinX(), 1e-9);
    }

    @Test
    public void aMissedTickStartsTheHistoryOver() {
        var history = new PoseHistory(6, 10, standingAt(10));
        history.record(15, standingAt(15));
        assertEquals(15, history.at(10).box().getMinX(), 1e-9);
    }

    @Test
    public void aBoltMeetsAStrafingTargetWhereItWasButNotWhereItIsNow() {
        var history = new PoseHistory(6, 10, standingAt(0));
        history.record(11, standingAt(0.25));
        history.record(12, standingAt(0.5));
        var start = new Vector(0.1, 1, -20);
        var forward = new Vector(0, 0, 1);
        assertTrue(history.at(10.5).hit(start, forward, 30, 0.1).isPresent());
        assertFalse(history.at(12).hit(start, forward, 30, 0.1).isPresent());
    }
}
