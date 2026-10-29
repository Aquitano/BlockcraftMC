package me.blockcraft.replay;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.playback.PlaybackManager;
import me.blockcraft.replay.storage.ReplayStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

final class ReplayService implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final ReplayRepository repository;
    private final ReplayStore store;
    private final PlaybackManager playback;
    private final ReplayConfig config;
    private final Messages messages;
    private final boolean playbackBackend;
    private final String minecraft = Bukkit.getMinecraftVersion();
    private final Map<UUID, Object> loading = new HashMap<>();
    private final Map<UUID, Object> pendingNavigation = new HashMap<>();
    private final ThreadPoolExecutor reads = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(4), Thread.ofPlatform().name("blockcraft-replay-reader").daemon().factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private final ThreadPoolExecutor maintenance = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), Thread.ofPlatform().name("blockcraft-replay-retention").daemon().factory(),
            new ThreadPoolExecutor.AbortPolicy());
    private ReplayControls controls;
    private boolean ready;

    ReplayService(Plugin plugin, ReplayRepository repository, ReplayStore store, PlaybackManager playback,
                  ReplayConfig config, Messages messages) {
        this.plugin = plugin;
        this.repository = repository;
        this.store = store;
        this.playback = playback;
        this.config = config;
        this.messages = messages;
        playbackBackend = BlockcraftApi.get().server().group().equals(config.playbackGroup());
    }

    void controls(ReplayControls controls) {
        this.controls = controls;
    }

    void ready() {
        ready = true;
    }

    void open(Player player) {
        pendingNavigation.remove(player.getUniqueId());
        if (playback.active(player)) {
            player.sendMessage(messages.get("playback.busy"));
            return;
        }
        cancelLoading(player);
        history(player, entries -> new ReplayMenu(this, messages, entries).open(player), () -> { });
    }

    void history(Player player, Consumer<List<ReplayRepository.Entry>> complete, Runnable failed) {
        if (!ready) {
            player.sendMessage(messages.get("playback.starting"));
            failed.run();
            return;
        }
        if (!allowed(player)) {
            return;
        }
        boolean staff = player.hasPermission(ReplayPlugin.STAFF_PERMISSION);
        BlockcraftApi.get().playerTasks().latest(player,
                () -> repository.history(player.getUniqueId(), staff, config.historyLimit()),
                entries -> {
                    if (allowed(player) && (!staff || player.hasPermission(ReplayPlugin.STAFF_PERMISSION))) {
                        complete.accept(entries);
                    }
                }, ignored -> { }, ignored -> failed.run());
    }

    void play(Player player, UUID replay) {
        pendingNavigation.remove(player.getUniqueId());
        if (!ready) {
            player.sendMessage(messages.get("playback.starting"));
            return;
        }
        if (!allowed(player) || loading.containsKey(player.getUniqueId()) || playback.active(player)) {
            player.sendMessage(messages.get("playback.busy"));
            return;
        }
        if (!playbackBackend) {
            requestTransfer(player, replay);
            return;
        }
        var token = new Object();
        pendingNavigation.put(player.getUniqueId(), token);
        Menu.close(player, closed -> {
            if (pendingNavigation.remove(closed.getUniqueId(), token)) {
                load(closed, replay);
            }
        });
    }

    private void requestTransfer(Player player, UUID replay) {
        var viewer = player.getUniqueId();
        var token = UUID.randomUUID();
        loading.put(viewer, token);
        Tasks.later(plugin, 1200L, () -> loading.remove(viewer, token));
        boolean staff = player.hasPermission(ReplayPlugin.STAFF_PERMISSION);
        var current = BlockcraftApi.get().playerTasks().currentScreen(player);
        BlockcraftApi.get().playerTasks().write(player, () -> {
            if (repository.accessible(replay, player.getUniqueId(), staff).isEmpty()) {
                return false;
            }
            repository.request(player.getUniqueId(), replay, token);
            return true;
        }, found -> {
            if (loading.get(viewer) != token || !current.getAsBoolean() || !allowed(player)) {
                loading.remove(viewer, token);
                Tasks.async(plugin, () -> repository.cancelRequest(viewer, token));
                return;
            }
            if (found) {
                Menu.close(player);
                player.sendMessage(messages.get("playback.connecting"));
                ProxyMessaging.play(plugin, player, config.playbackGroup());
            } else {
                loading.remove(viewer, token);
                player.sendMessage(messages.get("playback.unavailable"));
            }
        }, ignored -> loading.remove(viewer, token));
    }

    private void load(Player player, UUID replay) {
        if (!allowed(player) || loading.containsKey(player.getUniqueId()) || playback.active(player)) {
            return;
        }
        long active = Bukkit.getOnlinePlayers().stream().filter(playback::active).count();
        if (active + loading.size() >= config.limits().maxPlaybacks()) {
            player.sendMessage(messages.get("playback.full"));
            return;
        }
        var id = player.getUniqueId();
        var token = new Object();
        loading.put(id, token);
        boolean staff = player.hasPermission(ReplayPlugin.STAFF_PERMISSION);
        player.sendMessage(messages.get("playback.loading"));
        try {
            reads.execute(() -> {
                ReplayData data = null;
                try {
                    var entry = repository.accessible(replay, id, staff);
                    if (entry.isPresent() && entry.get().minecraft().equals(minecraft)) {
                        data = store.load(replay);
                        if (!data.header().minecraft().equals(minecraft)) {
                            data = null;
                        }
                    }
                } catch (IOException | RuntimeException error) {
                    plugin.getLogger().log(Level.WARNING, "Could not load replay " + replay, error);
                }
                var loaded = data;
                Tasks.sync(plugin, () -> {
                    if (loading.get(id) != token) {
                        return;
                    }
                    loading.remove(id);
                    if (!player.isOnline() || !allowed(player)) {
                        return;
                    }
                    boolean owns = loaded != null && loaded.header().participants().stream().anyMatch(p -> p.id().equals(id));
                    if (loaded == null || !owns && !player.hasPermission(ReplayPlugin.STAFF_PERMISSION)) {
                        player.sendMessage(messages.get("playback.unavailable"));
                    } else if (playback.start(player, loaded)) {
                        controls.give(player);
                        player.sendMessage(messages.get("playback.started"));
                    } else {
                        player.sendMessage(messages.get("playback.full"));
                    }
                });
            });
        } catch (RejectedExecutionException error) {
            loading.remove(id, token);
            player.sendMessage(messages.get("playback.full"));
        }
    }

    void stop(Player player) {
        pendingNavigation.remove(player.getUniqueId());
        cancelLoading(player);
        playback.stop(player);
        player.sendMessage(messages.get("playback.stopped"));
    }

    private void cancelLoading(Player player) {
        var viewer = player.getUniqueId();
        var token = loading.remove(viewer);
        if (token instanceof UUID request) {
            Tasks.async(plugin, () -> repository.cancelRequest(viewer, request));
        }
    }

    void pause(Player player) {
        if (playback.active(player)) {
            playback.pause(player);
        } else {
            player.sendMessage(messages.get("playback.not-active"));
        }
    }

    void speed(Player player, double speed) {
        if (speed != 0.5 && speed != 1 && speed != 2) {
            player.sendMessage(messages.get("playback.invalid-speed"));
            return;
        }
        if (!playback.speed(player, speed)) {
            player.sendMessage(messages.get("playback.not-active"));
        }
    }

    void seek(Player player, int seconds) {
        if (!playback.seek(player, Math.multiplyExact(seconds, 20))) {
            player.sendMessage(messages.get("playback.seek-failed"));
        }
    }

    void step(Player player, int seconds) {
        playback.state(player).ifPresent(state -> seek(player, Math.clamp(state.tick() / 20 + seconds, 0, state.durationTicks() / 20)));
    }

    void cycleSpeed(Player player) {
        playback.state(player).ifPresent(state -> speed(player, state.speed() < 1 ? 1 : state.speed() < 2 ? 2 : 0.5));
    }

    private boolean allowed(Player player) {
        return player.isOnline() && player.hasPermission(ReplayPlugin.USE_PERMISSION);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!playbackBackend) {
            return;
        }
        var player = event.getPlayer();
        var viewer = player.getUniqueId();
        var token = new Object();
        pendingNavigation.put(viewer, token);
        Tasks.later(plugin, 20L, () -> {
            if (player.isOnline() && pendingNavigation.get(viewer) == token) {
                BlockcraftApi.get().playerTasks().write(player, () -> repository.takeRequest(viewer), request -> {
                    if (pendingNavigation.remove(viewer, token)) {
                        request.ifPresentOrElse(id -> play(player, id), () -> open(player));
                    }
                }, ignored -> pendingNavigation.remove(viewer, token));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingNavigation.remove(event.getPlayer().getUniqueId());
        loading.remove(event.getPlayer().getUniqueId());
    }

    void maintenance() {
        if (!playbackBackend) {
            return;
        }
        var active = Bukkit.getOnlinePlayers().stream().flatMap(player -> playback.state(player).stream())
                .map(PlaybackManager.State::replay).collect(java.util.stream.Collectors.toSet());
        if (!loading.isEmpty() || maintenance.getActiveCount() != 0 || !maintenance.getQueue().isEmpty()) {
            return;
        }
        try {
            maintenance.execute(() -> ReplayRetention.clean(store, repository, config, active, Instant.now(), plugin.getLogger()));
        } catch (RejectedExecutionException ignored) {
        }
    }

    void publish(ReplayStore.Saved saved) {
        store.markReadyAsync(saved.header().id()).whenComplete((ignored, failure) -> {
            if (failure != null) {
                plugin.getLogger().log(Level.SEVERE, "Could not mark replay ready: " + saved.header().id(), failure);
                return;
            }
            try {
                reads.execute(() -> {
                    try {
                        repository.save(saved);
                    } catch (RuntimeException error) {
                        plugin.getLogger().log(Level.SEVERE, "Replay saved but history publication failed: " + saved.header().id(), error);
                    }
                });
            } catch (RejectedExecutionException error) {
                plugin.getLogger().log(Level.SEVERE, "Replay history publication queue is full: " + saved.header().id(), error);
            }
        });
    }

    @Override
    public void close() {
        pendingNavigation.clear();
        loading.clear();
        maintenance.shutdownNow();
        reads.shutdown();
        try {
            if (!reads.awaitTermination(5, TimeUnit.SECONDS)) {
                reads.shutdownNow();
                plugin.getLogger().warning("Replay background tasks did not finish within five seconds");
            }
        } catch (InterruptedException error) {
            reads.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
