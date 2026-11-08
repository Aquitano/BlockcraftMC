package me.blockcraft.api.menu;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * A player's 36 own slots in the client's order below a chest: the three rows above the hotbar, then the hotbar. What
 * a menu puts there is marked, so that an item a crash kept from coming back can be told from the player's own.
 */
record PlayerSlots(PlayerInventory inventory) implements Loan.Slots<ItemStack> {

    static final int SIZE = 36;
    private static final int MAIN = 27;
    private static final int HOTBAR = 9;
    private static final NamespacedKey MARKER = NamespacedKey.fromString("blockcraft:lent");

    @Override
    public int size() {
        return SIZE;
    }

    @Override
    public ItemStack get(int slot) {
        var item = inventory.getItem(index(slot));
        return item == null || item.isEmpty() ? null : item;
    }

    @Override
    public void set(int slot, ItemStack item) {
        inventory.setItem(index(slot), item);
    }

    private static int index(int slot) {
        return slot < MAIN ? slot + HOTBAR : slot - MAIN;
    }

    static ItemStack mark(ItemStack item) {
        var marked = item.clone();
        marked.editMeta(meta -> meta.getPersistentDataContainer().set(MARKER, PersistentDataType.BOOLEAN, true));
        return marked;
    }

    static boolean lent(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(MARKER);
    }
}
