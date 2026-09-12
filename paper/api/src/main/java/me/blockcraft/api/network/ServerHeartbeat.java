package me.blockcraft.api.network;

import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.ServerAddress;
import me.blockcraft.common.network.ServerRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/** Announces this server in {@code bc_servers} while it runs so the proxy can register it without configuration. */
public final class ServerHeartbeat {

    private final Plugin plugin;
    private final ServerRepository servers;
    private final ServerAddress self;
    private BukkitTask task;

    public ServerHeartbeat(Plugin plugin, ServerRepository servers, ServerAddress self) {
        this.plugin = plugin;
        this.servers = servers;
        this.self = self;
    }

    /**
     * Name: {@code BLOCKCRAFT_SERVER_NAME}, else the hostname (the container or pod name in Docker and Kubernetes).
     * Host: {@code BLOCKCRAFT_SERVER_HOST}, else the address of the interface that reaches the database, which is
     * the one the proxy on the same network reaches too.
     */
    public static ServerAddress local(DatabaseConfig database, int port) {
        String name = env("BLOCKCRAFT_SERVER_NAME", env("HOSTNAME", ""));
        if (name.isEmpty()) {
            throw new IllegalStateException("Neither BLOCKCRAFT_SERVER_NAME nor HOSTNAME is set");
        }
        return new ServerAddress(name, env("BLOCKCRAFT_SERVER_HOST", routeSource(database)), port);
    }

    public ServerAddress self() {
        return self;
    }

    public void start(long intervalTicks) {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::beat, 0L, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        try {
            servers.delete(self.name());
        } catch (DatabaseException e) {
            plugin.getLogger().warning("Could not unregister " + self.name() + ", the proxy drops it once the heartbeat is stale");
        }
    }

    private void beat() {
        try {
            servers.heartbeat(self);
        } catch (DatabaseException e) {
            plugin.getLogger().warning("Heartbeat failed: " + e.getCause().getMessage());
        }
    }

    private static String routeSource(DatabaseConfig database) {
        try (var socket = new DatagramSocket()) {
            socket.connect(new InetSocketAddress(database.host(), database.port()));
            return socket.getLocalAddress().getHostAddress();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not determine the address to announce, set BLOCKCRAFT_SERVER_HOST", e);
        }
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
