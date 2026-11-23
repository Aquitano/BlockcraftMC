package me.blockcraft.ttt.arena;

import me.blockcraft.api.world.TemplateDimensions;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Every map's world, a copy of its template under the map's key. A world a round played in is unloaded afterwards
 * and copied anew in the background, so that the next round on that map finds it as it was; a round that picks it
 * before the copy is done waits for it.
 */
public final class MapCopies {

    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    private final Plugin plugin;
    private final TemplateDimensions dimensions;
    private final Map<String, Path> templates = new HashMap<>();
    private final Map<String, CompletableFuture<Void>> copies = new HashMap<>();

    public MapCopies(Plugin plugin, TemplateDimensions dimensions) {
        this.plugin = plugin;
        this.dimensions = dimensions;
    }

    /** Copies {@code template} into place now, as the world of the map {@code key}. */
    public void copy(Path template, String key) {
        dimensions.copy(template, key);
        templates.put(key, template);
    }

    /** Keeps {@code world}, loaded and as built, as the template its map is copied from after every round. */
    public void keep(World world, Path template) {
        var key = dimensions.nameOf(world).orElseThrow(() -> new IllegalArgumentException("Not a map world: " + world.getKey()));
        dimensions.publish(world, template);
        templates.put(key, template);
    }


    public World load(String key) {
        var copy = copies.getOrDefault(key, DONE);
        if (!copy.isDone()) throw new IllegalStateException("The map is still copying: " + key);
        copy.getNow(null);
        return dimensions.load(key);
    }

    public boolean ready(String key) {
        return copies.getOrDefault(key, DONE).isDone();
    }

    /** Unloads a world a round played in, which nobody may be in any more, and copies its map anew. */
    public void renew(World world) {
        var key = dimensions.nameOf(world).filter(templates::containsKey);
        if (key.isEmpty()) {
            return;
        }
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getSLF4JLogger().warn("Could not unload the TTT map {}; its next round plays on what this one left", key.get());
            return;
        }
        String name = key.get();
        var template = templates.get(name);
        copies.put(name, CompletableFuture.runAsync(() -> {
            try {
                dimensions.copy(template, name);
            } catch (RuntimeException failure) {
                plugin.getSLF4JLogger().warn("Copying the TTT map {} failed; trying once more", name, failure);
                dimensions.copy(template, name);
            }
        }, task -> Bukkit.getScheduler().runTaskAsynchronously(plugin, task)));
    }
}
