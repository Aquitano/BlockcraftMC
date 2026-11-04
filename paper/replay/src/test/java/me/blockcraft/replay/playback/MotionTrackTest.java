package me.blockcraft.replay.playback;

import me.blockcraft.replay.data.ReplayEvent;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MotionTrackTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID ARROW = UUID.randomUUID();
    private static final UUID WORLD = UUID.randomUUID();
    private static final ReplayEvent.Animation SWING = new ReplayEvent.Animation(PLAYER, ReplayEvent.Motion.SWING_MAIN_HAND);

    @Test
    public void motionSpreadsOverTheSampleBeforeEachFrameAndTeleportsSnap() {
        var track = new MotionTrack(List.of(frame(0, 0, 170, List.of()), frame(2, 1, -170, List.of()),
                frame(10, 3, -170, List.of()), frame(12, 100, -170, List.of())));

        assertTrue(track.actors(-1).isEmpty());
        var between = track.actors(1).getFirst();
        assertEquals(0.5, between.x(), 1e-9);
        assertEquals(180, Math.abs(between.yaw()), 1e-4);
        assertEquals(0.5, track.props(1).getFirst().x(), 1e-9);
        assertEquals(1, track.actors(5).getFirst().x(), 1e-9);
        assertEquals(1, track.actors(8).getFirst().x(), 1e-9);
        assertEquals(2, track.actors(9).getFirst().x(), 1e-9);
        assertEquals(3, track.actors(11).getFirst().x(), 1e-9);
        assertEquals(100, track.actors(12).getFirst().x(), 1e-9);
    }

    @Test
    public void animationsBelongToTheFramesBetweenTwoMoments() {
        var track = new MotionTrack(List.of(frame(0, 0, 0, List.of(SWING)), frame(2, 0, 0, List.of(SWING)),
                frame(4, 0, 0, List.of())));

        assertEquals(List.of(SWING), track.animations(-1, 1));
        assertEquals(List.of(SWING), track.animations(1, 3));
        assertEquals(List.of(SWING), track.animations(0, 2));
        assertEquals(List.of(), track.animations(2, 4));
        assertEquals(List.of(), track.animations(3, 1));
    }

    private static ReplayEvent.Frame frame(int tick, double x, float yaw, List<ReplayEvent.Animation> animations) {
        return new ReplayEvent.Frame(tick,
                List.of(new ReplayEvent.Actor(PLAYER, WORLD, x, 64, 0, yaw, 0, "STANDING", List.of())),
                List.of(new ReplayEvent.Prop(ARROW, WORLD, x, 70, 0, yaw, 0)), List.of(), animations);
    }
}
