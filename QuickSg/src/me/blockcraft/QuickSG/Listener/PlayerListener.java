package me.blockcraft.QuickSG.Listener;

import me.blockcraft.QuickSG.Main;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

public class PlayerListener
        implements Listener {
    @EventHandler
    public void on(EntityDamageEvent e) {
        if (Main.State_Lobby) {
            e.setCancelled(true);
        }
        if (Main.Schutzzeit) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on(EntityDamageByEntityEvent e) {
        if (Main.State_Lobby) {
            e.setCancelled(true);
        }
        if (Main.Schutzzeit) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on(EntityDamageByBlockEvent e) {
        if (Main.State_Lobby) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Player)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on(PlayerDropItemEvent e) {
        e.setCancelled(Main.State_Lobby);
    }

    @EventHandler
    public void on(PlayerPickupItemEvent e) {
        e.setCancelled(Main.State_Lobby);
    }

    @EventHandler
    public void on(InventoryMoveItemEvent e) {
        e.setCancelled(Main.State_Lobby);
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (Main.State_Lobby) {
            e.setCancelled(!Main.builder.contains(p));
        } else {
            e.setCancelled(false);
        }
    }

    @EventHandler
    public void onMove(final PlayerMoveEvent e) {
        if (Main.countdownRunning) {
            final Location to = e.getFrom();
            to.setPitch(e.getTo().getPitch());
            to.setYaw(e.getTo().getYaw());
            e.setTo(to);
        }
    }
}

