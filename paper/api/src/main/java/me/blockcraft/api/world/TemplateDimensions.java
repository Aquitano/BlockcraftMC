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
    public synchronized void copy(Path template, String name) {
        if (!copyingOrLoading.add(name)) {
            throw new IllegalStateException("The world is being copied or loaded: " + name);
        }
        try {
            if (loaded(name).isPresent()) {
                throw new IllegalStateException("Cannot copy into a loaded world: " + name);
            }
            WorldTemplate.at(template).restore(folder(name));
        } finally {
            copyingOrLoading.remove(name);
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
    public synchronized void publish(World world, Path template) {
        var name = nameOf(world).orElseThrow(() -> new IllegalArgumentException("Not a " + namespace + " world: " + world.getKey()));
        world.save(true);
        WorldTemplate.at(template).publish(folder(name));
    }

    private Path folder(String name) {
        var level = Bukkit.getWorlds().getFirst().getName();
        return Bukkit.getWorldContainer().toPath().resolve(level).resolve("dimensions").resolve(namespace).resolve(name);
    }
}
