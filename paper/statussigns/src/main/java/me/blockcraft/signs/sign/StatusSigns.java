package me.blockcraft.signs.sign;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the registered signs, their last known status and the async refresh loop. */
public final class StatusSigns {

    private final Plugin plugin;
    private final SignStore store;
    private final StatusPinger pinger;
    private final SignRenderer renderer;
    private final Map<Location, StatusSign> signs = new ConcurrentHashMap<>();
    private final Map<Location, ServerStatus> statuses = new ConcurrentHashMap<>();
    private BukkitTask refreshTask;

    public StatusSigns(Plugin plugin, SignStore store, StatusPinger pinger, SignRenderer renderer) {
        this.plugin = plugin;
        this.store = store;
        this.pinger = pinger;
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

    public boolean isOnline(StatusSign sign) {
        return statuses.getOrDefault(sign.location(), new ServerStatus.Offline()).isOnline();
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
        List<StatusSign> snapshot = List.copyOf(signs.values());
        var results = new LinkedHashMap<StatusSign, ServerStatus>();
        for (StatusSign sign : snapshot) {
            results.put(sign, pinger.ping(sign.host(), sign.port()));
        }
        Bukkit.getScheduler().runTask(plugin, () -> results.forEach((sign, status) -> {
            statuses.put(sign.location(), status);
            renderer.render(sign, status);
        }));
    }
}
