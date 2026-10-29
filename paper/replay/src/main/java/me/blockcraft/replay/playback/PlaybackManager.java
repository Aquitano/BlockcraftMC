package me.blockcraft.replay.playback;

import me.blockcraft.common.message.Messages;
import me.blockcraft.replay.data.ReplayHeader;
import me.blockcraft.replay.data.ReplayLimits;
import org.bukkit.Bukkit;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class PlaybackManager implements AutoCloseable {

    public record State(UUID replay, int tick, int durationTicks, boolean paused, double speed, boolean preparing) {
        public boolean finished() {
            return tick >= durationTicks;
        }
    }

    private final Plugin plugin;
    private final ReplayLimits limits;
    private final Messages messages;
    private final Map<UUID, PlaybackSession> sessions = new HashMap<>();
    private final ThreadPoolExecutor cleanup;
    private final PlaybackGuard guard;
    private final BukkitTask task;
    private boolean closed;

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

    public boolean start(Player viewer, PlaybackTimeline timeline) {
        if (closed || active(viewer)
                || sessions.size() + cleanup.getActiveCount() + cleanup.getQueue().size() >= limits.maxPlaybacks() || !viewer.isOnline()
                || viewer.isDead() || timeline.empty()
                || timeline.header().participants().size() > limits.maxParticipants()) {
            return false;
        }
        var session = new PlaybackSession(plugin, viewer, timeline, messages);
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

    public void toggle(Player viewer) {
        var session = sessions.get(viewer.getUniqueId());
        if (session != null) {
            session.toggle();
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

    /** The participants on screen right now, for the viewer to jump to. */
    public List<ReplayHeader.Participant> present(Player viewer) {
        var session = sessions.get(viewer.getUniqueId());
        return session == null ? List.of() : session.present();
    }

    public boolean focus(Player viewer, UUID participant) {
        var session = sessions.get(viewer.getUniqueId());
        return session != null && session.focus(participant);
    }

    /** Freezes the playback without leaving its world, while the viewer is on the way to another server. */
    public void leave(Player viewer) {
        var session = sessions.get(viewer.getUniqueId());
        if (session != null) {
            session.leave();
        }
    }

    /** Ends the playback here and takes the viewer back to where they stood on this server. */
    public void stop(Player viewer) {
        end(viewer, false);
    }

    /** Ends the playback of a viewer leaving the server, whom a failed return no longer concerns. */
    void quit(Player viewer) {
        end(viewer, true);
    }

    private void end(Player viewer, boolean quitting) {
        var session = sessions.remove(viewer.getUniqueId());
        if (session == null) {
            return;
        }
        try {
            if (!session.restore() && !quitting) {
                viewer.sendMessage(messages.get("playback.restore-failed"));
                Bukkit.getWorlds().stream().filter(world -> !world.getKey().getNamespace().equals("replay"))
                        .findFirst().ifPresent(world -> viewer.teleport(world.getSpawnLocation()));
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.SEVERE, "Could not restore replay viewer " + viewer.getUniqueId(), failure);
        } finally {
            for (var other : Bukkit.getOnlinePlayers()) {
                if (!active(other)) {
                    other.showPlayer(plugin, viewer);
                    viewer.showPlayer(plugin, other);
                }
            }
            discard(session.worlds());
        }
    }

    /** Unloads a tick later, once a viewer who is quitting has been taken out of the world. */
    private void discard(List<World> worlds) {
        Runnable unload = () -> {
            var folders = worlds.stream().map(this::unload).flatMap(Optional::stream).toList();
            if (!folders.isEmpty()) {
                cleanup.execute(() -> folders.forEach(this::deleteDimension));
            }
        };
        if (closed) {
            unload.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, unload);
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
        int writes = Math.max(1, limits.blocksPerTick() / Math.max(1, sessions.size()));
        for (var session : List.copyOf(sessions.values())) {
            if (!session.viewer().isOnline()) {
                quit(session.viewer());
                continue;
            }
            try {
                session.pulse(writes);
            } catch (RuntimeException failure) {
                fail(session.viewer(), failure);
            }
        }
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
