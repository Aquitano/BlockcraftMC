package me.blockcraft.ttt.task;

import me.blockcraft.api.item.HotbarItem;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/** What a fetch chore has its player carry; it also names the chore, so two loads never stand in for each other. */
public enum TaskItem implements HotbarItem {
    CARGO;

    private static final NamespacedKey CHORE = new NamespacedKey("blockcraft", "ttt_chore");

    ItemStack forChore(ItemStack stack, String chore) {
        tag(stack).editMeta(meta -> meta.getPersistentDataContainer().set(CHORE, PersistentDataType.STRING, chore));
        return stack;
    }

    static Optional<String> chore(ItemStack stack) {
        return HotbarItem.of(stack, TaskItem.class)
                .map(item -> stack.getPersistentDataContainer().get(CHORE, PersistentDataType.STRING));
    }
}
