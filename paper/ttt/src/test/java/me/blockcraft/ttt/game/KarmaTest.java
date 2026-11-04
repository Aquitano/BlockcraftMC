package me.blockcraft.ttt.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KarmaTest {

    private static final Karma KARMA = new Karma(1000, 1000, 100, 20, 30, 0.5);

    @Test
    public void killingYourOwnSideCostsKarma() {
        assertEquals(900, KARMA.afterKill(1000, Role.INNOCENT, Role.DETECTIVE));
        assertEquals(900, KARMA.afterKill(1000, Role.TRAITOR, Role.TRAITOR));
        assertEquals(0, KARMA.afterKill(50, Role.DETECTIVE, Role.INNOCENT));
    }

    @Test
    public void killingATraitorEarnsKarmaUpToTheMaximum() {
        assertEquals(920, KARMA.afterKill(900, Role.INNOCENT, Role.TRAITOR));
        assertEquals(1000, KARMA.afterKill(990, Role.DETECTIVE, Role.TRAITOR));
        assertEquals(900, KARMA.afterKill(900, Role.TRAITOR, Role.INNOCENT));
    }

    @Test
    public void onlyACleanRoundEarnsTheBonus() {
        assertEquals(930, KARMA.afterRound(900, false));
        assertEquals(900, KARMA.afterRound(900, true));
    }

    @Test
    public void damageScalesWithKarmaDownToTheFloor() {
        assertEquals(1.0, KARMA.damageFactor(1000), 1e-9);
        assertEquals(0.8, KARMA.damageFactor(800), 1e-9);
        assertEquals(0.5, KARMA.damageFactor(100), 1e-9);
    }
}
