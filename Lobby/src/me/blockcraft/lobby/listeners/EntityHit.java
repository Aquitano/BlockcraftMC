package me.blockcraft.lobby.listeners;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityHit implements Listener {
    @EventHandler
    public void onHit(final EntityDamageByEntityEvent e) {
        final Entity r = e.getEntity();
        if (r instanceof Player) {
            e.setCancelled(true);
        }
        if (r instanceof Villager && e.getDamager() instanceof Player) {
            e.setCancelled(true);
        }
    }
}
