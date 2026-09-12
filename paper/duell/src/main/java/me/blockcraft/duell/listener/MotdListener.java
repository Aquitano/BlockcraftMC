package me.blockcraft.duell.listener;

import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public final class MotdListener implements Listener {

    private final Duel duel;
    private final Messages messages;

    public MotdListener(Duel duel, Messages messages) {
        this.duel = duel;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ServerListPingEvent event) {
        event.motd(messages.get(duel.isLobby() ? "motd.lobby" : "motd.ingame"));
    }
}
