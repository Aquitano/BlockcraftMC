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
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.network.PackStatus;
import me.blockcraft.common.network.ProxyMessage;
import me.blockcraft.system.LiveSession;
import me.blockcraft.system.SystemConfig;
import me.blockcraft.system.network.ProxyChannel;
import org.slf4j.Logger;

import java.io.IOException;
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
 * Required packs hold configuration until application succeeds; failed or expired downloads disconnect the client.
 */
public final class NetworkPack {

    private static final UUID ID = UUID.nameUUIDFromBytes("blockcraft:pack".getBytes(StandardCharsets.UTF_8));

    private final Optional<ResourcePackInfo> info;
    private final Optional<HttpServer> server;
    private final Duration wait;
    private final boolean required;
    private final Messages messages;
    private final Logger logger;
    private final Map<UUID, PackStatus> statuses = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> answers = new ConcurrentHashMap<>();

    private NetworkPack(Optional<ResourcePackInfo> info, Optional<HttpServer> server, SystemConfig.ResourcePack config,
                        Messages messages, Logger logger) {
        this.info = info;
        this.server = server;
        this.wait = Duration.ofSeconds(config.waitSeconds());
        this.required = config.required();
        this.messages = messages;
        this.logger = logger;
    }

    public static Optional<NetworkPack> start(ProxyServer proxy, SystemConfig.ResourcePack config, Path dataDirectory,
                                              Messages messages, Logger logger) {
        if (config.file().isBlank()) {
            return unavailable(config, messages, logger);
        }
        Optional<HttpServer> server = Optional.empty();
        try {
            var file = dataDirectory.resolve(config.file());
            var bytes = Files.readAllBytes(file);
            if (config.port() < 0 || config.port() > 65535) {
                throw new IllegalArgumentException("Resource pack port must be between 0 and 65535");
            }
            var info = proxy.createResourcePackBuilder(config.url())
                    .setId(ID)
                    .setHash(sha1(bytes))
                    .setShouldForce(config.required())
                    .setPrompt(messages.get("resource-pack.prompt"))
                    .build();
            if (config.port() > 0) {
                server = Optional.of(serve(config.port(), file.getFileName().toString(), bytes));
            }
            logger.info("Offering resource pack {} ({} KiB) as {}", file.getFileName(), bytes.length / 1024, config.url());
            return Optional.of(new NetworkPack(Optional.of(info), server, config, messages, logger));
        } catch (IOException | RuntimeException failure) {
            server.ifPresent(running -> running.stop(0));
            logger.warn("Could not prepare resource pack {} on port {}", config.file(), config.port(), failure);
            return unavailable(config, messages, logger);
        }
    }

    private static Optional<NetworkPack> unavailable(SystemConfig.ResourcePack config, Messages messages, Logger logger) {
        if (!config.required()) return Optional.empty();
        logger.error("Required resource pack is unavailable; players cannot enter the network");
        return Optional.of(new NetworkPack(Optional.empty(), Optional.empty(), config, messages, logger));
    }

    public void stop() {
        server.ifPresent(running -> running.stop(0));
    }

    @Subscribe
    public synchronized EventTask onConfiguration(PlayerConfigurationEvent event) {
        var player = event.player();
        var id = player.getUniqueId();
        if (info.isEmpty()) {
            player.disconnect(messages.get("resource-pack.unavailable"));
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        if (required && failed(id)) {
            player.disconnect(messages.get("resource-pack.failed"));
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        if (applied(player) || !required && statuses.get(id) == PackStatus.DECLINED) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        if (!required && statuses.containsKey(id) && !answers.containsKey(id)) {
            retry(player);
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        var answer = answers.get(id);
        if (answer == null) {
            answer = new CompletableFuture<>();
            answers.put(id, answer);
            var pending = answer;
            CompletableFuture.runAsync(() -> timedOut(player, pending),
                    CompletableFuture.delayedExecutor(wait.toMillis(), TimeUnit.MILLISECONDS));
        }
        retry(player);
        return EventTask.resumeWhenComplete(answer);
    }

    private synchronized void timedOut(Player player, CompletableFuture<Void> pending) {
        if (answers.get(player.getUniqueId()) != pending) return;
        statuses.put(player.getUniqueId(), PackStatus.FAILED);
        if (required) player.disconnect(messages.get("resource-pack.timeout"));
        tell(player, PackStatus.FAILED);
        answered(player.getUniqueId());
    }

    public synchronized void retry(Player player) {
        if (info.isEmpty()) {
            player.disconnect(messages.get("resource-pack.unavailable"));
            return;
        }
        if (required && failed(player.getUniqueId())) return;
        var status = statuses.get(player.getUniqueId());
        if (status == PackStatus.OFFERED || status == PackStatus.DOWNLOADING || applied(player)) {
            tell(player, applied(player) ? PackStatus.APPLIED : status);
            return;
        }
        statuses.put(player.getUniqueId(), PackStatus.OFFERED);
        player.sendResourcePackOffer(info.orElseThrow());
        tell(player, PackStatus.OFFERED);
    }

    @Subscribe
    public synchronized void onStatus(PlayerResourcePackStatusEvent event) {
        if (!ID.equals(event.getPackId())) return;
        if (required && failed(event.getPlayer().getUniqueId())) return;
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
            logger.warn("{} could not load the resource pack from {}: {}", event.getPlayer().getUsername(),
                    info.map(ResourcePackInfo::getUrl).orElse("unavailable"),
                    event.getStatus());
        }
        statuses.put(event.getPlayer().getUniqueId(), status);
        tell(event.getPlayer(), status);
        if (status != PackStatus.DOWNLOADING) {
            if (required && status != PackStatus.APPLIED) {
                event.getPlayer().disconnect(messages.get(status == PackStatus.DECLINED
                        ? "resource-pack.declined" : "resource-pack.failed"));
            }
            answered(event.getPlayer().getUniqueId());
        }
    }

    private void answered(UUID player) {
        var answer = answers.remove(player);
        if (answer != null) {
            answer.complete(null);
        }
    }

    private boolean failed(UUID player) {
        var status = statuses.get(player);
        return status == PackStatus.DECLINED || status == PackStatus.FAILED;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        tell(player, applied(player) ? PackStatus.APPLIED : statuses.getOrDefault(player.getUniqueId(), PackStatus.UNAVAILABLE));
    }

    @Subscribe
    public synchronized void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            answered(event.getPlayer().getUniqueId());
            statuses.remove(event.getPlayer().getUniqueId());
        }
    }

    public static boolean applied(Player player) {
        return player.getAppliedResourcePacks().stream().anyMatch(pack -> ID.equals(pack.getId()));
    }

    public static void tell(Player player, PackStatus status) {
        ProxyChannel.send(player, new ProxyMessage.Pack(status));
    }

    private static HttpServer serve(int port, String fileName, byte[] bytes) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        try {
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
        } catch (RuntimeException | Error failure) {
            server.stop(0);
            throw failure;
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
