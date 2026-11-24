package me.blockcraft.sternenfront.hero;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SabersTest {

    @Test
    public void theBladeGrowsAStagePerTickAndIsWholeIgniteTicksAfterTheDraw() {
        var ignition = Sabers.Ignition.drawn(Hero.JEDI, 100, 6, 5);
        assertEquals(103, ignition.from());
        assertEquals(1, ignition.stage(103));
        assertEquals(4, ignition.stage(106));
    }

    @Test
    public void aShortIgnitionStillShowsEveryLength() {
        var ignition = Sabers.Ignition.drawn(Hero.SITH, 100, 1, 5);
        assertEquals(100, ignition.from());
        assertEquals(4, ignition.stage(103));
    }
}
