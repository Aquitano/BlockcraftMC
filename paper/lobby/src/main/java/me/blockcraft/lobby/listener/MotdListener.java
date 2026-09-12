package me.blockcraft.lobby.listener;

import me.blockcraft.common.message.Messages;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public final class MotdListener implements Listener {

    private final Messages messages;
    private final int maxPlayers;

    public MotdListener(Messages messages, int maxPlayers) {
        this.messages = messages;
        this.maxPlayers = maxPlayers;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        event.setMaxPlayers(maxPlayers);
        event.motd(messages.get("motd"));
    }
}
