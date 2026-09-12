package me.blockcraft.lobby.listener;

import me.blockcraft.lobby.item.LobbyItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/** Players cannot rearrange their own inventory (operators can), and the lobby hotbar items never move or drop. */
public final class InventoryLockListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean ownInventory = event.getClickedInventory() == player.getInventory();
        if ((ownInventory && !player.isOp()) || LobbyItem.of(event.getCurrentItem()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (LobbyItem.of(event.getItemDrop().getItemStack()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
