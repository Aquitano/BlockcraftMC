package me.blockcraft.api.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Slots a menu borrows from their owner, with what each is expected to hold. The menu writes only to a slot that still
 * holds what it wrote there, and when the loan ends such a slot gets its own item back. A slot something else changed
 * meanwhile, a reset or a new loadout, keeps that change, so nothing comes back twice or overwrites newer items.
 * <p>
 * Every item the loan writes carries the item it stands in for, so a crash that saves the loan's items instead of the
 * owner's loses nothing: {@link #recover} puts the owner's back.
 */
final class Loan<T> {

    interface Slots<T> {
        int size();

        /** The slot's item, or null when it is empty. */
        T get(int slot);

        void set(int slot, T item);

        /** {@code item} marked as lent, carrying {@code original}, which is null for an empty slot. */
        T mark(T item, T original);

        boolean marked(T item);

        /** What a marked item stands in for, or null for an empty slot. */
        T original(T marked);
    }

    private final Slots<T> slots;
    private final List<T> own = new ArrayList<>();
    private final List<T> expected = new ArrayList<>();

    Loan(Slots<T> slots) {
        this.slots = slots;
        for (int slot = 0; slot < slots.size(); slot++) {
            var item = slots.get(slot);
            own.add(item != null && slots.marked(item) ? slots.original(item) : item);
            expected.add(item);
        }
    }

    void lend(int slot, T item) {
        if (untouched(slot)) {
            var marked = slots.mark(item, own.get(slot));
            expected.set(slot, marked);
            slots.set(slot, marked);
        }
    }

    void giveBack() {
        for (int slot = 0; slot < slots.size(); slot++) {
            if (untouched(slot)) {
                slots.set(slot, own.get(slot));
                expected.set(slot, own.get(slot));
            }
        }
    }

    /** What {@link #giveBack} would put back. */
    List<T> own() {
        return IntStream.range(0, slots.size()).filter(this::untouched).mapToObj(own::get).filter(Objects::nonNull).toList();
    }

    private boolean untouched(int slot) {
        return Objects.equals(slots.get(slot), expected.get(slot));
    }

    /** Puts back what every marked item stands in for, as a loan that never ended would have. */
    static <T> void recover(Slots<T> slots) {
        for (int slot = 0; slot < slots.size(); slot++) {
            var item = slots.get(slot);
            if (item != null && slots.marked(item)) {
                slots.set(slot, slots.original(item));
            }
        }
    }
}
