package me.blockcraft.lobby.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.entity.ItemSpawnEvent;

public class UnleashEvent implements Listener {
    @EventHandler
    public void onLeash(final EntityUnleashEvent e) {
        final Entity r = e.getEntity();
        r.remove();
    }

    @EventHandler
    public void onDrop(final ItemSpawnEvent e) {
        if (e.getEntity().getItemStack().getType() == Material.LEASH) {
            e.getEntity().remove();
        }
    }
}
