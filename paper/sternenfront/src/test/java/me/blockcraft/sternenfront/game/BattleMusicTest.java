package me.blockcraft.sternenfront.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BattleMusicTest {

    @Test
    public void musicMovesFromOpeningToBattleAndEscalatesOnTimeOrTickets() {
        assertEquals(BattleMusic.Cue.OPENING, BattleMusic.cue(20, 1200, 150, 35, false));
        assertEquals(BattleMusic.Cue.BATTLE, BattleMusic.cue(480, 1176, 150, 35, false));
        assertEquals(BattleMusic.Cue.PRESSURE, BattleMusic.cue(20, 1200, 35, 35, false));
        assertEquals(BattleMusic.Cue.PRESSURE, BattleMusic.cue(2000, 90, 100, 35, false));
        assertEquals(BattleMusic.Cue.PRESSURE, BattleMusic.cue(2000, 600, 100, 35, true));
    }
}
