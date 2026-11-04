package me.blockcraft.api.game;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;

/** Enchanting tables bring their own lapis, which stays with the table. */
public final class EnchantingLapis implements Listener {

    private static final ItemStack LAPIS = ItemStack.of(Material.LAPIS_LAZULI, 64);
    private static final int LAPIS_SLOT = 1;

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof EnchantingInventory table) {
            table.setSecondary(LAPIS.clone());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() instanceof EnchantingInventory && event.getRawSlot() == LAPIS_SLOT) {
            event.setCancelled(true);
        }
    }

    /** The lapis belongs to the table, not to whoever closes it. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof EnchantingInventory table) {
            table.setSecondary(null);
        }
    }
}
