package me.blockcraft.system.pack;

import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.player.configuration.PlayerConfigurationEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import me.blockcraft.common.network.PackStatus;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.common.network.ProxyMessageCodec;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.SystemConfig;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The network's resource pack: read from the plugin directory and hashed once at startup, served over plain HTTP
 * from the same process, offered while a player is being configured and tracked per client. Every backend the
 * player arrives on is told whether the client wears it, because the client's answer never reaches the backends.
 * A joining player's configuration waits for that answer, at most {@code wait}: a client that is still reloading its
 * resources when it arrives lays out the first chat lines wrongly and keeps them that way.
 */
public final class NetworkPack {

    private static final UUID ID = UUID.nameUUIDFromBytes("blockcraft:pack".getBytes(StandardCharsets.UTF_8));
    private static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(ProxyMessageCodec.CHANNEL);

    private final ResourcePackInfo info;
    private final Optional<HttpServer> server;
    private final Duration wait;
    private final Logger logger;
    private final Map<UUID, PackStatus> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> answers = new ConcurrentHashMap<>();

    private NetworkPack(ResourcePackInfo info, Optional<HttpServer> server, Duration wait, Logger logger) {
        this.info = info;
        this.server = server;
        this.wait = wait;
        this.logger = logger;
    }

    /** Empty when the config names no file; nothing is offered then and backends learn nothing. */
    public static Optional<NetworkPack> start(ProxyServer proxy, SystemConfig.ResourcePack config, Path dataDirectory,
                                              Component prompt, Logger logger) {
        if (config.file().isBlank()) {
            return Optional.empty();
        }
        var file = dataDirectory.resolve(config.file());
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            logger.warn("Resource pack {} is not readable, so none is offered", file, e);
            return Optional.empty();
        }
        var info = proxy.createResourcePackBuilder(config.url())
                .setId(ID)
                .setHash(sha1(bytes))
                .setShouldForce(config.required())
                .setPrompt(prompt)
                .build();
        var server = config.port() > 0 ? Optional.of(serve(config.port(), file.getFileName().toString(), bytes)) : Optional.<HttpServer>empty();
        logger.info("Offering resource pack {} ({} KiB) as {}", file.getFileName(), bytes.length / 1024, config.url());
        return Optional.of(new NetworkPack(info, server, Duration.ofSeconds(config.waitSeconds()), logger));
    }

    public void stop() {
        server.ifPresent(running -> running.stop(0));
    }

    @Subscribe
    public EventTask onConfiguration(PlayerConfigurationEvent event) {
        var player = event.player();
        var id = player.getUniqueId();
        if (applied(player) || statuses.get(id) == PackStatus.DECLINED) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        // Only the session's first offer waits, so a client whose download failed is not held up again at every switch.
        var answer = statuses.containsKey(id)
                ? CompletableFuture.<Void>completedFuture(null)
                : answers.computeIfAbsent(id, ignored -> new CompletableFuture<>())
                        .completeOnTimeout(null, wait.toMillis(), TimeUnit.MILLISECONDS);
        retry(player);
        return EventTask.resumeWhenComplete(answer);
    }

    public synchronized void retry(Player player) {
        var status = statuses.get(player.getUniqueId());
        if (status == PackStatus.OFFERED || status == PackStatus.DOWNLOADING || applied(player)) {
            tell(player, applied(player) ? PackStatus.APPLIED : status);
            return;
        }
        statuses.put(player.getUniqueId(), PackStatus.OFFERED);
        player.sendResourcePackOffer(info);
        tell(player, PackStatus.OFFERED);
    }

    @Subscribe
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!ID.equals(event.getPackId())) return;
        PackStatus status;
        if (event.getStatus().isIntermediate()) {
            status = PackStatus.DOWNLOADING;
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFUL) {
            status = PackStatus.APPLIED;
        } else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED) {
            status = PackStatus.DECLINED;
        } else {
            status = PackStatus.FAILED;
            // A wrong or unreachable url fails for every client alike, and nothing else would say so.
            logger.warn("{} could not load the resource pack from {}: {}", event.getPlayer().getUsername(), info.getUrl(),
                    event.getStatus());
        }
        statuses.put(event.getPlayer().getUniqueId(), status);
        tell(event.getPlayer(), status);
        if (status != PackStatus.DOWNLOADING) {
            answered(event.getPlayer().getUniqueId());
        }
    }

    private void answered(UUID player) {
        var answer = answers.remove(player);
        if (answer != null) {
            answer.complete(null);
        }
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        tell(player, applied(player) ? PackStatus.APPLIED : statuses.getOrDefault(player.getUniqueId(), PackStatus.UNAVAILABLE));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            answered(event.getPlayer().getUniqueId());
            statuses.remove(event.getPlayer().getUniqueId());
        }
    }

    public static boolean applied(Player player) {
        return player.getAppliedResourcePacks().stream().anyMatch(pack -> ID.equals(pack.getId()));
    }

    public static void tell(Player player, PackStatus status) {
        player.getCurrentServer().ifPresent(connection ->
                connection.sendPluginMessage(CHANNEL, ProxyMessageCodec.encode(new ProxyMessage.Pack(status))));
    }

    private static HttpServer serve(int port, String fileName, byte[] bytes) {
        try {
            var server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/" + fileName, exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not serve the resource pack on port " + port, e);
        }
    }

    private static byte[] sha1(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
