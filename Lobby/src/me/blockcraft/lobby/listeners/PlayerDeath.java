package me.blockcraft.lobby.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class PlayerDeath implements Listener {
    @EventHandler
    public void onDeath(final PlayerDeathEvent e) {
        e.setDeathMessage("");
        e.setDroppedExp(0);
        e.setKeepInventory(true);
    }
}
