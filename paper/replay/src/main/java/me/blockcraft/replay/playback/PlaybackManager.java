package me.blockcraft.replay.playback;

import me.blockcraft.common.message.Messages;
import me.blockcraft.replay.data.ReplayData;
import me.blockcraft.replay.data.ReplayLimits;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class PlaybackManager implements AutoCloseable {

    public record State(UUID replay, int tick, int durationTicks, boolean paused, double speed, boolean preparing) { }

    private final Plugin plugin;
    private final ReplayLimits limits;
    private final Messages messages;
    private final Map<UUID, PlaybackSession> sessions = new HashMap<>();
    private final ThreadPoolExecutor cleanup;
    private final PlaybackGuard guard;
    private final BukkitTask task;
    private boolean closed;
    private int statusTicks;

    public PlaybackManager(Plugin plugin, ReplayLimits limits, Messages messages) {
        this.plugin = plugin;
        this.limits = limits;
        this.messages = messages;
        cleanup = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(limits.maxPlaybacks()),
                Thread.ofPlatform().daemon(true).name("blockcraft-replay-cleanup").factory());
        guard = new PlaybackGuard(this, messages);
        Bukkit.getPluginManager().registerEvents(guard, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 1L, 1L);
    }

    public boolean start(Player viewer, ReplayData data) {
        if (closed || active(viewer)
                || sessions.size() + cleanup.getActiveCount() + cleanup.getQueue().size() >= limits.maxPlaybacks() || !viewer.isOnline()
                || viewer.isDead() || data.events().isEmpty()
                || data.header().participants().size() > limits.maxParticipants()) {
            return false;
        }
        var session = new PlaybackSession(viewer, data);
        sessions.put(viewer.getUniqueId(), session);
        try {
            session.begin();
            for (var other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(viewer)) {
                    other.hidePlayer(plugin, viewer);
                    viewer.hidePlayer(plugin, other);
                }
            }
            return true;
        } catch (RuntimeException failure) {
            fail(viewer, failure);
            return false;
        }
    }

    public boolean active(Player viewer) {
        return sessions.containsKey(viewer.getUniqueId());
    }

    public Optional<State> state(Player viewer) {
        return Optional.ofNullable(sessions.get(viewer.getUniqueId())).map(PlaybackSession::state);
    }

    public void pause(Player viewer) {
        var session = sessions.get(viewer.getUniqueId());
        if (session != null) {
            session.pause();
        }
    }

    public boolean speed(Player viewer, double speed) {
        var session = sessions.get(viewer.getUniqueId());
        return session != null && session.speed(speed);
    }

    public boolean seek(Player viewer, int ticks) {
        var session = sessions.get(viewer.getUniqueId());
        return session != null && session.seek(ticks);
    }

    public void stop(Player viewer) {
        var session = sessions.remove(viewer.getUniqueId());
        if (session == null) {
            return;
        }
        try {
            session.restore();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore replay viewer " + viewer.getUniqueId(), failure);
            viewer.sendMessage(messages.get("playback.restore-failed"));
            Bukkit.getWorlds().stream().filter(world -> !world.getKey().getNamespace().equals("replay"))
                    .findFirst().ifPresent(world -> {
                        if (!viewer.teleport(world.getSpawnLocation())) {
                            plugin.getLogger().severe("Could not move replay viewer to fallback world " + viewer.getUniqueId());
                        }
                    });
        } finally {
            viewer.sendActionBar(Component.empty());
            for (var other : Bukkit.getOnlinePlayers()) {
                if (!active(other)) {
                    other.showPlayer(plugin, viewer);
                    viewer.showPlayer(plugin, other);
                }
            }
            var folders = session.worlds().stream().map(this::unload).flatMap(Optional::stream).toList();
            if (!folders.isEmpty()) {
                cleanup.execute(() -> folders.forEach(this::deleteDimension));
            }
        }
    }

    boolean replayWorld(World world) {
        return sessions.values().stream().anyMatch(session -> session.owns(world));
    }

    boolean permittedWorld(Player viewer, World world) {
        var session = sessions.get(viewer.getUniqueId());
        if (session != null) {
            return session.owns(world);
        }
        return !replayWorld(world);
    }

    boolean permittedPosition(Player viewer, Location location) {
        var session = sessions.get(viewer.getUniqueId());
        return session == null || session.permittedPosition(location);
    }

    void hideFrom(Player joined) {
        for (var session : sessions.values()) {
            var viewer = session.viewer();
            if (!joined.equals(viewer)) {
                joined.hidePlayer(plugin, viewer);
                viewer.hidePlayer(plugin, joined);
            }
        }
    }

    private void pulse() {
        int sessionCount = Math.max(1, sessions.size());
        int budget = Math.max(1, limits.blocksPerTick() / sessionCount);
        int scans = Math.max(1, limits.blocksScannedPerTick() / sessionCount);
        boolean showStatus = ++statusTicks % 20 == 0;
        for (var session : List.copyOf(sessions.values())) {
            if (!session.viewer().isOnline()) {
                stop(session.viewer());
                continue;
            }
            try {
                session.pulse(budget, scans);
                if (showStatus) {
                    showStatus(session);
                }
            } catch (RuntimeException failure) {
                fail(session.viewer(), failure);
            }
        }
    }

    private void showStatus(PlaybackSession session) {
        var state = session.state();
        String stateKey = state.preparing() ? "preparing"
                : state.tick() >= state.durationTicks() ? "finished"
                : state.paused() ? "paused" : "playing";
        session.viewer().sendActionBar(messages.get("playback.status",
                Placeholder.unparsed("time", time(state.tick())),
                Placeholder.unparsed("duration", time(state.durationTicks())),
                Formatter.number("speed", state.speed()),
                Placeholder.component("state", messages.get("playback.state." + stateKey))));
    }

    private static String time(int ticks) {
        return String.format(Locale.ROOT, "%d:%02d", ticks / 1200, ticks / 20 % 60);
    }

    private void fail(Player viewer, RuntimeException failure) {
        plugin.getLogger().log(Level.WARNING, "Replay playback failed for " + viewer.getUniqueId(), failure);
        stop(viewer);
        viewer.sendMessage(messages.get("playback.failed"));
    }

    private Optional<Path> unload(World world) {
        var key = world.getKey();
        var folder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        if (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, false)) {
            plugin.getLogger().warning("Could not unload replay dimension " + key);
            return Optional.empty();
        }
        if (!key.getNamespace().equals("replay") || !key.getKey().matches("[a-f0-9-]{36}_[a-f0-9-]{36}")
                || !folder.getFileName().toString().equals(key.getKey())
                || !folder.getParent().getFileName().toString().equals("replay")) {
            plugin.getLogger().warning("Refusing to delete unexpected replay dimension path " + folder);
            return Optional.empty();
        }
        return Optional.of(folder);
    }

    private void deleteDimension(Path folder) {
        try {
            for (Path ancestor = folder; ancestor != null; ancestor = ancestor.getParent()) {
                if (Files.isSymbolicLink(ancestor)) {
                    throw new IOException("Replay dimension path contains a symbolic link");
                }
            }
            if (!Files.exists(folder)) {
                return;
            }
            try (var files = Files.walk(folder)) {
                for (var path : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not remove replay dimension " + folder, failure);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        task.cancel();
        for (var session : List.copyOf(sessions.values())) {
            stop(session.viewer());
        }
        HandlerList.unregisterAll(guard);
        cleanup.shutdown();
    }
}
