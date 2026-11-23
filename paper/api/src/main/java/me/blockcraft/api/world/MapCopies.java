package me.blockcraft.api.world;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * Every map's world, a copy of its template under the map's key. A world a round played in is copied anew in the
 * background once it is unloaded, so that the next round on that map finds it as it was; a round that picks it
 * before the copy is done waits for it. A {@code RoundScope} that {@code own}s the world unloads it and hands it to
 * {@link #recopy}. The copies run on threads of their own rather than Bukkit's async scheduler, which starts a task
 * only on the main thread's next tick: a main thread waiting in {@link #load} for a copy queued that tick would wait
 * forever.
 */
public final class MapCopies {

    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);

    private final Plugin plugin;
    private final TemplateDimensions dimensions;
    private final Map<String, Consumer<String>> copiers = new HashMap<>();
    private final Map<String, CompletableFuture<Void>> copies = new HashMap<>();

    /** On the main thread, which resolves where the copies go before any copy runs in the background. */
    public MapCopies(Plugin plugin, TemplateDimensions dimensions) {
        this.plugin = plugin;
        this.dimensions = dimensions;
        dimensions.folder("");
    }

    /** Copies {@code template} into place now, as the world of the map {@code key}. */
    public void copy(Path template, String key) {
        remember(key, name -> dimensions.copy(template, name));
    }

    /** As {@link #copy}, for a whole level's template, such as the main world's, of which the overworld is played. */
    public void copyLevel(WorldTemplate level, String key) {
        remember(key, name -> dimensions.copyLevel(level, name));
    }

    /** As {@link #copy}, off the main thread; {@link #load} waits for it. */
    public void copyInBackground(Path template, String key) {
        copiers.put(key, name -> dimensions.copy(template, name));
        recopy(key);
    }

    /** As {@link #copyLevel}, off the main thread; {@link #load} waits for it. */
    public void copyLevelInBackground(WorldTemplate level, String key) {
        copiers.put(key, name -> dimensions.copyLevel(level, name));
        recopy(key);
    }

    private void remember(String key, Consumer<String> copier) {
        copier.accept(key);
        copiers.put(key, copier);
    }

    /** Keeps {@code world}, loaded and as built, as the template its map is copied from after every round. */
    public void keep(World world, Path template) {
        var key = dimensions.nameOf(world).orElseThrow(() -> new IllegalArgumentException("Not a map world: " + world.getKey()));
        dimensions.publish(world, template);
        copiers.put(key, name -> dimensions.copy(template, name));
    }

    /** The world of the map {@code key}; a background copy that failed is tried once more here, and throws if it fails again. */
    public World load(String key) {
        var copy = copies.getOrDefault(key, DONE);
        try {
            copy.join();
        } catch (CompletionException failed) {
            plugin.getSLF4JLogger().warn("Copying the map {} failed; trying once more", key, failed.getCause());
            copies.remove(key);
            copiers.get(key).accept(key);
        }
        return dimensions.load(key);
    }

    /** Unloads a world a round played in, which nobody may be in any more, and copies its map anew. */
    public void renew(World world) {
        if (dimensions.nameOf(world).filter(copiers::containsKey).isEmpty()) {
            return;
        }
        if (!Bukkit.unloadWorld(world, false)) {
            plugin.getSLF4JLogger().warn("Could not unload the map {}; its next round plays on what this one left", world.getKey());
            return;
        }
        recopy(world);
    }

    /** Copies the map of a world that is already unloaded anew, in the background. */
    public void recopy(World world) {
        dimensions.nameOf(world).filter(copiers::containsKey).ifPresent(this::recopy);
    }

    private void recopy(String key) {
        if (dimensions.loaded(key).isPresent()) {
            plugin.getSLF4JLogger().warn("The map {} is still loaded, so it is not copied anew", key);
            return;
        }
        var copier = copiers.get(key);
        copies.put(key, CompletableFuture.runAsync(() -> copier.accept(key),
                task -> Thread.ofVirtual().name("map-copy-" + key).start(task)));
    }
}
