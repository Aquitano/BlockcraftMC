package me.blockcraft.cores.game;

import me.blockcraft.api.item.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * What a player starts and respawns with: the configured items and leather armour in the team's colour. Kit items
 * are marked, so that a death drops only what the player found or made.
 */
public final class Kit {

    private static final NamespacedKey MARK = new NamespacedKey("cores", "kit");

    private final List<ItemStack> items;

    public Kit(List<ItemStack> items) {
        this.items = items.stream().map(Kit::mark).toList();
    }

    public void give(Player player, Side side) {
        var inventory = player.getInventory();
        inventory.clear();
        items.forEach(item -> inventory.addItem(item.clone()));
        inventory.setHelmet(armor(Material.LEATHER_HELMET, side));
        inventory.setChestplate(armor(Material.LEATHER_CHESTPLATE, side));
        inventory.setLeggings(armor(Material.LEATHER_LEGGINGS, side));
        inventory.setBoots(armor(Material.LEATHER_BOOTS, side));
    }

    public static boolean isKit(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(MARK, PersistentDataType.BYTE);
    }

    private static ItemStack armor(Material material, Side side) {
        return mark(Items.of(material).color(side.dye()).edit(meta -> meta.setUnbreakable(true)).build());
    }

    private static ItemStack mark(ItemStack item) {
        var marked = item.clone();
        marked.editMeta(meta -> meta.getPersistentDataContainer().set(MARK, PersistentDataType.BYTE, (byte) 1));
        return marked;
    }
}
