package me.blockcraft.QuickSG.Listener;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class WorldListener
        implements Listener {
    @EventHandler
    public void on(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        e.setCancelled(!Main.builder.contains(p));
    }

    @EventHandler
    public void on(BlockBreakEvent e) {
        Player p = e.getPlayer();
        e.setCancelled(!Main.builder.contains(p));
    }

    @EventHandler
    public void on(FoodLevelChangeEvent e) {
        if (Main.State_Lobby) {
            e.setCancelled(true);
        } else if (Main.State_InGame) {
            e.setCancelled(false);
        }
    }

    @EventHandler
    public void noUproot(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock().getType() == Material.SOIL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void preventLeavedecay(LeavesDecayEvent e) {
        e.setCancelled(true);
    }
}

