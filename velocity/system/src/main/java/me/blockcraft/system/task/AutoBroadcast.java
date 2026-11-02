package me.blockcraft.system.task;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blockcraft.common.message.Messages;
import me.blockcraft.system.LobbyConnector;

import java.util.List;
import java.util.Set;

/** Rotates through the network's tips, its ranks and channels, and secret hints, which only the lobbies hear. */
public final class AutoBroadcast implements Runnable {

    private static final List<String> KEYS = List.of("friends", "premium", "pond", "report", "teamspeak", "retro",
            "support", "youtuber", "secrets", "discord");
    private static final Set<String> LOBBY_HINTS = Set.of("pond", "retro", "secrets");

    private final ProxyServer proxy;
    private final LobbyConnector lobby;
    private final Messages messages;
    private int next;

    public AutoBroadcast(ProxyServer proxy, LobbyConnector lobby, Messages messages) {
        this.proxy = proxy;
        this.lobby = lobby;
        this.messages = messages;
    }

    @Override
    public void run() {
        String key = KEYS.get(next);
        next = (next + 1) % KEYS.size();
        var lines = messages.getLines("broadcast." + key);
        boolean lobbyOnly = LOBBY_HINTS.contains(key);
        for (Player player : proxy.getAllPlayers()) {
            if (!lobbyOnly || lobby.isOnLobby(player)) {
                lines.forEach(player::sendMessage);
            }
        }
    }
}
