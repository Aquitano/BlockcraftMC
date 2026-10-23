package me.blockcraft.duell.game;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DuelSeriesTest {

    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID SPECTATOR = UUID.randomUUID();
    private static final Map<DuelMode, List<Integer>> PLAYABLE = Map.of(
            DuelMode.VS, List.of(1, 2, 3),
            DuelMode.SG, List.of(1, 2),
            DuelMode.SUPERJUMP, List.of(2, 3),
            DuelMode.SUMO, List.of(1));
    private static final List<DuelVote.Outcome> TOUR = List.of(
            new DuelVote.Outcome(DuelMode.VS, 1),
            new DuelVote.Outcome(DuelMode.SG, 2),
            new DuelVote.Outcome(DuelMode.SUMO, 1));

    @Test
    public void singleAndInsufficientPlayableModesUseTheExistingTally() {
        var votes = new DuelVote();
        votes.cast(FIRST, new DuelVote.Choice(DuelMode.SG, OptionalInt.of(2)));
        var expected = List.of(votes.tally(PLAYABLE, new Random(42)));

        assertEquals(expected, DuelSeries.plan(DuelSeries.Format.SINGLE, votes, PLAYABLE, new Random(42)));
        assertEquals(expected, DuelSeries.plan(DuelSeries.Format.TOUR, votes,
                Map.of(DuelMode.SG, List.of(1, 2), DuelMode.SUMO, List.of(1), DuelMode.VS, List.of()),
                new Random(42)));
        assertEquals(List.of(new DuelVote.Outcome(DuelMode.SG, 1)),
                DuelSeries.plan(DuelSeries.Format.TOUR, votes, Map.of(), new Random(42)));
    }

    @Test
    public void distinctNominationsKeepTheirMapVotesAndTheRemainingModeIsRandom() {
        var votes = new DuelVote();
        votes.cast(FIRST, new DuelVote.Choice(DuelMode.VS, OptionalInt.of(3)));
        votes.cast(SECOND, new DuelVote.Choice(DuelMode.SG, OptionalInt.of(2)));

        var plan = DuelSeries.plan(DuelSeries.Format.TOUR, votes, PLAYABLE, new Random(42));

        assertEquals(3, plan.size());
        assertEquals(3, plan.stream().map(DuelVote.Outcome::mode).distinct().count());
        assertTrue(plan.contains(new DuelVote.Outcome(DuelMode.VS, 3)));
        assertTrue(plan.contains(new DuelVote.Outcome(DuelMode.SG, 2)));
        assertTrue(plan.stream().allMatch(outcome -> PLAYABLE.get(outcome.mode()).contains(outcome.map())));
    }

    @Test
    public void duplicateNominationsAndUnavailableModeOrMapVotesCannotRepeatOrBlockTheTour() {
        var votes = new DuelVote();
        votes.cast(FIRST, new DuelVote.Choice(DuelMode.VS, OptionalInt.of(99)));
        votes.cast(SECOND, new DuelVote.Choice(DuelMode.VS, OptionalInt.empty()));
        var playable = Map.of(DuelMode.VS, List.of(2), DuelMode.SG, List.of(1), DuelMode.SUMO, List.of(1));

        var plan = DuelSeries.plan(DuelSeries.Format.TOUR, votes, playable, new Random(42));

        assertEquals(3, plan.stream().map(DuelVote.Outcome::mode).distinct().count());
        assertTrue(plan.contains(new DuelVote.Outcome(DuelMode.VS, 2)));
        votes.cast(SECOND, new DuelVote.Choice(DuelMode.SUPERJUMP, OptionalInt.of(3)));
        assertEquals(new HashSet<>(plan), new HashSet<>(
                DuelSeries.plan(DuelSeries.Format.TOUR, votes, playable, new Random(42))));
    }

    @Test
    public void withoutNominationsRandomThreeAndTheirOrderVaryWithoutRepeatingModes() {
        var votes = new DuelVote();
        var random = new Random(42);
        var lineups = new HashSet<Set<DuelMode>>();
        var orders = new HashSet<List<DuelMode>>();
        for (int sample = 0; sample < 64; sample++) {
            var plan = DuelSeries.plan(DuelSeries.Format.TOUR, votes, PLAYABLE, random);
            var modes = plan.stream().map(DuelVote.Outcome::mode).toList();
            assertEquals(3, modes.size());
            assertEquals(3, modes.stream().distinct().count());
            assertTrue(plan.stream().allMatch(outcome -> PLAYABLE.get(outcome.mode()).contains(outcome.map())));
            lineups.add(new HashSet<>(modes));
            orders.add(modes);
        }
        assertEquals(4, lineups.size());
        assertTrue(orders.size() > 4);
        assertEquals(DuelSeries.plan(DuelSeries.Format.TOUR, votes, PLAYABLE, new Random(42)),
                DuelSeries.plan(DuelSeries.Format.TOUR, votes, PLAYABLE, new Random(42)));
    }

    @Test
    public void aForcedModeJoinsBothNominationsAndStillHonorsEachModesMapVotes() {
        var votes = new DuelVote();
        votes.cast(FIRST, new DuelVote.Choice(DuelMode.VS, OptionalInt.of(3)));
        votes.cast(SECOND, new DuelVote.Choice(DuelMode.SG, OptionalInt.of(2)));
        votes.force(DuelMode.SUMO);

        assertEquals(new HashSet<>(List.of(new DuelVote.Outcome(DuelMode.VS, 3),
                        new DuelVote.Outcome(DuelMode.SG, 2), new DuelVote.Outcome(DuelMode.SUMO, 1))),
                new HashSet<>(DuelSeries.plan(DuelSeries.Format.TOUR, votes, PLAYABLE, new Random(42))));
    }

    @Test
    public void theScheduleAndParticipantsStayFixedAndRejectInvalidSeries() {
        var participants = new ArrayList<>(List.of(FIRST, SECOND));
        var schedule = new ArrayList<>(TOUR);
        var series = new DuelSeries(participants, schedule);
        participants.clear();
        schedule.clear();

        assertEquals(List.of(FIRST, SECOND), series.participants());
        assertEquals(TOUR, series.schedule());
        assertThrows(UnsupportedOperationException.class, () -> series.schedule().clear());
        assertThrows(IllegalArgumentException.class, () -> new DuelSeries(List.of(FIRST), TOUR));
        assertThrows(IllegalArgumentException.class, () -> new DuelSeries(List.of(FIRST, FIRST), TOUR));
        assertThrows(IllegalArgumentException.class,
                () -> new DuelSeries(List.of(FIRST, SECOND), TOUR.subList(0, 2)));
        assertThrows(IllegalArgumentException.class,
                () -> new DuelSeries(List.of(FIRST, SECOND), List.of(TOUR.getFirst(), TOUR.getFirst(), TOUR.getLast())));
    }

    @Test
    public void everyGameCompletesOnceAndTheThirdStillRunsAfterTwoWins() {
        var series = new DuelSeries(List.of(FIRST, SECOND), TOUR);

        assertEquals(0, series.gameIndex());
        assertEquals(Optional.of(TOUR.getFirst()), series.current());
        assertFalse(series.complete(1, Optional.of(FIRST)));
        assertFalse(series.complete(0, Optional.of(SPECTATOR)));
        assertTrue(series.complete(0, Optional.of(FIRST)));
        assertFalse(series.complete(0, Optional.of(SECOND)));
        assertTrue(series.complete(1, Optional.of(FIRST)));
        assertFalse(series.finished());
        assertTrue(series.winner().isEmpty());
        assertEquals(Optional.of(TOUR.getLast()), series.current());
        assertTrue(series.complete(2, Optional.of(SECOND)));

        assertTrue(series.finished());
        assertEquals(3, series.gameIndex());
        assertTrue(series.current().isEmpty());
        assertEquals(2, series.wins(FIRST));
        assertEquals(1, series.wins(SECOND));
        assertEquals(0, series.wins(SPECTATOR));
        assertEquals(Optional.of(FIRST), series.winner());
        assertFalse(series.complete(2, Optional.of(SECOND)));
        assertFalse(series.complete(3, Optional.of(SECOND)));
        assertEquals(1, series.wins(SECOND));
    }

    @Test
    public void drawsConsumeTheirSlotAndTheFinalAggregateCanBeTied() {
        var series = new DuelSeries(List.of(FIRST, SECOND), TOUR);
        assertTrue(series.complete(0, Optional.of(FIRST)));
        assertTrue(series.complete(1, Optional.empty()));
        assertTrue(series.complete(2, Optional.of(SECOND)));

        assertTrue(series.finished());
        assertEquals(1, series.wins(FIRST));
        assertEquals(1, series.wins(SECOND));
        assertTrue(series.winner().isEmpty());
        var single = new DuelSeries(List.of(FIRST, SECOND), List.of(TOUR.getFirst()));
        assertTrue(single.complete(0, Optional.empty()));
        assertTrue(single.finished());
        assertTrue(single.winner().isEmpty());
        assertFalse(single.complete(0, Optional.of(FIRST)));
    }

    @Test
    public void aCompletionFromThePreviousSeriesCannotAdvanceTheRematch() {
        var players = List.of(UUID.randomUUID(), UUID.randomUUID());
        var schedule = List.of(new DuelVote.Outcome(DuelMode.VS, 1));
        var previous = new DuelSeries(players, schedule);
        var rematch = new DuelSeries(players, schedule);
        assertFalse(rematch.complete(previous, 0, Optional.of(players.getFirst())));
        assertEquals(0, rematch.gameIndex());
        assertTrue(rematch.complete(rematch, 0, Optional.of(players.getFirst())));
        assertFalse(rematch.complete(rematch, 0, Optional.of(players.getLast())));
        assertEquals(1, rematch.wins(players.getFirst()));
    }
}
