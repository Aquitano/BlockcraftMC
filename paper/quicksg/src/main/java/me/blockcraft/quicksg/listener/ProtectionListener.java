package me.blockcraft.quicksg.listener;

import me.blockcraft.quicksg.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class ProtectionListener implements Listener {

    private final Game game;

    public ProtectionListener(Game game) {
        this.game = game;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (game.isLobby() || game.protection()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (game.isLobby()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (game.isLobby() && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (game.isLobby()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (game.isLobby() && event.getWhoClicked() instanceof Player player && !game.isBuilder(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        event.setCancelled(!game.isBuilder(event.getPlayer()));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        event.setCancelled(!game.isBuilder(event.getPlayer()));
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (game.isLobby()) {
            event.setCancelled(true);
        }
    }
}
