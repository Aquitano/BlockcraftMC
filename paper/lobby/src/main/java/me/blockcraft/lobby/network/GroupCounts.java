package me.blockcraft.lobby.network;

import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.stream.Collectors;

/** Players online per server group, refreshed from {@code bc_servers} for the navigator. */
public final class GroupCounts {

    private final Plugin plugin;
    private final ServerRepository servers;
    private final int staleSeconds;
    private volatile Map<String, Integer> online = Map.of();
    private BukkitTask task;

    public GroupCounts(Plugin plugin, ServerRepository servers, int staleSeconds) {
        this.plugin = plugin;
        this.servers = servers;
        this.staleSeconds = staleSeconds;
    }

    public int online(String group) {
        return online.getOrDefault(group, 0);
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
            online = servers.alive(staleSeconds).stream()
                    .collect(Collectors.groupingBy(ServerRecord::group, Collectors.summingInt(server -> server.status().online())));
        } catch (DatabaseException e) {
            plugin.getLogger().warning("Could not read bc_servers: " + e.getCause().getMessage());
        }
    }
}
