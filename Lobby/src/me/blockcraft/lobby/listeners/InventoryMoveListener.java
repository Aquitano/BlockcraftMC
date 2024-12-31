package me.blockcraft.lobby.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class InventoryMoveListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        ItemStack clickedItem = e.getCurrentItem();

        if (isNonDroppableItem(clickedItem)) {
            e.setCancelled(true);
            //e.getWhoClicked().sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du kannst dieses Item nicht bewegen!");
        }
    }

    private boolean isNonDroppableItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) {
            return false;
        }

        for (String lore : meta.getLore()) {
            if (lore.equalsIgnoreCase("NON_DROPPABLE")) {
                return true;
            }
        }

        return false;
    }
}
