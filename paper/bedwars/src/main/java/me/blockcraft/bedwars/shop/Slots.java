package me.blockcraft.bedwars.shop;

/** Where the shop puts a row of icons: full rows from the left, the last one centred, an even count around a gap. */
final class Slots {

    private static final int COLUMNS = 9;
    private static final int MIDDLE = 4;

    private Slots() {
    }

    static int rows(int count) {
        return Math.max(1, (count + COLUMNS - 1) / COLUMNS);
    }

    static int[] centered(int count) {
        var slots = new int[count];
        int full = count / COLUMNS * COLUMNS;
        for (int slot = 0; slot < full; slot++) {
            slots[slot] = slot;
        }
        int rest = count - full;
        boolean even = rest % 2 == 0;
        int column = MIDDLE - rest / 2;
        for (int index = full; index < count; column++) {
            if (even && column == MIDDLE) {
                continue;
            }
            slots[index++] = full + column;
        }
        return slots;
    }
}
