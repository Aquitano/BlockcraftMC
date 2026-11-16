package me.blockcraft.api.game;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class StatsMenusTest {

    @Test
    public void linesFillTheRowsUnderTheToolbarTwoAtATime() {
        assertEquals(9, StatsMenus.lineSlot(0));
        assertEquals(14, StatsMenus.lineSlot(1));
        assertEquals(18, StatsMenus.lineSlot(2));
        assertEquals(32, StatsMenus.lineSlot(5));
    }

    @Test
    public void aLineKeepsItsValueAndCutsItsNameToFit() {
        assertEquals(List.of("SIEGE", "12"), StatsMenus.nameAndValue("» Siege: 12"));
        assertEquals(List.of("LIGA", "GOLD III"), StatsMenus.nameAndValue("» Liga: Gold III"));
        assertEquals(List.of("EINSÄTZE GE…", "123"), StatsMenus.nameAndValue("» Einsätze geschafft: 123"));
    }
}
