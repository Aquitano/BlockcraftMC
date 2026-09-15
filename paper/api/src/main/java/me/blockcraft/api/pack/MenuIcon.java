package me.blockcraft.api.pack;

import me.blockcraft.api.item.Items;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Optional;

/**
 * The pictures a menu can show on an item: the pack's {@code items/<key>.json} model for clients wearing it, the
 * vanilla item everyone else sees, decided in one place. An item built here carries its icon, so any {@code Menu}
 * that places it draws it right for its viewer.
 */
public enum MenuIcon {

    BACK(Material.ARROW),
    CLOSE(Material.BARRIER),
    TOOL_CLOSE(Material.BARRIER),
    TOOL_SHOP(Material.GOLD_INGOT),
    TOOL_HOME(Material.ENDER_EYE),
    QUICKSG(Material.IRON_SWORD),
    EMERALDRUSH(Material.EMERALD),
    DUELL(Material.SHIELD),
    JUMP(Material.DIAMOND_BOOTS);

    /** Draws nothing at all; the outer slots of a toolbar button wear it so the whole bar answers hover and click. */
    public static final Key EMPTY_MODEL = Key.key("blockcraft", "empty");

    private static final NamespacedKey MARKER = NamespacedKey.fromString("blockcraft:menu_icon");

    private final Material fallback;

    MenuIcon(Material fallback) {
        this.fallback = fallback;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Key model() {
        return Key.key("blockcraft", key());
    }

    public Items.Builder item() {
        return Items.of(fallback).hideAttributes()
                .edit(meta -> meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, key()));
    }

    public static Optional<Key> modelOf(ItemStack item) {
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(item.getItemMeta().getPersistentDataContainer().get(MARKER, PersistentDataType.STRING))
                .map(key -> Key.key("blockcraft", key));
    }
}
