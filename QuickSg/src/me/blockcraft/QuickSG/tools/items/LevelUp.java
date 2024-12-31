package me.blockcraft.QuickSG.tools.items;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class LevelUp
        implements Listener {
    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && p.getItemInHand().getType() == Material.ENCHANTED_BOOK && Main.State_InGame && Main.online.contains(p)) {
            p.getInventory().setItem(p.getInventory().getHeldItemSlot(), null);
            p.setLevel(p.getLevel() + 1);
        }
        if (e.getAction() == Action.RIGHT_CLICK_AIR && p.getItemInHand().getType() == Material.ENCHANTED_BOOK && Main.State_InGame && Main.online.contains(p)) {
            p.getInventory().setItem(p.getInventory().getHeldItemSlot(), null);
            p.setLevel(p.getLevel() + 1);
        }
    }
}

