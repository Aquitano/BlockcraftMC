package me.blockcraft.ttt.game;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class FactionTest {

    @Test
    public void nobodyWinsWhileBothSidesLive() {
        assertEquals(Optional.empty(), Faction.winner(List.of(Role.TRAITOR, Role.INNOCENT)));
    }

    @Test
    public void theInnocentsWinWithoutTraitors() {
        assertEquals(Optional.of(Faction.INNOCENTS), Faction.winner(List.of(Role.DETECTIVE, Role.INNOCENT)));
        assertEquals(Optional.of(Faction.INNOCENTS), Faction.winner(List.of()));
    }

    @Test
    public void theTraitorsWinAlone() {
        assertEquals(Optional.of(Faction.TRAITORS), Faction.winner(List.of(Role.TRAITOR, Role.TRAITOR)));
    }

    @Test
    public void killsEarnShopPointsOnlyAcrossSides() {
        assertEquals(1, Role.TRAITOR.shopPointsFor(Role.DETECTIVE));
        assertEquals(0, Role.TRAITOR.shopPointsFor(Role.TRAITOR));
        assertEquals(1, Role.DETECTIVE.shopPointsFor(Role.TRAITOR));
        assertEquals(0, Role.DETECTIVE.shopPointsFor(Role.INNOCENT));
        assertEquals(0, Role.INNOCENT.shopPointsFor(Role.TRAITOR));
    }
}
