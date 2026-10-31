package me.blockcraft.duell.menu;

import java.util.OptionalInt;

record DuelMenuLayout(int modes) {

    DuelMenuLayout {
        if (modes < 1 || modes > 15) {
            throw new IllegalArgumentException("A duel chooser supports 1 to 15 modes");
        }
    }

    int rows() {
        return 3 + (modes + 4) / 5;
    }

    int modeSlot(int index) {
        if (index < 0 || index >= modes) {
            throw new IndexOutOfBoundsException(index);
        }
        int row = index / 5;
        int count = Math.min(5, modes - row * 5);
        return (row + 2) * 9 + 5 - count + index % 5 * 2;
    }

    OptionalInt modeIndex(int slot) {
        for (int index = 0; index < modes; index++) {
            if (modeSlot(index) == slot) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    int randomSlot() {
        return (rows() - 1) * 9 + 3;
    }

    int combatSlot() {
        return (rows() - 1) * 9 + 4;
    }

    int ratingSlot() {
        return (rows() - 1) * 9 + 5;
    }
}
