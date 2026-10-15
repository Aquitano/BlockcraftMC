package me.blockcraft.duell.game;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class DuelVoteTest {

    private static final Map<DuelMode, List<Integer>> PLAYABLE = Map.of(
            DuelMode.VS, List.of(1, 2, 3),
            DuelMode.SG, List.of(1, 2, 3),
            DuelMode.SUPERJUMP, List.of());

    /** Always takes the last of the tied options, which are in natural order. */
    private static final Random LAST = new Random() {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    };

    @Test
    public void theMostVotedModeAndMapWin() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.of(2)));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(1)));

        assertEquals(new DuelVote.Outcome(DuelMode.SG, 2), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void aTieFallsToTheRandom() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.of(1)));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(2)));

        assertEquals(new DuelVote.Outcome(DuelMode.SG, 1), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void mapVotesForALosingModeAreIgnored() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SG, OptionalInt.of(1)));

        assertEquals(new DuelVote.Outcome(DuelMode.VS, 3), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void withoutVotesOnlyModesWithAPlayableMapComeUp() {
        assertEquals(new DuelVote.Outcome(DuelMode.SG, 3), new DuelVote().tally(PLAYABLE, LAST));
    }

    @Test
    public void votesForUnavailableModesCannotOverrideVotesForPlayableModes() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUPERJUMP, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUPERJUMP, OptionalInt.empty()));
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(2)));

        assertEquals(new DuelVote.Outcome(DuelMode.VS, 2), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void votesOnlyForUnavailableModesFallBackToPlayableModes() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.SUPERJUMP, OptionalInt.empty()));

        assertEquals(new DuelVote.Outcome(DuelMode.SG, 3), vote.tally(PLAYABLE, LAST));
    }

    @Test
    public void withoutAnyPlayableMapsTheVotedModeKeepsItsFallbackMap() {
        var vote = new DuelVote();
        vote.cast(UUID.randomUUID(), new DuelVote.Choice(DuelMode.VS, OptionalInt.of(2)));

        assertEquals(new DuelVote.Outcome(DuelMode.VS, 1), vote.tally(Map.of(), LAST));
    }

    @Test
    public void withoutAnyPlayableMapsOrVotesAnyModeKeepsItsFallbackMap() {
        assertEquals(new DuelVote.Outcome(DuelMode.SUPERJUMP, 1), new DuelVote().tally(Map.of(), LAST));
    }
}
