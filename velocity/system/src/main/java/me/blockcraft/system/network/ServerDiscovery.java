package me.blockcraft.system.network;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.ServerAddress;
import me.blockcraft.common.network.ServerRepository;
import org.slf4j.Logger;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mirrors {@code bc_servers} into the proxy's server list: backends appear when their heartbeat does and vanish
 * when it goes stale. Servers listed in velocity.toml are left alone.
 */
public final class ServerDiscovery implements Runnable {

    private final ProxyServer proxy;
    private final ServerRepository servers;
    private final int staleSeconds;
    private final Logger logger;
    private final Map<String, ServerInfo> registered = new HashMap<>();

    public ServerDiscovery(ProxyServer proxy, ServerRepository servers, int staleSeconds, Logger logger) {
        this.proxy = proxy;
        this.servers = servers;
        this.staleSeconds = staleSeconds;
        this.logger = logger;
    }

    @Override
    public synchronized void run() {
        Map<String, ServerAddress> alive;
        try {
            alive = servers.alive(staleSeconds).stream().collect(Collectors.toMap(ServerAddress::name, address -> address));
        } catch (DatabaseException e) {
            logger.warn("Could not read bc_servers, keeping the current server list", e);
            return;
        }
        registered.keySet().removeIf(name -> {
            if (alive.containsKey(name)) {
                return false;
            }
            proxy.unregisterServer(registered.get(name));
            logger.info("Unregistered {}", name);
            return true;
        });
        alive.values().forEach(this::register);
    }

    private void register(ServerAddress address) {
        var info = new ServerInfo(address.name(), InetSocketAddress.createUnresolved(address.host(), address.port()));
        var current = registered.get(address.name());
        if (info.equals(current)) {
            return;
        }
        if (current != null) {
            proxy.unregisterServer(current);
        } else if (proxy.getServer(address.name()).isPresent()) {
            return;
        }
        proxy.registerServer(info);
        registered.put(address.name(), info);
        logger.info("Registered {} at {}:{}", address.name(), address.host(), address.port());
    }
}
