package me.blockcraft.rush.Listener;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.tools.Timer_Shopping;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

public class PlayerListener implements Listener {
    @EventHandler
    public void on(final EntityDamageEvent e) {
        e.setCancelled(Main.State_Lobby || Main.State_SuchZeit || Main.State_Shopping);
        if (Main.State_DM && e.getEntity() instanceof Player) {
            final Player p = (Player) e.getEntity();
            if (p.getHealth() >= 5.0) {
                Timer_Shopping.invcontents.put(p, p.getInventory().getContents());
                Timer_Shopping.armorContents.put(p, p.getInventory().getArmorContents());
            }
        }
    }

    @EventHandler
    public void on(final EntityDamageByEntityEvent e) {
        e.setCancelled(Main.State_Lobby || Main.State_SuchZeit || Main.State_Shopping);
    }

    @EventHandler
    public void on(final EntityDamageByBlockEvent e) {
        e.setCancelled(Main.State_Lobby || Main.State_SuchZeit || Main.State_Shopping);
    }

    @EventHandler
    public void on(final CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on(final PlayerDropItemEvent e) {
        final Player p = e.getPlayer();
        if (Main.State_Lobby || Main.State_SuchZeit || Main.State_Shopping) {
            e.setCancelled(true);
            e.setCancelled(!Main.builder.contains(p));
        }
    }

    @EventHandler
    public void on(final PlayerPickupItemEvent e) {
        e.setCancelled(Main.State_Lobby || Main.State_SuchZeit);
    }

    @EventHandler
    public void on(final InventoryMoveItemEvent e) {
        e.setCancelled(Main.State_Lobby || Main.State_SuchZeit);
    }

    @EventHandler
    public void on(final FoodLevelChangeEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void on(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (Main.State_Lobby || Main.State_SuchZeit) {
            e.setCancelled(!Main.builder.contains(p));
        } else {
            e.setCancelled(false);
        }
    }
}
