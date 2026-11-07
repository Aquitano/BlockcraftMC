package me.blockcraft.sternenfront.game;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReinforcementsTest {

    @Test
    public void theFactionHoldingMorePostsDrainsTheOtherByTheDifference() {
        var tickets = new Reinforcements(50);

        var kolonien = Optional.of(Faction.KOLONIEN);
        var hegemonie = Optional.of(Faction.HEGEMONIE);
        tickets.bleed(List.of(kolonien, kolonien, kolonien), post -> 1);
        tickets.bleed(List.of(kolonien, kolonien, hegemonie), post -> 1);
        tickets.bleed(List.of(kolonien, Optional.empty(), hegemonie), post -> 1);

        assertEquals(46, tickets.left(Faction.HEGEMONIE));
        assertEquals(50, tickets.left(Faction.KOLONIEN));
        assertEquals(Optional.of(Faction.KOLONIEN), tickets.leader());
    }

    @Test
    public void aWeightedPostCountsForMoreAndCanTurnTheBleed() {
        var tickets = new Reinforcements(50);
        var owners = List.of(Optional.of(Faction.HEGEMONIE), Optional.of(Faction.KOLONIEN), Optional.of(Faction.HEGEMONIE));

        tickets.bleed(owners, post -> post == 1 ? 3 : 1);
        assertEquals(49, tickets.left(Faction.HEGEMONIE));
        assertEquals(50, tickets.left(Faction.KOLONIEN));

        tickets.bleed(owners, post -> 2);
        assertEquals(48, tickets.left(Faction.KOLONIEN));
    }

    @Test
    public void aFactionAtZeroIsDefeatedAndNeverGoesBelow() {
        var tickets = new Reinforcements(2);

        tickets.lose(Faction.HEGEMONIE, 1);
        assertFalse(tickets.exhausted());
        tickets.lose(Faction.HEGEMONIE, 5);

        assertEquals(0, tickets.left(Faction.HEGEMONIE));
        assertTrue(tickets.exhausted());
        assertEquals(Optional.of(Faction.KOLONIEN), tickets.leader());
    }

    @Test
    public void bothFactionsAtZeroIsADraw() {
        var tickets = new Reinforcements(1);

        tickets.lose(Faction.KOLONIEN, 1);
        tickets.lose(Faction.HEGEMONIE, 1);

        assertTrue(tickets.exhausted());
        assertEquals(Optional.empty(), tickets.leader());
    }

    @Test
    public void equalTicketsHaveNoLeader() {
        assertEquals(Optional.empty(), new Reinforcements(10).leader());
    }
}
