package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AbilityHintsTest {

    @Test
    public void interruptedHintsRestartWithoutRepeatingTheControlsMessage() {
        var hints = new AbilityHints();
        var keys = List.of("shield", "grenade");
        assertTrue(hints.start(keys, 100));
        assertEquals(0, hints.index(140, 60).orElseThrow());
        hints.interrupt();
        assertFalse(hints.start(keys, 300));
        assertEquals(0, hints.index(300, 60).orElseThrow());
        assertEquals(1, hints.index(360, 60).orElseThrow());
    }

    @Test
    public void completedHintsStaySuppressedUntilAbilitiesChange() {
        var hints = new AbilityHints();
        hints.start(List.of("shield", "grenade"), 100);
        assertTrue(hints.index(220, 60).isEmpty());
        hints.interrupt();
        assertFalse(hints.start(List.of("shield", "grenade"), 400));
        assertTrue(hints.index(400, 60).isEmpty());
        assertTrue(hints.start(List.of("jump", "grenade"), 500));
        assertEquals(0, hints.index(500, 60).orElseThrow());
    }
}
