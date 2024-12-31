package me.blockcraft.lobby.listeners;

import me.eventseen.Data.Data;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class RunMessageofDay implements Listener {
    @EventHandler
    public void onPing(final ServerListPingEvent e) {
        e.setMaxPlayers(100);
        e.setMotd("\u00a7c" + Data.BLOCKCRAFT_IP + " Network");
    }
}
