package me.blockcraft.quicksg.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.quicksg.game.Game;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public final class MotdListener implements Listener {

    private final Game game;
    private final Messages messages;

    public MotdListener(Game game, Messages messages) {
        this.game = game;
        this.messages = messages;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        if (game.isLobby()) {
            event.motd(messages.get("motd.lobby"));
        }
    }
}
