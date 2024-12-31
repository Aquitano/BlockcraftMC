package me.blockcraft.core.Commands.Listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class HelpListener implements Listener {
    @EventHandler
    public void on(final PlayerCommandPreprocessEvent e) {
        if (Bukkit.getServer().getHelpMap().getHelpTopic(e.getMessage().split(" ")[0]) == null) {
            e.setCancelled(true);
        }
    }
}
