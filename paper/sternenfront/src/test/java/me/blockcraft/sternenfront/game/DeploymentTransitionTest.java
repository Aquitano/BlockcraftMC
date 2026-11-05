package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DeploymentTransitionTest {
    @Test
    public void initialArrivalDeploysWhileBlackWithoutWaitingThroughAnotherFadeOut() {
        var frames = new ArrayList<Double>();
        var teleports = new AtomicInteger();
        var transition = DeploymentTransition.arrival(6, teleports::incrementAndGet, frames::add);
        transition.tick();
        assertEquals(1, teleports.get());
        assertEquals(1.0, frames.getFirst(), 0);
        for (int tick = 0; tick < 8; tick++) transition.tick();
        assertEquals(1, teleports.get());
        assertEquals(0.0, frames.getLast(), 0);
    }

    @Test
    public void teleportOccursOnceAfterTwoFullyDarkFramesAndThenFadesBackIn() {
        var frames = new ArrayList<Double>();
        var teleports = new AtomicInteger();
        var transition = new DeploymentTransition(6, () -> {
            assertEquals(1.0, frames.get(6), 0);
            assertEquals(1.0, frames.get(7), 0);
            assertEquals(1.0, frames.getLast(), 0);
            teleports.incrementAndGet();
        }, frames::add);
        for (int tick = 0; tick < 20; tick++) transition.tick();
        assertEquals(1, teleports.get());
        assertEquals(0.0, frames.getFirst(), 0);
        assertEquals(0.0, frames.getLast(), 0);
        assertTrue(transition.tick());
    }

    @Test
    public void cancellingTheTransitionPreventsAQueuedTeleport() {
        var teleports = new AtomicInteger();
        var transition = new DeploymentTransition(6, teleports::incrementAndGet, ignored -> {});
        for (int tick = 0; tick < 7; tick++) transition.tick();
        transition.close();
        for (int tick = 0; tick < 20; tick++) assertTrue(transition.tick());
        assertEquals(0, teleports.get());
    }
}
