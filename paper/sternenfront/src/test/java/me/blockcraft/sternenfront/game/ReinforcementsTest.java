package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class ReinforcementsTest {

    @Test
    public void theFactionHoldingMorePostsDrainsTheOtherByTheDifference() {
        var tickets = new Reinforcements(50);

        tickets.bleed(Map.of(Faction.KOLONIEN, 3L));
        tickets.bleed(Map.of(Faction.KOLONIEN, 2L, Faction.HEGEMONIE, 1L));
        tickets.bleed(Map.of(Faction.KOLONIEN, 1L, Faction.HEGEMONIE, 1L));

        assertEquals(46, tickets.left(Faction.HEGEMONIE));
        assertEquals(50, tickets.left(Faction.KOLONIEN));
        assertEquals(Optional.of(Faction.KOLONIEN), tickets.leader());
    }

    @Test
    public void aFactionAtZeroIsDefeatedAndNeverGoesBelow() {
        var tickets = new Reinforcements(2);

        tickets.lose(Faction.HEGEMONIE, 1);
        assertEquals(Optional.empty(), tickets.defeated());
        tickets.lose(Faction.HEGEMONIE, 5);

        assertEquals(0, tickets.left(Faction.HEGEMONIE));
        assertEquals(Optional.of(Faction.HEGEMONIE), tickets.defeated());
    }

    @Test
    public void equalTicketsHaveNoLeader() {
        assertEquals(Optional.empty(), new Reinforcements(10).leader());
    }
}
