package me.blockcraft.QuickSG.Listener;

import me.blockcraft.QuickSG.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class Motd
        implements Listener {
    @EventHandler
    public void on(ServerListPingEvent e) {
        if (Main.State_Lobby) {
            e.setMotd("\u00a7a\u00a7m-------------");
        }
    }
}

