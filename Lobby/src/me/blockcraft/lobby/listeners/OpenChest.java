package me.blockcraft.lobby.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class OpenChest implements Listener {
    @SuppressWarnings("unused")
    @EventHandler
    public void onPlayerInteractInLobby2(final PlayerInteractEvent event) {
        final Player p = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR) {
            event.getPlayer().getItemInHand().getType();
            final Material chest = Material.CHEST;
        }
    }
}
