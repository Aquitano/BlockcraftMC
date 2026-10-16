package me.blockcraft.api.network;

import me.blockcraft.common.db.DatabaseConfig;
import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.ServerIdentity;
import me.blockcraft.common.network.ServerRepository;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Announces this server in {@code bc_servers} while it runs so the proxy can register it without configuration. Every
 * beat runs on one thread, so a beat that read an older state can never commit after a newer one, and none lands
 * after {@link #stop()} removed the row.
 */
public final class ServerHeartbeat {

    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(10);

    private final Plugin plugin;
    private final ServerRepository servers;
    private final ServerIdentity self;
    private final ServerPresence presence;
    private final AtomicBoolean failing = new AtomicBoolean();
    private final ScheduledExecutorService beats = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("BlockcraftAPI heartbeat").daemon().factory());

    public ServerHeartbeat(Plugin plugin, ServerRepository servers, ServerIdentity self, ServerPresence presence) {
        this.plugin = plugin;
        this.servers = servers;
        this.self = self;
        this.presence = presence;
        presence.onChange(() -> {
            if (!beats.isShutdown()) {
                beats.execute(this::beat);
            }
        });
    }

    /**
     * Name: {@code BLOCKCRAFT_SERVER_NAME}, else the hostname (the container or pod name in Docker and Kubernetes).
     * Group: {@code BLOCKCRAFT_SERVER_GROUP}, else the name. Host: {@code BLOCKCRAFT_SERVER_HOST}, else the address
     * of the interface that reaches the database, which is the one the proxy on the same network reaches too.
     */
    public static ServerIdentity local(DatabaseConfig database, int port) {
        String name = env("BLOCKCRAFT_SERVER_NAME").or(() -> env("HOSTNAME"))
                .orElseThrow(() -> new IllegalStateException("Neither BLOCKCRAFT_SERVER_NAME nor HOSTNAME is set"));
        return new ServerIdentity(name, env("BLOCKCRAFT_SERVER_GROUP").orElse(name),
                env("BLOCKCRAFT_SERVER_HOST").orElseGet(() -> routeSource(database)), port);
    }

    public ServerIdentity self() {
        return self;
    }

    public void start(Duration interval) {
        beats.scheduleWithFixedDelay(this::beat, 0L, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void stop() {
        beats.shutdown();
        try {
            if (!beats.awaitTermination(DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                plugin.getLogger().warning("A heartbeat was still running after " + DRAIN_TIMEOUT.toSeconds() + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            servers.delete(self.name());
        } catch (DatabaseException e) {
            plugin.getLogger().warning("Could not unregister " + self.name() + ", the proxy drops it once the heartbeat is stale");
        }
    }

    private void beat() {
        try {
            servers.heartbeat(self, presence.status());
            if (failing.getAndSet(false)) {
                plugin.getLogger().info("Heartbeat recovered");
            }
        } catch (DatabaseException e) {
            if (!failing.getAndSet(true)) {
                plugin.getLogger().warning("Heartbeat failed, retrying quietly until it recovers: " + e.getCause().getMessage());
            }
        } catch (RuntimeException e) {
            // Escaping the executor would cancel every later beat.
            plugin.getLogger().log(Level.WARNING, "Heartbeat failed", e);
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

    private static Optional<String> env(String key) {
        return Optional.ofNullable(System.getenv(key)).filter(value -> !value.isBlank());
    }
}
