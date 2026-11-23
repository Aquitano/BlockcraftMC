package me.blockcraft.api.world;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Worlds kept as templates inside a map bundle and loaded only when needed. Since 26.2 every world of a server is a
 * dimension of its one level, so a template is copied to {@code <level>/dimensions/<namespace>/<name>} before it loads
 * as the world {@code <namespace>:<name>}. Nothing generates around it: what lies beyond its build is void.
 * The copy saves like any world, because it is replaced before every load anyway. Minecraft never unloads a chunk
 * of a world with saving off, so such a world would keep every chunk a visitor has seen in memory.
 */
public final class TemplateDimensions {

    /** A name becomes the path of its dimension, so it keeps to what a namespaced key allows. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_.-]+");

    private final String namespace;
    private final Consumer<World> settle;
    private final Set<String> copyingOrLoading = ConcurrentHashMap.newKeySet();
    private volatile Path root;

    /** {@code settle} sets the rules every one of these worlds keeps, right after it loads. */
    public TemplateDimensions(String namespace, Consumer<World> settle) {
        this.namespace = namespace;
        this.settle = settle;
    }

    public static boolean isValidName(String name) {
        return NAME.matcher(name).matches();
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(namespace, name);
    }

    /** The world of {@code name} if it is loaded, so that nothing loads it twice. */
    public Optional<World> loaded(String name) {
        return Optional.ofNullable(Bukkit.getWorld(key(name)));
    }

    /** The name of {@code world} if it is one of these. */
    public Optional<String> nameOf(World world) {
        return world.getKey().getNamespace().equals(namespace) ? Optional.of(world.getKey().getKey()) : Optional.empty();
    }

    /** Blocking file work, run before {@link #load}; only an unloaded dimension may be replaced. */
    public void copy(Path template, String name) {
        reserved(name, () -> copyReserved(template, name));
    }

    /** {@link #copy} for a whole level's template, of which only the overworld and the map's bundle are played. */
    public void copyLevel(WorldTemplate level, String name) {
        reserved(name, () -> copyLevelReserved(level, name));
    }

    /**
     * On the main thread: holds the map, unloaded, so that nothing loads, copies or publishes it until
     * {@link #release}; false if it is loaded or held already. Each map is held on its own, so a background copy
     * waiting on another process's template lock never holds up the main thread's work on a different map.
     */
    public boolean reserve(String name) {
        return loaded(name).isEmpty() && copyingOrLoading.add(name);
    }

    public void release(String name) {
        copyingOrLoading.remove(name);
    }

    /** {@link #copy} into a map held with {@link #reserve}, from any thread. */
    public void copyReserved(Path template, String name) {
        WorldTemplate.at(template).restore(folder(name));
    }

    /** {@link #copyLevel} into a map held with {@link #reserve}, from any thread. */
    public void copyLevelReserved(WorldTemplate level, String name) {
        level.restoreDimension(folder(name));
    }

    private void reserved(String name, Runnable work) {
        if (!reserve(name)) {
            throw new IllegalStateException("The world is loaded, or being copied or loaded: " + name);
        }
        try {
            work.run();
        } finally {
            release(name);
        }
    }

    public World load(String name) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Worlds must be loaded on the main thread");
        }
        if (!copyingOrLoading.add(name)) {
            throw new IllegalStateException("The world is being copied or loaded: " + name);
        }
        try {
            return loaded(name).orElseGet(() -> {
                var world = WorldCreator.ofKey(key(name))
                        .generator(new ChunkGenerator() { })
                        .createWorld();
                if (world == null) {
                    throw new IllegalStateException("Could not load the world " + name);
                }
                settle.accept(world);
                return world;
            });
        } finally {
            copyingOrLoading.remove(name);
        }
    }

    /** Flushes a world a builder edited and copies it back into its template. */
    public void publish(World world, Path template) {
        var name = nameOf(world).orElseThrow(() -> new IllegalArgumentException("Not a " + namespace + " world: " + world.getKey()));
        if (!copyingOrLoading.add(name)) {
            throw new IllegalStateException("The world is being copied or loaded: " + name);
        }
        try {
            world.save(true);
            WorldTemplate.at(template).publish(folder(name));
        } finally {
            copyingOrLoading.remove(name);
        }
    }

    /** Resolved once on the main thread, so that background copies never read Bukkit's world list. */
    Path folder(String name) {
        var dimensions = root;
        if (dimensions == null) {
            var level = Bukkit.getWorlds().getFirst().getName();
            dimensions = Bukkit.getWorldContainer().toPath().resolve(level).resolve("dimensions").resolve(namespace);
            root = dimensions;
        }
        return dimensions.resolve(name);
    }
}
