package me.blockcraft.api.menu;

import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileMenuTest {

    @Test
    public void everyGameGetsItsOwnSlotInsideTheMenu() {
        for (int games = 1; games <= 18; games++) {
            int size = (3 + ProfileMenu.gameRows(games)) * 9;
            var slots = new HashSet<Integer>();
            for (int index = 0; index < games; index++) {
                int slot = ProfileMenu.gameSlot(index, games);
                assertTrue(games + " games, slot " + slot, slot >= 27 && slot < size);
                slots.add(slot);
            }
            assertEquals(games + " games", games, slots.size());
        }
    }

    @Test
    public void tenGamesSitInTwoCentredRowsOfFive() {
        assertEquals(29, ProfileMenu.gameSlot(0, 10));
        assertEquals(33, ProfileMenu.gameSlot(4, 10));
        assertEquals(38, ProfileMenu.gameSlot(5, 10));
        assertEquals(42, ProfileMenu.gameSlot(9, 10));
    }
}
