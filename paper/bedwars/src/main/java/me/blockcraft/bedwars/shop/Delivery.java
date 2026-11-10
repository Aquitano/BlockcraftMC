package me.blockcraft.bedwars.shop;

import org.bukkit.inventory.ItemStack;

/** Puts what was bought into a copy of the inventory's storage, as the original did; false if it does not fit. */
final class Delivery {

    private Delivery() {
    }

    /** {@code times} the item, topping up stacks of it first and taking empty slots after. */
    static boolean add(ItemStack[] contents, ItemStack item, int times) {
        int max = item.getMaxStackSize();
        int left = item.getAmount() * times;
        for (var current : contents) {
            if (left <= 0) {
                return true;
            }
            if (current == null || !item.isSimilar(current) || current.getAmount() >= max) {
                continue;
            }
            int moved = Math.min(left, max - current.getAmount());
            current.setAmount(current.getAmount() + moved);
            left -= moved;
        }
        for (int slot = 0; slot < contents.length && left > 0; slot++) {
            if (contents[slot] != null && !contents[slot].isEmpty()) {
                continue;
            }
            int amount = Math.min(left, max);
            contents[slot] = item.asQuantity(amount);
            left -= amount;
        }
        return left <= 0;
    }

    /**
     * The item into the hotbar slot whose number key was pressed: whatever lay there moves to the first empty slot,
     * and a stack of the same item grows, its overflow taking an empty slot as well.
     */
    static boolean toSlot(ItemStack[] contents, ItemStack item, int slot) {
        var present = contents[slot];
        if (present == null || present.isEmpty()) {
            contents[slot] = item;
            return true;
        }
        int empty = firstEmpty(contents);
        if (!item.isSimilar(present)) {
            if (empty < 0) {
                return false;
            }
            contents[empty] = present;
            contents[slot] = item;
            return true;
        }
        int total = present.getAmount() + item.getAmount();
        if (total <= item.getMaxStackSize()) {
            present.setAmount(total);
            return true;
        }
        if (empty < 0) {
            return false;
        }
        present.setAmount(item.getMaxStackSize());
        contents[empty] = item.asQuantity(total - item.getMaxStackSize());
        return true;
    }

    private static int firstEmpty(ItemStack[] contents) {
        for (int slot = 0; slot < contents.length; slot++) {
            if (contents[slot] == null || contents[slot].isEmpty()) {
                return slot;
            }
        }
        return -1;
    }
}
