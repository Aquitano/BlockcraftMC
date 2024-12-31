package me.blockcraft.lobby.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemDropListener implements Listener {
    @EventHandler
    public void onDrop(final PlayerDropItemEvent e) {
        ItemStack droppedItem = e.getItemDrop().getItemStack();

        if (isNonDroppableItem(droppedItem)) {
            e.setCancelled(true);
            //e.getPlayer().sendMessage(Data.BLOCKCRAFT_PREFIX + "\u00a77Du kannst dieses Item nicht fallen lassen!");
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
