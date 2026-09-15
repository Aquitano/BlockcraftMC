package me.blockcraft.lobby.listener;

import me.blockcraft.api.world.BuildMode;
import me.blockcraft.lobby.item.LobbyItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** Players cannot rearrange their own inventory (builders can), and the lobby hotbar items never move or drop. */
public final class InventoryLockListener implements Listener {

    private final BuildMode buildMode;

    public InventoryLockListener(BuildMode buildMode) {
        this.buildMode = buildMode;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        boolean ownInventory = event.getClickedInventory() == player.getInventory();
        if ((ownInventory && !buildMode.isEnabled(player)) || LobbyItem.of(event.getCurrentItem()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (LobbyItem.of(event.getOffHandItem()).isPresent() || LobbyItem.of(event.getMainHandItem()).isPresent()) {
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
