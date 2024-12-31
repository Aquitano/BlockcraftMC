package me.blockcraft.core.Commands.replace;

import me.blockcraft.core.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class StopCommand implements Listener {
    @EventHandler
    public void on(final PlayerCommandPreprocessEvent e) {
        final Player p = e.getPlayer();
        final String cmd = e.getMessage();
        if (cmd.equalsIgnoreCase("/stop")) {
            e.setCancelled(true);
            if (p.hasPermission("bs.stop")) {
                p.sendMessage(Main.Prefix + "\u00a7cDer Server wird nun neugestartet!");
                Bukkit.shutdown();
            }
        }
    }
}
