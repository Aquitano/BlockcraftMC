package me.blockcraft.QuickSG.tools.items;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlayerTracker
        implements Listener {
    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getItemInHand().getType() == Material.COMPASS) {
            try {
                if (p.getItemInHand().getType() == Material.COMPASS) {
                    for (Entity ent : p.getNearbyEntities(100.0, 25.0, 100.0)) {
                        if (!(ent instanceof Player) || Main.spectator.contains((Object) ent)) continue;
                        Player near = (Player) ent;
                        p.setCompassTarget(near.getLocation());
                        p.sendMessage(Main.Prefix + "\u00a7f" + near.getName() + " \u00a77» \u00a7f" + (int) p.getLocation().distance(near.getLocation()) + "\u00a77 Blöcke");
                    }
                }
            } catch (Exception ent) {
                // empty catch block
            }
        }
    }
}

