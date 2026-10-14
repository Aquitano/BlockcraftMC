package me.blockcraft.bedwars.lobby;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class MapVoteTest {

    private static final List<String> POOL = List.of("mushroom", "medieval_mansion", "space");

    /** Always takes the last of the options, which keep their order. */
    private static final Random LAST = new Random() {
        @Override
        public int nextInt(int bound) {
            return bound - 1;
        }
    };

    private static MapVote allThree() {
        var vote = MapVote.among(POOL, POOL.size(), LAST);
        assertEquals(POOL.size(), vote.choices().size());
        return vote;
    }

    @Test
    public void theMostVotedMapWins() {
        var vote = allThree();
        vote.cast(UUID.randomUUID(), "space");
        vote.cast(UUID.randomUUID(), "mushroom");
        vote.cast(UUID.randomUUID(), "mushroom");

        assertEquals(Optional.of("mushroom"), vote.winner(LAST));
    }

    @Test
    public void aChangedVoteCountsOnceForTheNewMap() {
        var vote = allThree();
        var voter = UUID.randomUUID();
        vote.cast(voter, "space");

        assertEquals(Optional.of("space"), vote.cast(voter, "mushroom"));
        assertEquals(0, vote.votes("space"));
        assertEquals(1, vote.votes("mushroom"));
    }

    @Test
    public void withoutVotesChanceDecidesAmongTheChoices() {
        var vote = allThree();

        assertEquals(Optional.of(vote.choices().getLast()), vote.winner(LAST));
    }

    @Test
    public void onlyAsManyMapsAsThePoolHoldsAreDrawn() {
        assertEquals(1, MapVote.among(List.of("mushroom"), 3, LAST).choices().size());
    }
}
