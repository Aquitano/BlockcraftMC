package me.blockcraft.common.elo;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class EloTest {

    @Test
    public void anExpectedWinMovesLessThanAnUpset() {
        var formula = new EloFormula(1000, 32);
        assertEquals(16, formula.delta(1000, 1000));
        assertEquals(29, formula.delta(1000, 1400));
        assertEquals(3, formula.delta(1400, 1000));
    }

    @Test
    public void seasonsAreQuartersKeyedByYear() {
        assertEquals("2026-1", Season.of(LocalDate.of(2026, 1, 1)).key());
        assertEquals("2026-1", Season.of(LocalDate.of(2026, 3, 31)).key());
        assertEquals("2026-3", Season.of(LocalDate.of(2026, 9, 18)).key());
        assertEquals("2026-4", Season.of(LocalDate.of(2026, 12, 31)).key());
    }
}
