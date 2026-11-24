package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class PriorityPostTest {

    private static final Optional<Faction> KOLONIEN = Optional.of(Faction.KOLONIEN);
    private static final Optional<Faction> HEGEMONIE = Optional.of(Faction.HEGEMONIE);
    private static final Optional<Faction> NEUTRAL = Optional.empty();

    private static Reinforcements hegemonieTrails() {
        var tickets = new Reinforcements(100);
        tickets.lose(Faction.HEGEMONIE, 10);
        return tickets;
    }

    @Test
    public void theTrailingFactionHasFewerTicketsThenFewerPostsThenIsTheKolonien() {
        assertEquals(Faction.HEGEMONIE, PriorityPost.trailing(hegemonieTrails(), List.of(NEUTRAL, HEGEMONIE, HEGEMONIE)));
        assertEquals(Faction.KOLONIEN, PriorityPost.trailing(new Reinforcements(100), List.of(NEUTRAL, HEGEMONIE, NEUTRAL)));
        assertEquals(Faction.HEGEMONIE, PriorityPost.trailing(new Reinforcements(100), List.of(NEUTRAL, KOLONIEN, NEUTRAL)));
        assertEquals(Faction.KOLONIEN, PriorityPost.trailing(new Reinforcements(100), List.of(KOLONIEN, NEUTRAL, HEGEMONIE)));
    }

    @Test
    public void thePostIsTheEnemysNearestTheCentreThenANeutralOneThenTheCentre() {
        assertEquals(1, PriorityPost.pick(hegemonieTrails(), List.of(KOLONIEN, KOLONIEN, NEUTRAL)).post());
        assertEquals(2, PriorityPost.pick(hegemonieTrails(), List.of(NEUTRAL, HEGEMONIE, KOLONIEN)).post());
        assertEquals(1, PriorityPost.pick(hegemonieTrails(), List.of(NEUTRAL, NEUTRAL, HEGEMONIE)).post());
        assertEquals(0, PriorityPost.pick(hegemonieTrails(), List.of(NEUTRAL, HEGEMONIE, HEGEMONIE)).post());
        assertEquals(1, PriorityPost.pick(hegemonieTrails(), List.of(HEGEMONIE, HEGEMONIE, HEGEMONIE)).post());
    }
}
