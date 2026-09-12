package me.blockcraft.api.network;

import me.blockcraft.common.network.ServerState;
import me.blockcraft.common.network.ServerStatus;
import org.bukkit.Bukkit;

import java.util.OptionalInt;

/** What this server reports about itself; game plugins update the state as rounds start and end. */
public final class ServerPresence {

    private volatile ServerState state = ServerState.LOBBY;
    private volatile OptionalInt maxPlayers = OptionalInt.empty();
    private volatile Runnable onChange = () -> {
    };

    public ServerState state() {
        return state;
    }

    public void state(ServerState state) {
        this.state = state;
        onChange.run();
    }

    /** Caps what the proxy sends here below {@code server.properties}; games with a fixed player count use it. */
    public void maxPlayers(int maxPlayers) {
        this.maxPlayers = OptionalInt.of(maxPlayers);
        onChange.run();
    }

    public ServerStatus status() {
        return new ServerStatus(state, Bukkit.getOnlinePlayers().size(), maxPlayers.orElseGet(Bukkit::getMaxPlayers));
    }

    void onChange(Runnable onChange) {
        this.onChange = onChange;
    }
}
