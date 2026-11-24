package me.blockcraft.system;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is away from the keyboard, as the Paper server they play on reports it. A player arrives on a new server
 * active, and the old one cannot report anything once they left, so a switch clears the flag here.
 */
public final class AfkPlayers {

    private final Set<UUID> away = ConcurrentHashMap.newKeySet();

    public void set(Player player, boolean afk) {
        if (afk) {
            away.add(player.getUniqueId());
        } else {
            away.remove(player.getUniqueId());
        }
    }

    public boolean isAfk(Player player) {
        return away.contains(player.getUniqueId());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        away.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            away.remove(event.getPlayer().getUniqueId());
        }
    }
}
