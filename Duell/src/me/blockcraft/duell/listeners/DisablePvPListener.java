package me.blockcraft.duell.listeners;

import me.blockcraft.duell.Duell;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class DisablePvPListener implements Listener {
    public static Duell main;

    public DisablePvPListener(final Duell main) {
        DisablePvPListener.main = main;
    }

    @EventHandler
    public void onHit(final EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) {
            e.setCancelled(DisablePvPListener.main.noPvP);
        }
    }
}
