package me.blockcraft.system.pack;

import com.sun.net.httpserver.HttpServer;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
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
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.function.Function;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class NetworkPack {

    private static final UUID ID = packId("base");

    private static UUID packId(String name) {
        return UUID.nameUUIDFromBytes((name.equals("base") ? "blockcraft:pack" : "blockcraft:pack:" + name)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static final class Client {
        private final Map<UUID, PackStatus> statuses = new HashMap<>();
        private final Set<UUID> removals = new java.util.HashSet<>();
        private final Map<UUID, Integer> retries = new HashMap<>();
        private Set<UUID> needed = Set.of(ID);
        private Pending pending;
        private String destination = "";
        private String replayGroup = "";
        private String replayGame = "";
    }

    private record Pending(String destination, Set<UUID> packs, CompletableFuture<Void> answer) {
    }

    private final Map<UUID, ResourcePackInfo> packs;
    private final Optional<HttpServer> server;
    private final Duration wait;
    private final boolean required;
    private final int downloadRetries;
    private final SystemConfig.PackProfiles profiles;
    private final Function<String, Optional<String>> groupOf;
    private final Messages messages;
    private final Logger logger;
    private final Map<UUID, Client> clients = new HashMap<>();

    private NetworkPack(Map<UUID, ResourcePackInfo> packs, Optional<HttpServer> server, SystemConfig.ResourcePack config,
                        Messages messages, Logger logger, Function<String, Optional<String>> groupOf) {
        this.packs = packs;
        this.server = server;
        this.wait = Duration.ofSeconds(config.waitSeconds());
        this.required = config.required() || config.profiles().enabled();
        this.downloadRetries = config.downloadRetries();
        this.profiles = config.profiles();
        this.groupOf = groupOf;
        this.messages = messages;
        this.logger = logger;
    }

    public static Optional<NetworkPack> start(ProxyServer proxy, SystemConfig.ResourcePack config, Path dataDirectory,
                                              Messages messages, Logger logger,
                                              Function<String, Optional<String>> groupOf) {
        Optional<HttpServer> server = Optional.empty();
        try {
            if (config.port() < 0 || config.port() > 65535) {
                throw new IllegalArgumentException("Resource pack port must be between 0 and 65535");
            }
            var definitions = config.profiles().enabled() ? config.profiles().archives()
                    : Map.of("base", new SystemConfig.PackArchive(config.file(), config.url()));
            if (config.profiles().enabled()) {
                if (!definitions.keySet().containsAll(Set.of("base", "lobby", "modes", "starwars"))
                        || definitions.keySet().stream().anyMatch(name -> !name.matches("[a-z][a-z0-9-]*"))) {
                    throw new IllegalArgumentException("Profiles need base, lobby, modes and starwars archives");
                }
                validateSelection(config.profiles().defaultPacks(), definitions);
                config.profiles().groups().values().forEach(names -> validateSelection(names, definitions));
            }
            var packs = new LinkedHashMap<UUID, ResourcePackInfo>();
            var archives = new LinkedHashMap<String, byte[]>();
            for (var entry : definitions.entrySet()) {
                var archive = entry.getValue();
                if (archive.file().isBlank()) throw new IllegalArgumentException("Empty resource pack file");
                var file = dataDirectory.resolve(archive.file());
                var bytes = Files.readAllBytes(file);
                if (archives.put(file.getFileName().toString(), bytes) != null) {
                    throw new IllegalArgumentException("Duplicate resource pack filename");
                }
                packs.put(packId(entry.getKey()), proxy.createResourcePackBuilder(archive.url())
                        .setId(packId(entry.getKey())).setHash(sha1(bytes))
                        .setShouldForce(config.required() || config.profiles().enabled())
                        .setPrompt(messages.get("resource-pack.prompt")).build());
                logger.info("Offering resource pack {} ({} KiB) as {}", file.getFileName(), bytes.length / 1024, archive.url());
            }
            if (config.port() > 0) server = Optional.of(serve(config.port(), archives));
            return Optional.of(new NetworkPack(Map.copyOf(packs), server, config, messages, logger, groupOf));
        } catch (IOException | RuntimeException failure) {
            server.ifPresent(running -> running.stop(0));
            logger.warn("Could not prepare resource packs on port {}", config.port(), failure);
            if (!config.required() && !config.profiles().enabled()) return Optional.empty();
            return Optional.of(new NetworkPack(Map.of(), Optional.empty(), config, messages, logger, groupOf));
        }
    }

    private static void validateSelection(List<String> names, Map<String, SystemConfig.PackArchive> definitions) {
        if (!definitions.keySet().containsAll(names)) throw new IllegalArgumentException("Unknown pack in profile");
    }

    public void stop() {
        server.ifPresent(running -> running.stop(0));
    }

    public synchronized void replay(Player player, String group, String game) {
        var client = clients.computeIfAbsent(player.getUniqueId(), id -> new Client());
        client.replayGroup = group;
        client.replayGame = game;
    }

    public synchronized CompletableFuture<Boolean> prepareReplay(Player player, String game) {
        if (!profiles.enabled()) return CompletableFuture.completedFuture(true);
        var destination = player.getCurrentServer().map(connection -> connection.getServerInfo().getName());
        var group = destination.flatMap(groupOf);
        if (group.isEmpty() || !profiles.groups().containsKey(group.orElseThrow())
                || !profiles.groups().get(group.orElseThrow()).isEmpty()) return CompletableFuture.completedFuture(false);
        var client = clients.computeIfAbsent(player.getUniqueId(), id -> new Client());
        client.replayGroup = group.orElseThrow();
        client.replayGame = game;
        var selected = selection(destination.orElseThrow(), client);
        if (selected.isEmpty()) return CompletableFuture.completedFuture(false);
        var needed = selected.orElseThrow();
        if (needed.stream().anyMatch(id -> failed(client.statuses.get(id)))) return CompletableFuture.completedFuture(false);
        var old = client.pending;
        client.pending = null;
        client.needed = needed;
        client.destination = destination.orElseThrow();
        var answer = new CompletableFuture<Void>();
        if (needed.stream().allMatch(id -> client.statuses.get(id) == PackStatus.APPLIED)) answer.complete(null);
        else {
            var pending = new Pending(client.destination, needed, answer);
            client.pending = pending;
            CompletableFuture.runAsync(() -> timedOut(player, client, pending),
                    CompletableFuture.delayedExecutor(wait.toMillis(), TimeUnit.MILLISECONDS));
            retry(player);
        }
        if (old != null) old.answer().complete(null);
        return answer.thenApply(ignored -> {
            synchronized (NetworkPack.this) {
                boolean applied = clients.get(player.getUniqueId()) == client && needed.equals(client.needed)
                        && needed.stream().allMatch(id -> client.statuses.get(id) == PackStatus.APPLIED);
                if (applied) removeUnused(player, client);
                return applied;
            }
        });
    }

    private Optional<Set<UUID>> selection(String destination, Client client) {
        if (!profiles.enabled()) return Optional.of(Set.of(ID));
        return groupOf.apply(destination).flatMap(group -> {
            var names = profiles.groups().getOrDefault(group, profiles.defaultPacks());
            if (names.isEmpty() && group.equals(client.replayGroup) && !group.equals(client.replayGame)) {
                names = profiles.groups().getOrDefault(client.replayGame, profiles.defaultPacks());
            }
            if (names.isEmpty()) return Optional.empty();
            var ids = new LinkedHashSet<UUID>();
            ids.add(ID);
            names.forEach(name -> ids.add(packId(name)));
            return Optional.of(java.util.Collections.unmodifiableSet(ids));
        });
    }

    @Subscribe
    public synchronized EventTask onConfiguration(PlayerConfigurationEvent event) {
        var player = event.player();
        var client = clients.computeIfAbsent(player.getUniqueId(), id -> new Client());
        var old = client.pending;
        client.pending = null;
        var destination = profiles.enabled() ? event.server().getServerInfo().getName() : "";
        var selected = selection(destination, client);
        if (selected.isEmpty() || packs.isEmpty()) {
            player.disconnect(messages.get(packs.isEmpty() ? "resource-pack.unavailable" : "resource-pack.unsupported-profile"));
            if (old != null) old.answer().complete(null);
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        client.needed = selected.orElseThrow();
        client.destination = destination;
        if (required && client.needed.stream().anyMatch(id -> failed(client.statuses.get(id)))) {
            player.disconnect(messages.get("resource-pack.failed"));
            if (old != null) old.answer().complete(null);
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        var mustWait = client.needed.stream().anyMatch(id -> {
            var status = client.statuses.get(id);
            return status != PackStatus.APPLIED && (required || status == null
                    || status == PackStatus.OFFERED || status == PackStatus.DOWNLOADING);
        });
        var answer = new CompletableFuture<Void>();
        if (mustWait) {
            var pending = new Pending(destination, client.needed, answer);
            client.pending = pending;
            CompletableFuture.runAsync(() -> timedOut(player, client, pending),
                    CompletableFuture.delayedExecutor(wait.toMillis(), TimeUnit.MILLISECONDS));
        } else answer.complete(null);
        retry(player);
        if (old != null) old.answer().complete(null);
        return EventTask.resumeWhenComplete(answer);
    }

    private synchronized void timedOut(Player player, Client client, Pending pending) {
        if (clients.get(player.getUniqueId()) != client || client.pending != pending) return;
        pending.packs().stream().filter(id -> client.statuses.get(id) != PackStatus.APPLIED)
                .forEach(id -> client.statuses.put(id, PackStatus.FAILED));
        if (required) player.disconnect(messages.get("resource-pack.timeout"));
        tellBase(player, client);
        answered(client);
    }

    public synchronized void retry(Player player) {
        if (packs.isEmpty()) {
            player.disconnect(messages.get("resource-pack.unavailable"));
            return;
        }
        var client = clients.computeIfAbsent(player.getUniqueId(), id -> new Client());
        for (var id : client.needed) {
            var status = client.statuses.get(id);
            if (status == PackStatus.APPLIED || status == PackStatus.OFFERED || status == PackStatus.DOWNLOADING
                    || required && failed(status) || !required && status == PackStatus.DECLINED) continue;
            client.statuses.put(id, PackStatus.OFFERED);
            player.sendResourcePackOffer(packs.get(id));
        }
        tellBase(player, client);
    }

    @Subscribe
    public synchronized void onStatus(PlayerResourcePackStatusEvent event) {
        var client = clients.get(event.getPlayer().getUniqueId());
        var id = event.getPackId();
        if (client == null) return;
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DISCARDED && client.removals.remove(id)) return;
        if (!client.statuses.containsKey(id)) return;
        if (required && failed(client.statuses.get(id))) return;
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                && client.needed.contains(id) && client.retries.getOrDefault(id, 0) < downloadRetries) {
            client.retries.merge(id, 1, Integer::sum);
            client.statuses.put(id, PackStatus.OFFERED);
            event.getPlayer().sendMessage(messages.get("resource-pack.retrying"));
            event.getPlayer().sendResourcePackOffer(packs.get(id));
            return;
        }
        PackStatus status;
        if (event.getStatus().isIntermediate()) status = PackStatus.DOWNLOADING;
        else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFUL) status = PackStatus.APPLIED;
        else if (event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED) status = PackStatus.DECLINED;
        else status = PackStatus.FAILED;
        client.statuses.put(id, status);
        if (failed(status) && client.needed.contains(id)) {
            logger.warn("{} could not load resource pack {}: {}", event.getPlayer().getUsername(), id, event.getStatus());
            if (required) event.getPlayer().disconnect(messages.get(status == PackStatus.DECLINED
                    ? "resource-pack.declined" : "resource-pack.failed"));
            answered(client);
        } else if (client.pending != null && client.pending.packs().stream()
                .allMatch(pack -> client.statuses.get(pack) == PackStatus.APPLIED)) answered(client);
        if (ID.equals(id)) tellBase(event.getPlayer(), client);
    }

    private static boolean failed(PackStatus status) {
        return status == PackStatus.DECLINED || status == PackStatus.FAILED;
    }

    private static void answered(Client client) {
        var pending = client.pending;
        client.pending = null;
        if (pending != null) pending.answer().complete(null);
    }

    private static void tellBase(Player player, Client client) {
        tell(player, client.statuses.getOrDefault(ID, PackStatus.UNAVAILABLE));
    }

    @Subscribe
    public synchronized void onServerPostConnect(ServerPostConnectEvent event) {
        var player = event.getPlayer();
        var client = clients.get(player.getUniqueId());
        if (client == null) return;
        if (profiles.enabled()) {
            var destination = player.getCurrentServer().map(connection -> connection.getServerInfo().getName());
            var selected = destination.flatMap(name -> selection(name, client));
            if (selected.isPresent() && destination.orElseThrow().equals(client.destination)) {
                client.needed = selected.orElseThrow();
                removeUnused(player, client);
                answered(client);
                if (destination.flatMap(groupOf).filter(client.replayGroup::equals).isEmpty()) {
                    client.replayGroup = "";
                    client.replayGame = "";
                }
            }
        }
        tellBase(player, client);
    }

    private static void removeUnused(Player player, Client client) {
        var removed = client.statuses.keySet().stream().filter(id -> !client.needed.contains(id)).toList();
        removed.forEach(id -> {
            client.statuses.remove(id);
            client.retries.remove(id);
            client.removals.add(id);
            player.removeResourcePacks(id);
        });
    }

    @Subscribe
    public synchronized void onKick(KickedFromServerEvent event) {
        if (!profiles.enabled() || !event.kickedDuringServerConnect()) return;
        var player = event.getPlayer();
        var client = clients.get(player.getUniqueId());
        if (client == null || client.pending == null
                || !client.pending.destination().equals(event.getServer().getServerInfo().getName())) return;
        client.destination = player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("");
        client.needed = selection(client.destination, client).orElse(Set.of(ID));
        answered(client);
    }

    @Subscribe
    public synchronized void onDisconnect(DisconnectEvent event) {
        if (LiveSession.endedBy(event)) {
            var client = clients.remove(event.getPlayer().getUniqueId());
            if (client != null) answered(client);
        }
    }

    public static boolean applied(Player player) {
        return player.getAppliedResourcePacks().stream().anyMatch(pack -> ID.equals(pack.getId()));
    }

    public static void tell(Player player, PackStatus status) {
        ProxyChannel.send(player, new ProxyMessage.Pack(status));
    }

    private static HttpServer serve(int port, Map<String, byte[]> archives) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        try {
            archives.forEach((fileName, bytes) -> server.createContext("/" + fileName, exchange -> {
                try (exchange) {
                    if (!exchange.getRequestURI().getPath().equals("/" + fileName)) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    exchange.getResponseHeaders().add("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }));
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
