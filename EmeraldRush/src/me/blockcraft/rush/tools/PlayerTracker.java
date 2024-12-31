package me.blockcraft.rush.tools;

import me.blockcraft.rush.Main;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerTracker implements Listener {
    @EventHandler
    public void on(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getItemInHand().getType() == Material.COMPASS) {
            try {
                if (p.getItemInHand().getType() == Material.COMPASS) {
                    for (final Entity ent : p.getNearbyEntities(100.0, 25.0, 100.0)) {
                        if (ent instanceof Player) {
                            if (!Main.spectator.contains(ent)) {
                                final Player near = (Player) ent;
                                p.setCompassTarget(near.getLocation());
                                p.sendMessage(Main.Prefix + "\u00a7f" + near.getName() + " \u00a77» \u00a7f" + (int) p.getLocation().distance(near.getLocation()) + "\u00a77 Bläcke");
                            } else {
                                p.sendMessage("");
                            }
                        }
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
}
