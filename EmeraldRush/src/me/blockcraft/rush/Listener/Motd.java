package me.blockcraft.rush.Listener;

import me.blockcraft.rush.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class Motd implements Listener {
    @EventHandler
    public void on(final ServerListPingEvent e) {
        if (Main.State_Lobby) {
            e.setMotd("\u00a7a-------------");
        } else if (Main.State_InGame) {
            e.setMotd("\u00a76-------------");
        }
    }
}
