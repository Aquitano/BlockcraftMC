package me.blockcraft.lobby.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public class AntiBuild implements Listener {
    @EventHandler
    public void onBuild(final BlockPlaceEvent e) {
        final Player p = e.getPlayer();
        e.setCancelled(!p.hasPermission("build.b"));
    }

    @EventHandler
    public void onBuild(final BlockBreakEvent e) {
        final Player p = e.getPlayer();
        e.setCancelled(!p.hasPermission("build.b"));
    }

    @EventHandler
    public void onBuild(final PlayerBucketEmptyEvent e) {
        final Player p = e.getPlayer();
        e.setCancelled(!p.hasPermission("build.b"));
    }

    @EventHandler
    public void onDrop(final PlayerDropItemEvent e) {
        final Player p = e.getPlayer();
        e.setCancelled(!p.hasPermission("build.b"));
    }
}
