package me.blockcraft.sternenfront.appearance;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.item.Items;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
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
        return Items.of(Material.PAPER).model(Key.key("blockcraft", "battle/" + name)).build();
    }

    public ItemDisplay part(String model, Location at) {
        var display = at.getWorld().spawn(at, ItemDisplay.class, spawned -> {
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            spawned.setItemStack(model(model));
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDuration(4);
            spawned.setDisplayWidth(4);
            spawned.setDisplayHeight(4);
        });
        parts.add(display);
        return display;
    }

    public void refresh() {
        for (var player : Bukkit.getOnlinePlayers()) {
            boolean show = player.getWorld().equals(origin.getWorld()) && BlockcraftApi.get().resourcePack().applied(player);
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
