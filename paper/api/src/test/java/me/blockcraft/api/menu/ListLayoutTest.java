package me.blockcraft.api.menu;

import org.junit.Test;

import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;

public class ListLayoutTest {

    @Test
    public void aFullChestListsFiveRowsAndCentresItsNotice() {
        var layout = new ListLayout(6, false);

        assertEquals(45, layout.perPage());
        assertEquals(45, layout.lastRow());
        assertEquals(22, layout.center());
    }

    @Test
    public void aToolbarTakesTheFirstRowFromTheEntries() {
        var layout = new ListLayout(6, true);

        assertEquals(9, layout.firstSlot());
        assertEquals(36, layout.perPage());
        assertEquals(22, layout.center());
        assertEquals(OptionalInt.empty(), layout.index(0, 4));
        assertEquals(OptionalInt.of(36), layout.index(1, 9));
    }

    @Test
    public void onlyTheEntryRowsShowEntriesAndTheLastPageMayBePartlyFull() {
        var layout = new ListLayout(3, false);

        assertEquals(OptionalInt.of(19), layout.index(1, 1));
        assertEquals(OptionalInt.empty(), layout.index(1, 18));
        assertEquals(1, layout.pages(0));
        assertEquals(1, layout.pages(18));
        assertEquals(2, layout.pages(19));
    }

    @Test
    public void rowsGrowWithTheEntriesUpToAFullChest() {
        assertEquals(2, ListLayout.rowsFor(0));
        assertEquals(2, ListLayout.rowsFor(9));
        assertEquals(3, ListLayout.rowsFor(10));
        assertEquals(6, ListLayout.rowsFor(200));
    }
}
