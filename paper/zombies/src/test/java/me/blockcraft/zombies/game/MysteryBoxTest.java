package me.blockcraft.zombies.game;

import org.junit.Test;

import static org.junit.Assert.*;

public class MysteryBoxTest {

    @Test
    public void gunChangesFastAtFirstThenSlowerAndStopsWhenTheRollEnds() {
        var swaps = MysteryBox.swapTicks(100);
        assertEquals(100, (int) swaps.getLast());
        for (int swap = 1; swap < swaps.size(); swap++) {
            assertTrue(swaps.get(swap) > swaps.get(swap - 1));
        }
        assertTrue(swaps.getFirst() <= 3);
        assertTrue(swaps.getLast() - swaps.get(swaps.size() - 2) >= 10);
        assertEquals(20, (int) MysteryBox.swapTicks(20).getLast());
    }

    @Test
    public void boxStepsThroughEveryGunAndStopsOnTheRolledOne() {
        var pool = Weapon.buyable();
        int swaps = 9;
        assertEquals(Weapon.SNIPER, MysteryBox.shown(pool, Weapon.SNIPER, swaps, swaps));
        for (int swap = 1; swap <= swaps; swap++) {
            assertNotEquals(MysteryBox.shown(pool, Weapon.SNIPER, swap - 1, swaps), MysteryBox.shown(pool, Weapon.SNIPER, swap, swaps));
        }
        assertFalse(pool.contains(Weapon.PISTOL));
    }
}
