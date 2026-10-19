package me.blockcraft.api.menu;

import java.util.OptionalInt;

/**
 * Where a {@link ListMenu} puts things: its entries fill the rows between the toolbar, when it has one, and the last
 * row, page after page; a notice stands in the middle of those rows.
 */
record ListLayout(int rows, boolean toolbar) {

    static final int COLUMNS = 9;
    private static final int MAX_ROWS = 6;

    ListLayout {
        if (rows - (toolbar ? 2 : 1) < 1 || rows > MAX_ROWS) {
            throw new IllegalArgumentException("No row left for entries in " + rows + " rows");
        }
    }

    /** As many rows as {@code entries} need, plus the last row, up to a full chest. */
    static int rowsFor(int entries) {
        return Math.min(MAX_ROWS, 1 + Math.max(1, (entries + COLUMNS - 1) / COLUMNS));
    }

    int firstSlot() {
        return toolbar ? COLUMNS : 0;
    }

    int lastRow() {
        return (rows - 1) * COLUMNS;
    }

    int perPage() {
        return lastRow() - firstSlot();
    }

    int pages(int entries) {
        return Math.max(1, (entries + perPage() - 1) / perPage());
    }

    int center() {
        return firstSlot() + (perPage() / COLUMNS - 1) / 2 * COLUMNS + COLUMNS / 2;
    }

    /** The position in the list of the entry {@code slot} shows on {@code page}; empty outside the entry rows. */
    OptionalInt index(int page, int slot) {
        return slot >= firstSlot() && slot < lastRow()
                ? OptionalInt.of(page * perPage() + slot - firstSlot())
                : OptionalInt.empty();
    }
}
