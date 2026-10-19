package me.blockcraft.api.item;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.pack.MenuIcon;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Optional;

/**
 * An item a plugin hands out for the player to click, implemented by the plugin's enum of them. It is recognised
 * by a tag in its data, never by its material or name, so the same material from a chest or a kit does nothing; the
 * tag names the enum too, so two enums' {@code STATS} never answer for each other.
 */
public interface HotbarItem {

    NamespacedKey KEY = new NamespacedKey("blockcraft", "hotbar_item");

    String name();

    default ItemStack tag(ItemStack stack) {
        var type = this instanceof Enum<?> constant ? constant.getDeclaringClass() : getClass();
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id(type, name())));
        return stack;
    }

    /** The tagged item, its {@link MenuIcon} drawn the way {@code viewer}'s client can show it. */
    default ItemStack build(Player viewer, Items.Builder item) {
        var stack = tag(item.build());
        MenuIcon.style(stack, BlockcraftApi.get().resourcePack().applied(viewer));
        return stack;
    }

    static <T extends Enum<T> & HotbarItem> Optional<T> of(ItemStack stack, Class<T> type) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        String id = stack.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        return Arrays.stream(type.getEnumConstants()).filter(item -> id(type, item.name()).equals(id)).findFirst();
    }

    private static String id(Class<?> type, String name) {
        return type.getName() + "." + name;
    }
}
