package me.blockcraft.lobby.listener;

import me.blockcraft.api.world.BuildMode;
import me.blockcraft.lobby.item.LobbyItem;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Players cannot rearrange their own inventory or open the map's containers (builders can), and the lobby hotbar
 * items never move or drop.
 */
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
    public void onOpen(InventoryOpenEvent event) {
        var holder = event.getInventory().getHolder(false);
        boolean container = holder instanceof BlockInventoryHolder || holder instanceof DoubleChest
                || (holder instanceof Entity && !(holder instanceof Player))
                || event.getInventory().getType() == InventoryType.ENDER_CHEST;
        if (container && event.getPlayer() instanceof Player player && !buildMode.isEnabled(player)) {
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
