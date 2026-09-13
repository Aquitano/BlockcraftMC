package me.blockcraft.lobby.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Optional;

/** Hotbar items every player gets on join, identified by a persistent tag instead of their name. */
public enum LobbyItem {
    NAVIGATOR(0),
    PLAYER_HIDER(1),
    PROFILE(2),
    EXTRAS(4),
    GADGET(5),
    SETTINGS(6),
    FRIENDS(7),
    SECRETS(8);

    public static final NamespacedKey KEY = new NamespacedKey("lobby", "item");

    private final int slot;

    LobbyItem(int slot) {
        this.slot = slot;
    }

    public static Optional<LobbyItem> of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String id = stack.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return Arrays.stream(values()).filter(item -> item.name().equals(id)).findFirst();
    }

    public int slot() {
        return slot;
    }

    public ItemStack tag(ItemStack stack) {
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, name()));
        return stack;
    }
}
