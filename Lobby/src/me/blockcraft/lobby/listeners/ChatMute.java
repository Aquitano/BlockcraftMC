package me.blockcraft.lobby.listeners;

import me.blockcraft.lobby.Lobby;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatMute implements Listener {
    public static Lobby main;

    public ChatMute(final Lobby main) {
        ChatMute.main = main;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMute(final AsyncPlayerChatEvent e) {
        final Player p = e.getPlayer();
        if (ChatMute.main.mute) {
            if (p.hasPermission("bs.bypass")) {
                e.setCancelled(false);
            } else {
                e.setCancelled(true);
                p.sendMessage("\u00a78» \u00a7cDer Chat ist derzeit deaktiviert.");
            }
        }
    }
}
