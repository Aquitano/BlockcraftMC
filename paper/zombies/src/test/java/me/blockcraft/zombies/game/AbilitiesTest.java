package me.blockcraft.zombies.game;

import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.*;

public class AbilitiesTest {

    private static final Vector NORTH = new Vector(0, 0, -1);

    @Test
    public void theShieldTakesHitsFromTheFrontButNotFromTheSideOrBack() {
        assertTrue(ShieldBearer.blocks(NORTH, new Vector(0, 2, -8), 60));
        assertTrue(ShieldBearer.blocks(NORTH, new Vector(4, 0, -8), 60));
        assertFalse(ShieldBearer.blocks(NORTH, new Vector(8, 0, 0), 60));
        assertFalse(ShieldBearer.blocks(NORTH, new Vector(0, 0, 8), 60));
    }

    @Test
    public void aShadowWalkerIsWatchedOnlyWithinTheAngleOfTheFightersView() {
        var eye = new Vector(0, 1.6, 0);
        assertTrue(ShadowWalker.watches(eye, NORTH, new Vector(1, 1, -10), 30));
        assertFalse(ShadowWalker.watches(eye, NORTH, new Vector(10, 1, -10), 30));
        assertFalse(ShadowWalker.watches(eye, NORTH, new Vector(0, 1, 10), 30));
    }

    @Test
    public void aMolotovLandsOnItsTargetAfterItsFlightTime() {
        var from = new Vector(0, 1.7, 0);
        var to = new Vector(9, 0, -5);
        var velocity = Arsonist.launch(from, to, 22);
        var position = from.clone();
        for (int tick = 0; tick < 22; tick++) {
            velocity.setY(velocity.getY() - Arsonist.GRAVITY).multiply(Arsonist.DRAG);
            position.add(velocity);
        }
        assertEquals(0, position.distance(to), 1e-6);
    }

    @Test
    public void aZapJumpsToTheClosestFightersInRangeUpToItsJumps() {
        var first = new Vector(0, 0, 0);
        var near = new Vector(3, 0, 0);
        var next = new Vector(6, 0, 0);
        var far = new Vector(20, 0, 0);
        var fighters = List.of(far, next, first, near);
        assertEquals(List.of(first, near, next), Electric.chain(first, fighters, Function.identity(), 4, 2));
        assertEquals(List.of(first, near), Electric.chain(first, fighters, Function.identity(), 4, 1));
        assertEquals(List.of(far), Electric.chain(far, fighters, Function.identity(), 4, 2));
    }
}
