package me.blockcraft.api.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Slots a menu borrows from their owner, with what each is expected to hold. The menu writes only to a slot that still
 * holds what it wrote there, and when the loan ends such a slot gets its own item back. A slot something else changed
 * meanwhile, a reset or a new loadout, keeps that change, so nothing comes back twice or overwrites newer items.
 */
final class Loan<T> {

    interface Slots<T> {
        int size();

        /** The slot's item, or null when it is empty. */
        T get(int slot);

        void set(int slot, T item);
    }

    private final Slots<T> slots;
    private final List<T> own = new ArrayList<>();
    private final List<T> expected = new ArrayList<>();

    /** {@code lent} recognises an item an earlier loan never gave back, which is nobody's own. */
    Loan(Slots<T> slots, Predicate<T> lent) {
        this.slots = slots;
        for (int slot = 0; slot < slots.size(); slot++) {
            var item = slots.get(slot);
            own.add(item == null || lent.test(item) ? null : item);
            expected.add(item);
        }
    }

    void lend(int slot, T item) {
        if (Objects.equals(slots.get(slot), expected.get(slot))) {
            expected.set(slot, item);
            slots.set(slot, item);
        }
    }

    void giveBack() {
        for (int slot = 0; slot < slots.size(); slot++) {
            if (Objects.equals(slots.get(slot), expected.get(slot))) {
                slots.set(slot, own.get(slot));
                expected.set(slot, own.get(slot));
            }
        }
    }

    List<T> own() {
        return own.stream().filter(Objects::nonNull).toList();
    }
}
