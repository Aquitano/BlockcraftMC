package me.blockcraft.sternenfront.hero;

import me.blockcraft.sternenfront.game.Faction;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HeroRosterTest {

    @Test
    public void aFactionFieldsOnlyItsOwnHeroesUpToItsSlots() {
        var roster = new HeroRoster(1);

        assertFalse(roster.open(Faction.KOLONIEN, Hero.SITH));
        assertTrue(roster.enlist(UUID.randomUUID(), Faction.KOLONIEN, Hero.JEDI));
        assertFalse(roster.open(Faction.KOLONIEN, Hero.BOUNTY_HUNTER));
        assertTrue(roster.open(Faction.HEGEMONIE, Hero.SITH));
    }

    @Test
    public void aFallenHeroFreesTheSlotAndTheChampionTakesNone() {
        var roster = new HeroRoster(1);
        var jedi = UUID.randomUUID();
        roster.enlist(jedi, Faction.KOLONIEN, Hero.JEDI);

        assertTrue(roster.enlist(UUID.randomUUID(), Faction.KOLONIEN, Hero.CHAMPION));
        roster.discharge(jedi);

        assertTrue(roster.open(Faction.KOLONIEN, Hero.BOUNTY_HUNTER));
    }

    @Test
    public void theSameHeroFightsOnlyOnceEvenWithMoreSlots() {
        var roster = new HeroRoster(2);
        roster.enlist(UUID.randomUUID(), Faction.HEGEMONIE, Hero.SITH);

        assertFalse(roster.open(Faction.HEGEMONIE, Hero.SITH));
        assertTrue(roster.open(Faction.HEGEMONIE, Hero.COMMANDER));
    }
}
