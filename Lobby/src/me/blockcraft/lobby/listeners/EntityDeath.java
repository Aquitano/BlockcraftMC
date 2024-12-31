package me.blockcraft.lobby.listeners;

import org.bukkit.entity.Blaze;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityDeath implements Listener {
    @EventHandler
    public void onDeath(final EntityDeathEvent e) {
        final Entity r = e.getEntity();
        if (r instanceof Blaze) {
            final Entity rider = r.getPassenger();
            rider.remove();
        }
    }
}
