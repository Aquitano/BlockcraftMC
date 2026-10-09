package me.blockcraft.api.network;

import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.GroupStatus;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/** Availability and population of every server group, read from {@code bc_servers} once for all plugins on this server. */
public final class ServerGroups {

    private record Subscriber(Plugin owner, Runnable action) {
    }

    private final Plugin plugin;
    private final ServerRepository servers;
    private final int staleSeconds;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    private volatile Map<String, GroupStatus> groups = Map.of();
    private BukkitTask task;

    public ServerGroups(Plugin plugin, ServerRepository servers, int staleSeconds) {
        this.plugin = plugin;
        this.servers = servers;
        this.staleSeconds = staleSeconds;
    }

    public GroupStatus status(String group) {
        return groups.getOrDefault(group, GroupStatus.OFFLINE);
    }

    /** Every group with a live server. */
    public Set<String> known() {
        return groups.keySet();
    }

    /** Runs {@code action} on the main thread after every refresh, for as long as {@code owner} is enabled. */
    public void onRefresh(Plugin owner, Runnable action) {
        subscribers.add(new Subscriber(owner, action));
    }

    public void start(long intervalTicks) {
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 0L, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    private void refresh() {
        try {
            groups = servers.alive(staleSeconds).stream()
                    .collect(Collectors.groupingBy(ServerRecord::group,
                            Collectors.collectingAndThen(Collectors.toList(), GroupStatus::of)));
        } catch (DatabaseException e) {
            plugin.getLogger().warning("Could not read bc_servers: " + e.getCause().getMessage());
            return;
        }
        if (plugin.isEnabled() && !subscribers.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> subscribers.stream()
                    .filter(subscriber -> subscriber.owner().isEnabled())
                    .forEach(subscriber -> subscriber.action().run()));
        }
    }
}
