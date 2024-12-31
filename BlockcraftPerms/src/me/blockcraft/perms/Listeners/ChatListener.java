package me.blockcraft.perms.Listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat3(final AsyncPlayerChatEvent event) {
        final Player p = event.getPlayer();
        String msg = event.getMessage();
        msg = msg.replace("%", "Prozent");
        event.setFormat(p.getDisplayName() + " \u00a78»\u00a7f " + msg);
    }
}
