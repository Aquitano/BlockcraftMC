package me.blockcraft.signs.sign;

import me.blockcraft.common.db.DatabaseException;
import me.blockcraft.common.network.GroupStatus;
import me.blockcraft.common.network.ServerRecord;
import me.blockcraft.common.network.ServerRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Owns the registered signs, the last known status per group and the async refresh loop over {@code bc_servers}. */
public final class StatusSigns {

    private final Plugin plugin;
    private final SignStore store;
    private final ServerRepository servers;
    private final int staleSeconds;
    private final SignRenderer renderer;
    private final Map<Location, StatusSign> signs = new ConcurrentHashMap<>();
    private volatile Map<String, GroupStatus> groups = Map.of();
    private BukkitTask refreshTask;

    public StatusSigns(Plugin plugin, SignStore store, ServerRepository servers, int staleSeconds, SignRenderer renderer) {
        this.plugin = plugin;
        this.store = store;
        this.servers = servers;
        this.staleSeconds = staleSeconds;
        this.renderer = renderer;
    }

    public int load() {
        store.load().forEach(sign -> signs.put(sign.location(), sign));
        return signs.size();
    }

    public void add(StatusSign sign) {
        signs.put(sign.location(), sign);
        store.add(sign);
    }

    public Optional<StatusSign> find(Location location) {
        return Optional.ofNullable(signs.get(location));
    }

    public GroupStatus status(StatusSign sign) {
        return groups.getOrDefault(sign.group(), GroupStatus.OFFLINE);
    }

    /** Every group with a live server, for tab completion. */
    public Set<String> knownGroups() {
        return groups.keySet();
    }

    public void start(long intervalTicks) {
        refreshTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 0L, intervalTicks);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
    }

    private void refresh() {
        Map<String, GroupStatus> fresh;
        try {
            fresh = servers.alive(staleSeconds).stream()
                    .collect(Collectors.groupingBy(ServerRecord::group,
                            Collectors.collectingAndThen(Collectors.toList(), GroupStatus::of)));
        } catch (DatabaseException e) {
            plugin.getLogger().warning("Could not read bc_servers: " + e.getCause().getMessage());
            return;
        }
        groups = fresh;
        if (plugin.isEnabled()) {
            List<StatusSign> snapshot = List.copyOf(signs.values());
            Bukkit.getScheduler().runTask(plugin, () -> snapshot.forEach(sign -> renderer.render(sign, status(sign))));
        }
    }
}
