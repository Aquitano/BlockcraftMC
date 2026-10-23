package me.blockcraft.api.game.rush;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RushMatchTest {

    private static final UUID RED = UUID.randomUUID();
    private static final UUID BLUE = UUID.randomUUID();
    private static final UUID SPECTATOR = UUID.randomUUID();

    @Test
    public void needsExactlyTwoDistinctPlayersBeforeBedsCanScore() {
        var match = new RushMatch(5);

        assertFalse(match.start(List.of(RED)));
        assertFalse(match.start(List.of(RED, RED)));
        assertFalse(match.start(List.of(RED, BLUE, SPECTATOR)));
        assertFalse(match.score(RED, 1));
        assertTrue(match.start(List.of(RED, BLUE)));
        assertFalse(match.score(RED, 0));
        assertFalse(match.score(SPECTATOR, 0));
        assertFalse(match.score(RED, -1));
        assertFalse(match.start(List.of(RED, BLUE)));
        assertEquals(0, match.score(0));
    }

    @Test
    public void locksBothBedsUntilResetCompletesAndKeepsBothScores() {
        var match = new RushMatch(5);
        match.start(List.of(RED, BLUE));

        assertTrue(match.score(RED, 1));
        assertEquals(RushMatch.State.RESETTING, match.state());
        assertFalse(match.score(RED, 1));
        assertFalse(match.score(BLUE, 0));
        match.ready();
        assertTrue(match.score(BLUE, 0));
        match.ready();
        assertEquals(1, match.score(0));
        assertEquals(1, match.score(1));
        assertEquals(RushMatch.State.ACTIVE, match.state());
    }

    @Test
    public void firstToTheTargetWinsOnceAndCannotBeReactivated() {
        var match = new RushMatch(2);
        match.start(List.of(RED, BLUE));
        match.score(RED, 1);
        match.ready();

        assertTrue(match.score(RED, 1));
        assertEquals(RED, match.winner().orElseThrow());
        assertEquals(RushMatch.State.FINISHED, match.state());
        match.ready();
        assertFalse(match.score(BLUE, 0));
        assertFalse(match.leave(RED));
        assertEquals(2, match.score(0));
    }

    @Test
    public void leavingDuringPlayOrResetForfeitsButSpectatorsDoNot() {
        for (boolean resetting : new boolean[] { false, true }) {
            var match = new RushMatch(5);
            match.start(List.of(RED, BLUE));
            if (resetting) {
                match.score(RED, 1);
            }

            assertFalse(match.leave(SPECTATOR));
            assertTrue(match.leave(RED));
            assertEquals(BLUE, match.winner().orElseThrow());
            match.ready();
            assertEquals(RushMatch.State.FINISHED, match.state());
        }
    }

    @Test
    public void rematchRequiresBothParticipantsAfterTheMatchFinishes() {
        var match = new RushMatch(1);
        match.start(List.of(RED, BLUE));

        assertFalse(match.requestRematch(RED));
        match.score(RED, 1);
        assertFalse(match.requestRematch(SPECTATOR));
        assertFalse(match.requestRematch(RED));
        assertFalse(match.requestRematch(RED));
        assertTrue(match.requestRematch(BLUE));
        assertFalse(match.requestRematch(BLUE));

        var next = new RushMatch(1);
        assertTrue(next.start(List.of(RED, BLUE)));
        assertEquals(0, next.score(0));
        assertEquals(0, next.score(1));
        assertTrue(next.winner().isEmpty());
        assertFalse(next.requestRematch(RED));
    }

    @Test
    public void shutdownPreventsPendingResetOrScoreFromRestartingPlay() {
        var match = new RushMatch(5);
        match.start(List.of(RED, BLUE));
        match.score(RED, 1);

        match.stop();
        match.ready();

        assertFalse(match.score(BLUE, 0));
        assertFalse(match.leave(RED));
        assertEquals(RushMatch.State.STOPPED, match.state());
    }
}
