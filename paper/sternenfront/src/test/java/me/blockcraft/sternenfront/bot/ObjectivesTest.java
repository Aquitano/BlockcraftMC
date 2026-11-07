package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.bot.Objectives.Hold;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ObjectivesTest {

    @Test
    public void aTeamThatTookItsPostPushesOnToTheNextContestableOne() {
        assertEquals(1, Objectives.choose(List.of(
                new Objectives.Post(0, Hold.HELD, 2, 90, 0, 0),
                new Objectives.Post(1, Hold.OPEN, 44, 82, 0, 0),
                new Objectives.Post(2, Hold.OPEN, 88, 90, 0, 0)), 0, 25).orElseThrow());
    }

    @Test
    public void teamsSpreadAcrossObjectivesAndReinforceAPostTheEnemyIsTaking() {
        assertEquals(1, Objectives.choose(List.of(
                new Objectives.Post(0, Hold.OPEN, 20, 80, 1, 0),
                new Objectives.Post(1, Hold.OPEN, 35, 80, 0, 0)), -1, 25).orElseThrow());
        assertEquals(2, Objectives.choose(List.of(
                new Objectives.Post(0, Hold.OPEN, 20, 80, 0, 0),
                new Objectives.Post(2, Hold.THREATENED, 50, 80, 0, 0)), -1, 25).orElseThrow());
    }

    @Test
    public void aTeamKeepsItsObjectiveUnlessAnotherIsClearlyCloser() {
        var current = new Objectives.Post(1, Hold.OPEN, 50, 80, 0, 0);
        assertEquals(1, Objectives.choose(List.of(new Objectives.Post(0, Hold.OPEN, 40, 80, 0, 0), current), 1, 25).orElseThrow());
        assertEquals(0, Objectives.choose(List.of(new Objectives.Post(0, Hold.OPEN, 20, 80, 0, 0), current), 1, 25).orElseThrow());
    }

    @Test
    public void withEveryPostHeldTeamsHoldTheOneTheEnemyReachesFirst() {
        assertEquals(1, Objectives.choose(List.of(
                new Objectives.Post(0, Hold.HELD, 2, 93, 0, 0),
                new Objectives.Post(1, Hold.HELD, 44, 82, 0, 0)), 0, 25).orElseThrow());
    }

    @Test
    public void thePriorityPostDrawsTeamsUntilItIsCrowded() {
        assertEquals(1, Objectives.choose(List.of(
                new Objectives.Post(0, Hold.OPEN, 20, 80, 0, 0),
                new Objectives.Post(1, Hold.OPEN, 70, 80, 0, 60)), -1, 25).orElseThrow());
        assertEquals(0, Objectives.choose(List.of(
                new Objectives.Post(0, Hold.OPEN, 20, 80, 0, 0),
                new Objectives.Post(1, Hold.OPEN, 70, 80, 2, 60)), -1, 25).orElseThrow());
    }
}
