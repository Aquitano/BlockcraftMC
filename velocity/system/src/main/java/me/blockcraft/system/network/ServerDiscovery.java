package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerRepository;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors {@code bc_servers} into the proxy's server list: backends appear when their heartbeat does and vanish
 * when it goes stale. Servers listed in velocity.toml are left alone.
 */
public final class ServerDiscovery implements Runnable {

    private final ProxyServer proxy;
    private final ServerRepository servers;
    private final ServerDirectory directory;
    private final LobbyAlert lobbyAlert;
    private final LagAlert lagAlert;
    private final int staleSeconds;
    private final Logger logger;
    private final Map<String, ServerInfo> registered = new HashMap<>();
    private final Set<String> warnedUnreadable = new HashSet<>();

    public ServerDiscovery(ProxyServer proxy, ServerRepository servers, ServerDirectory directory, LobbyAlert lobbyAlert,
                           LagAlert lagAlert, int staleSeconds, Logger logger) {
        this.proxy = proxy;
        this.servers = servers;
        this.directory = directory;
        this.lobbyAlert = lobbyAlert;
        this.lagAlert = lagAlert;
        this.staleSeconds = staleSeconds;
        this.logger = logger;
    }

    @Override
    public synchronized void run() {
        List<ServerRecord> alive;
        try {
            var unreadable = new HashSet<String>();
            alive = servers.alive(staleSeconds, unreadable::add);
            warnUnreadable(unreadable);
        } catch (DatabaseException e) {
            logger.warn("Could not read bc_servers, keeping the current server list", e);
            return;
        }
        var aliveNames = alive.stream().map(ServerRecord::name).toList();
        registered.keySet().removeIf(name -> {
            if (aliveNames.contains(name)) {
                return false;
            }
            proxy.unregisterServer(registered.get(name));
            logger.info("Unregistered {}", name);
            return true;
        });
        alive.forEach(server -> register(server.identity()));
        directory.update(alive);
        lobbyAlert.check();
        lagAlert.check(alive);
    }

    /** Once per broken row, not on every poll; a row that gets fixed and breaks again is reported again. */
    private void warnUnreadable(Set<String> unreadable) {
        unreadable.stream().filter(name -> !warnedUnreadable.contains(name))
                .forEach(name -> logger.warn("Skipping {} in bc_servers: its row cannot be read, check its state", name));
        warnedUnreadable.clear();
        warnedUnreadable.addAll(unreadable);
    }

    private void register(ServerIdentity server) {
        var info = new ServerInfo(server.name(), InetSocketAddress.createUnresolved(server.host(), server.port()));
        var current = registered.get(server.name());
        if (info.equals(current)) {
            return;
        }
        if (current != null) {
            proxy.unregisterServer(current);
        } else if (proxy.getServer(server.name()).isPresent()) {
            return;
        }
        proxy.registerServer(info);
        registered.put(server.name(), info);
        logger.info("Registered {} ({}) at {}:{}", server.name(), server.group(), server.host(), server.port());
    }
}
