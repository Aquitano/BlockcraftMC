package me.blockcraft.api.pack;

import me.blockcraft.api.item.Items;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum MenuState {
    AVAILABLE, LOCKED, OWNED, ACTIVE, PENDING, OFFLINE;

    private static final NamespacedKey MARKER = NamespacedKey.fromString("blockcraft:menu_state");

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Key model() {
        return Key.key("blockcraft", "state_" + key());
    }

    public Items.Builder mark(Items.Builder item) {
        return item.edit(meta -> meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, key()));
    }

    public static Optional<MenuState> of(ItemStack item) {
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        String key = item.getItemMeta().getPersistentDataContainer().get(MARKER, PersistentDataType.STRING);
        return Arrays.stream(values()).filter(state -> state.key().equals(key)).findFirst();
    }

    public static void style(ItemStack item) {
        of(item).ifPresent(state -> Items.of(item).tooltipStyle(state.model()));
    }
}
