package me.blockcraft.duell.menu;

import org.junit.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DuelMenuLayoutTest {

    @Test
    public void fiveModesFitBelowTheFormatTabsAndAboveTheOptions() {
        var layout = new DuelMenuLayout(5);

        assertEquals(4, layout.rows());
        assertEquals(List.of(18, 20, 22, 24, 26),
                IntStream.range(0, 5).map(layout::modeSlot).boxed().toList());
        assertEquals(30, layout.randomSlot());
        assertEquals(31, layout.combatSlot());
        assertEquals(32, layout.ratingSlot());
        assertEquals(OptionalInt.of(4), layout.modeIndex(26));
        assertTrue(layout.modeIndex(4).isEmpty());
        assertTrue(layout.modeIndex(30).isEmpty());
    }

    @Test
    public void anotherModeAddsACentredRowWithoutOverlappingTheFooter() {
        var layout = new DuelMenuLayout(6);

        assertEquals(5, layout.rows());
        assertEquals(31, layout.modeSlot(5));
        assertEquals(OptionalInt.of(5), layout.modeIndex(31));
        assertEquals(39, layout.randomSlot());
        assertEquals(40, layout.combatSlot());
        assertEquals(41, layout.ratingSlot());
    }
}
