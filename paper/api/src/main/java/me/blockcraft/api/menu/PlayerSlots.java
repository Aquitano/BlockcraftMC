package me.blockcraft.api.menu;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * A player's 36 own slots in the client's order below a chest: the three rows above the hotbar, then the hotbar. What
 * a menu puts there is marked and carries the player's own item, so that a crash that saved the menu's item gives the
 * player's back on their next join.
 */
record PlayerSlots(PlayerInventory inventory) implements Loan.Slots<ItemStack> {

    static final int SIZE = 36;
    private static final int MAIN = 27;
    private static final int HOTBAR = 9;
    private static final NamespacedKey MARKER = NamespacedKey.fromString("blockcraft:lent");
    private static final NamespacedKey ORIGINAL = NamespacedKey.fromString("blockcraft:lent_from");

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

    @Override
    public ItemStack mark(ItemStack item, ItemStack original) {
        var marked = item.clone();
        marked.editMeta(meta -> {
            var data = meta.getPersistentDataContainer();
            data.set(MARKER, PersistentDataType.BOOLEAN, true);
            if (original != null) {
                data.set(ORIGINAL, PersistentDataType.BYTE_ARRAY, original.serializeAsBytes());
            }
        });
        return marked;
    }

    @Override
    public boolean marked(ItemStack item) {
        return lent(item);
    }

    @Override
    public ItemStack original(ItemStack marked) {
        var data = marked.getItemMeta().getPersistentDataContainer();
        return data.has(ORIGINAL, PersistentDataType.BYTE_ARRAY)
                ? ItemStack.deserializeBytes(data.get(ORIGINAL, PersistentDataType.BYTE_ARRAY)) : null;
    }

    static boolean lent(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(MARKER);
    }
}
