package me.blockcraft.signs.sign;

import me.blockcraft.api.network.ServerGroups;
import me.blockcraft.common.network.GroupStatus;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Owns the registered signs and redraws them whenever the network's group status is refreshed. */
public final class StatusSigns {

    private final Plugin plugin;
    private final SignStore store;
    private final ServerGroups groups;
    private final SignRenderer renderer;
    private final Map<Location, StatusSign> signs = new ConcurrentHashMap<>();

    public StatusSigns(Plugin plugin, SignStore store, ServerGroups groups, SignRenderer renderer) {
        this.plugin = plugin;
        this.store = store;
        this.groups = groups;
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
        return groups.status(sign.group());
    }

    /** Every group with a live server, for tab completion. */
    public Set<String> knownGroups() {
        return groups.known();
    }

    public void start() {
        groups.onRefresh(plugin, () -> signs.values().forEach(sign -> renderer.render(sign, status(sign))));
    }
}
