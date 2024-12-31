package me.blockcraft.lobby.listeners;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class EntityDamager implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(final EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            e.setCancelled(true);
        }
        if (e.getEntity() instanceof Blaze) {
            e.setCancelled(true);
        }
        if (e.getEntity() instanceof Rabbit) {
            e.setCancelled(true);
        }
        if (e.getEntity() instanceof Wolf) {
            e.setCancelled(true);
        }
        if (e.getEntity() instanceof Chicken) {
            e.setCancelled(true);
        }
        if (e.getEntity() instanceof Pig) {
            e.setCancelled(true);
        }
    }
}
