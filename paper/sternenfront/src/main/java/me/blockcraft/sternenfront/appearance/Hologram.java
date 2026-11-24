package me.blockcraft.sternenfront.appearance;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class Hologram implements AutoCloseable {

    private final Plugin plugin;
    private final Location origin;
    private final List<ItemDisplay> parts = new ArrayList<>();
    private final Set<UUID> viewers = new HashSet<>();

    public Hologram(Plugin plugin, Location origin) {
        this.plugin = plugin;
        this.origin = origin.clone();
    }

    public static ItemStack model(String name) {
        return Displays.model("battle/" + name);
    }

    public ItemDisplay part(String model, Location at) {
        var display = Displays.item(at, model(model), spawned -> {
            spawned.setVisibleByDefault(false);
            spawned.setBrightness(Displays.LIT);
            spawned.setInterpolationDuration(4);
            spawned.setDisplayWidth(4);
            spawned.setDisplayHeight(4);
        });
        parts.add(display);
        return display;
    }

    public void refresh() {
        for (var player : Bukkit.getOnlinePlayers()) {
            boolean show = player.getWorld().equals(origin.getWorld());
            if (show && viewers.add(player.getUniqueId())) {
                parts.forEach(part -> player.showEntity(plugin, part));
            } else if (!show && viewers.remove(player.getUniqueId())) {
                parts.forEach(part -> player.hideEntity(plugin, part));
            }
        }
        viewers.removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    @Override
    public void close() {
        parts.forEach(Entity::remove);
        parts.clear();
        viewers.clear();
    }
}
