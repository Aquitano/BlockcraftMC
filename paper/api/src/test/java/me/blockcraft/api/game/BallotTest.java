package me.blockcraft.api.game;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class BallotTest {

    private static final List<String> MAPS = List.of("mushroom", "medieval_mansion", "space");

    /** Always takes the last of the options, which keep their order. */
    private static final Random LAST = new Random() {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    };

    /** Always takes the first. */
    private static final Random FIRST = new Random() {
        @Override
        public int nextInt(int bound) {
            return 0;
        }
    };

    @Test
    public void theMostVotedOptionWins() {
        var ballot = new Ballot<String>();
        ballot.cast(UUID.randomUUID(), Optional.of("space"));
        ballot.cast(UUID.randomUUID(), Optional.of("mushroom"));
        ballot.cast(UUID.randomUUID(), Optional.of("mushroom"));

        assertEquals(Optional.of("mushroom"), ballot.winner(MAPS, LAST));
    }

    @Test
    public void aChangedVoteCountsOnceForTheNewOption() {
        var ballot = new Ballot<String>();
        var voter = UUID.randomUUID();
        ballot.cast(voter, Optional.of("space"));

        assertEquals(Optional.of(Optional.of("space")), ballot.cast(voter, Optional.of("mushroom")));
        assertEquals(0, ballot.votes(Optional.of("space")));
        assertEquals(1, ballot.votes(Optional.of("mushroom")));
    }

    @Test
    public void withoutVotesChanceDecidesAmongTheOptions() {
        assertEquals(Optional.of("space"), new Ballot<String>().winner(MAPS, LAST));
    }

    @Test
    public void aWinningVoteForChancePicksAnyOption() {
        var ballot = new Ballot<String>();
        ballot.cast(UUID.randomUUID(), Optional.empty());
        ballot.cast(UUID.randomUUID(), Optional.empty());
        ballot.cast(UUID.randomUUID(), Optional.of("space"));

        assertEquals(Optional.of("mushroom"), ballot.winner(MAPS, FIRST));
    }

    @Test
    public void votesForOptionsNoLongerUpDoNotCount() {
        var ballot = new Ballot<String>();
        ballot.cast(UUID.randomUUID(), Optional.of("castle"));
        ballot.cast(UUID.randomUUID(), Optional.of("castle"));
        ballot.cast(UUID.randomUUID(), Optional.of("space"));

        assertEquals(Optional.of("space"), ballot.winner(MAPS, FIRST));
    }
}
