package me.blockcraft.api.menu;

import me.blockcraft.api.settings.PlayerSettingChangedEvent;
import me.blockcraft.api.task.PlayerTasks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;


public final class MenuListener implements Listener {

    private final PlayerTasks playerTasks;

    public MenuListener(PlayerTasks playerTasks) {
        this.playerTasks = playerTasks;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof Menu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        if (!menu.accepts(event.getClick())) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (event.getClickedInventory() == menu.getInventory() && clicked != null && !clicked.isEmpty()) {
            menu.handleClick(player, event.getSlot(), clicked, event);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSettingChanged(PlayerSettingChangedEvent event) {
        var player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
            menu.onSettingChanged(player, event.setting());
        }
    }

    @EventHandler
    public void onPack(PlayerResourcePackStatusEvent event) {
        var player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu menu) {
            menu.restyle(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu menu && event.getPlayer() instanceof Player player) {
            if (event.getReason() == InventoryCloseEvent.Reason.PLAYER) {
                playerTasks.invalidate(player);
            }
            menu.handleClose(player, event);
        }
    }
}
