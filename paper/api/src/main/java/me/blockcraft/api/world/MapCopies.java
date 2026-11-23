package me.blockcraft.api.world;

import me.blockcraft.api.BlockcraftApi;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Every map's world, a copy of its template under the map's key. A world a round played in is copied anew in the
 * background once it is unloaded, so that the next round on that map finds it as it was; a round that picks it
 * before the copy is done cannot load it and falls back. A {@code RoundScope} that {@code own}s the world unloads it and hands it to
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
    private final Set<String> mainCopies = new HashSet<>();

    /**
     * On the main thread while the plugin enables, which resolves where the copies go before any copy runs in the
     * background. A copy of the main world's map is copied anew once {@code /map save} published that world, since it
     * was taken from the template before the save.
     */
    public MapCopies(Plugin plugin, TemplateDimensions dimensions) {
        this.plugin = plugin;
        this.dimensions = dimensions;
        dimensions.folder("");
        plugin.getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPublished(MapPublishedEvent event) {
                if (event.world().equals(Bukkit.getWorlds().getFirst())) {
                    mainCopies.stream().filter(key -> dimensions.loaded(key).isEmpty()).forEach(MapCopies.this::recopy);
                }
            }
        }, plugin);
    }

    /** Copies {@code template} into place now, as the world of the map {@code key}. */
    public void copy(Path template, String key) {
        copiers.put(key, name -> dimensions.copyReserved(template, name));
        dimensions.copy(template, key);
    }

    /** As {@link #copy}, for a whole level's template, such as the main world's, of which the overworld is played. */
    public void copyLevel(WorldTemplate level, String key) {
        rememberLevel(level, key);
        copiers.put(key, name -> dimensions.copyLevelReserved(level, name));
        dimensions.copyLevel(level, key);
    }

    /** As {@link #copy}, off the main thread; {@link #load} refuses the map until it is done. */
    public void copyInBackground(Path template, String key) {
        copiers.put(key, name -> dimensions.copyReserved(template, name));
        recopy(key);
    }

    /** As {@link #copyLevel}, off the main thread; {@link #load} refuses the map until it is done. */
    public void copyLevelInBackground(WorldTemplate level, String key) {
        rememberLevel(level, key);
        copiers.put(key, name -> dimensions.copyLevelReserved(level, name));
        recopy(key);
    }

    /** Keeps {@code world}, loaded and as built, as the template its map is copied from after every round. */
    public void keep(World world, Path template) {
        var key = dimensions.nameOf(world).orElseThrow(() -> new IllegalArgumentException("Not a map world: " + world.getKey()));
        dimensions.publish(world, template);
        copiers.put(key, name -> dimensions.copyReserved(template, name));
    }

    private void rememberLevel(WorldTemplate level, String key) {
        var main = BlockcraftApi.get().map().template();
        if (main.filter(template -> template.directory().equals(level.directory())).isPresent()) {
            mainCopies.add(key);
        }
    }

    /** Whether a copy of the map {@code key} is under way, which a game can wait for rather than fall back. */
    public boolean copying(String key) {
        var copy = copies.get(key);
        return copy != null && !copy.isDone();
    }

    /** Whether the last copy of the map {@code key} failed; {@link #load} starts another and throws. */
    public boolean failed(String key) {
        var copy = copies.get(key);
        return copy != null && copy.isCompletedExceptionally();
    }

    /** Whether the map {@code key} can be loaded now, its copy done, so that a game can choose a map that is. */
    public boolean ready(String key) {
        var copy = copies.getOrDefault(key, DONE);
        return copy.isDone() && !copy.isCompletedExceptionally();
    }

    /**
     * The world of the map {@code key}, on the main thread. The main thread never waits for a copy, which another
     * process's template lock can hold up for minutes: a copy not done yet, or one that failed, throws, and the caller
     * falls back. A failed copy is tried again in the background for a later round.
     */
    public World load(String key) {
        var copy = copies.getOrDefault(key, DONE);
        if (!copy.isDone()) {
            throw new IllegalStateException("The map " + key + " is still being copied");
        }
        if (copy.isCompletedExceptionally()) {
            recopy(key);
            throw new IllegalStateException("Copying the map " + key + " failed", copy.exceptionNow());
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

    /**
     * Holds the map from the main thread until the copy is done, so that nothing can load it between the check that it
     * is unloaded and the copy, such as a builder's edit.
     */
    private void recopy(String key) {
        if (!dimensions.reserve(key)) {
            plugin.getSLF4JLogger().warn("The map {} is loaded or busy, so it is not copied anew", key);
            return;
        }
        var copier = copiers.get(key);
        copies.put(key, CompletableFuture.runAsync(() -> {
            try {
                copier.accept(key);
            } finally {
                dimensions.release(key);
            }
        }, task -> Thread.ofVirtual().name("map-copy-" + key).start(task)));
    }
}
