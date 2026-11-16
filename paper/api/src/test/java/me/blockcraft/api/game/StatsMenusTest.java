package me.blockcraft.api.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StatsMenusTest {

    @Test
    public void ownTilesSitCentredInTheLastRow() {
        assertEquals(20, StatsMenus.tileSlot(0, 5));
        assertEquals(24, StatsMenus.tileSlot(4, 5));
        assertEquals(19, StatsMenus.tileSlot(0, 6));
        assertEquals(24, StatsMenus.tileSlot(5, 6));
        assertEquals(26, StatsMenus.tileSlot(8, 9));
    }
}
