package me.blockcraft.duell.game;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SumoMatchTest {

    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID SPECTATOR = UUID.randomUUID();

    @Test
    public void startsOnceWithExactlyTwoDistinctPlayersAndAPositiveTarget() {
        assertThrows(IllegalArgumentException.class, () -> new SumoMatch(0));
        var match = new SumoMatch(3);

        match.ready();
        assertEquals(SumoMatch.State.WAITING, match.state());
        assertFalse(match.fall(FIRST));
        assertFalse(match.start(List.of()));
        assertFalse(match.start(List.of(FIRST)));
        assertFalse(match.start(List.of(FIRST, FIRST)));
        assertFalse(match.start(List.of(FIRST, SECOND, SPECTATOR)));
        assertFalse(match.start(Arrays.asList(FIRST, null)));
        assertTrue(match.start(List.of(FIRST, SECOND)));
        assertFalse(match.start(List.of(FIRST, SECOND)));
        assertEquals(SumoMatch.State.ACTIVE, match.state());
        assertTrue(match.winner().isEmpty());
    }

    @Test
    public void awardsTheOpponentOnceAndBlocksBothPlayersUntilReady() {
        var match = new SumoMatch(3);
        match.start(List.of(FIRST, SECOND));

        assertFalse(match.fall(SPECTATOR));
        assertFalse(match.fall(null));
        assertTrue(match.fall(FIRST));
        assertEquals(0, match.score(FIRST));
        assertEquals(1, match.score(SECOND));
        assertEquals(0, match.score(SPECTATOR));
        assertEquals(SumoMatch.State.RESETTING, match.state());
        assertFalse(match.fall(FIRST));
        assertFalse(match.fall(SECOND));
        match.ready();
        assertTrue(match.fall(SECOND));
        match.ready();
        assertEquals(1, match.score(FIRST));
        assertEquals(1, match.score(SECOND));
        assertEquals(SumoMatch.State.ACTIVE, match.state());
    }

    @Test
    public void reachingTheTargetFinishesOnceAndReadyCannotRestartIt() {
        var match = new SumoMatch(2);
        match.start(List.of(FIRST, SECOND));
        match.fall(FIRST);
        match.ready();

        assertTrue(match.fall(FIRST));
        assertEquals(SECOND, match.winner().orElseThrow());
        assertEquals(SumoMatch.State.FINISHED, match.state());
        match.ready();
        assertFalse(match.fall(SECOND));
        assertFalse(match.fall(FIRST));
        assertFalse(match.start(List.of(FIRST, SECOND)));
        assertEquals(2, match.score(SECOND));
        assertEquals(0, match.score(FIRST));
    }
}
