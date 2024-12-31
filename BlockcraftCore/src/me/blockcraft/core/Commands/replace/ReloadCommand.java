package me.blockcraft.core.Commands.replace;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class ReloadCommand implements Listener {
    @EventHandler
    public void on(final PlayerCommandPreprocessEvent e) {
        final Player p = e.getPlayer();
        final String cmd = e.getMessage();
        if (cmd.equalsIgnoreCase("/reload") || cmd.equalsIgnoreCase("/rl")) {
            e.setCancelled(true);
            if (p.hasPermission("bs.reload")) {
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    if (all.hasPermission("bs.reload")) {
                        all.sendMessage(Main.Prefix + "\u00a7cAlle Serverdaten werden nun neugeladen!");
                    }
                }
                Bukkit.reload();
                for (final Player all : Bukkit.getOnlinePlayers()) {
                    if (all.hasPermission("bs.reload")) {
                        all.sendMessage(Main.Prefix + "\u00a7aDer Server wurde reloaded!");
                    }
                }
            }
        }
    }
}
