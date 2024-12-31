package me.blockcraft.QuickSG.tools.items;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class HealthItem
        implements Listener {
    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && p.getItemInHand().getType() == Material.MUSHROOM_SOUP && Main.State_InGame && Main.online.contains(p)) {
            e.setCancelled(true);
            p.getInventory().setItem(p.getInventory().getHeldItemSlot(), null);
            p.setMaxHealth(p.getMaxHealth() + 2.0);
            p.setHealth(p.getHealth() + 2.0);
            p.getInventory().remove(Material.GLASS_BOTTLE);
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR && p.getItemInHand().getType() == Material.MUSHROOM_SOUP && Main.State_InGame && Main.online.contains(p)) {
            e.setCancelled(true);
            p.getInventory().setItem(p.getInventory().getHeldItemSlot(), null);
            p.setMaxHealth(p.getMaxHealth() + 2.0);
            p.setHealth(p.getHealth() + 2.0);
            p.getInventory().remove(Material.GLASS_BOTTLE);
        }
    }
}

