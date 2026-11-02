package me.blockcraft.lobby.item;

import me.blockcraft.api.item.HotbarItem;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Hotbar items every player gets on join. */
public enum LobbyItem implements HotbarItem {
    NAVIGATOR(0),
    PLAYER_HIDER(1),
    FRIENDS(2),
    EXTRAS(4),
    GADGET(5),
    SETTINGS(6),
    ACHIEVEMENTS(7),
    PROFILE(8),
    POND_ROD(3),
    THROW(3);

    private final int slot;

    LobbyItem(int slot) {
        this.slot = slot;
    }

    public static Optional<LobbyItem> of(ItemStack stack) {
        return HotbarItem.of(stack, LobbyItem.class);
    }

    public int slot() {
        return slot;
    }
}
