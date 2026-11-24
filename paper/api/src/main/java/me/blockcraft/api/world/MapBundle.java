package me.blockcraft.api.world;

import me.blockcraft.common.config.YamlFile;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The map this server runs: the main world folder plus the {@code blockcraft/} directory inside it, where every
 * plugin keeps what belongs to the map (spawns, signs, hologram positions) and {@code map.yml} names the map and
 * its builders. With a {@link WorldTemplate} configured the whole bundle is restored at every start and
 * {@link #save()} publishes the running one, so a map travels between servers as one folder.
 */
public final class MapBundle {

    private static final String DIRECTORY = "blockcraft";
    private static final String META = "map.yml";

    /** What {@code map.yml} says of a map; the name falls back to the folder's when the file has none. */
    record Meta(String name, List<String> builders) {

        static Meta of(Path level, String fallbackName) {
            var meta = loadMeta(level.resolve(DIRECTORY).resolve(META));
            String name = meta.get("name") instanceof String value && !value.isBlank() ? value : fallbackName;
            List<String> builders = meta.get("builders") instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
            return new Meta(name, builders);
        }
    }

    private final World world;
    private final List<WorldTemplate> rotation;
    private final Meta meta;

    private MapBundle(World world, List<WorldTemplate> rotation, Meta meta) {
        this.world = world;
        this.rotation = List.copyOf(rotation);
        this.meta = meta;
    }

    /** {@code rotation} holds every configured template, the main world's first. */
    public static MapBundle load(World world, List<WorldTemplate> rotation) {
        String fallback = rotation.stream().findFirst().map(template -> template.directory().getFileName().toString()).orElse(world.getName());
        return new MapBundle(world, rotation, Meta.of(root(world), fallback));
    }

    public String name() {
        return meta.name();
    }

    public List<String> builders() {
        return meta.builders();
    }

    /** The main world's template, which {@link #save()} publishes to. */
    public Optional<WorldTemplate> template() {
        return rotation.stream().findFirst();
    }

    /** Every map the server's game may play, the main world's first; more than one only where a lobby chooses. */
    public List<WorldTemplate> rotation() {
        return rotation;
    }

    /**
     * A plugin's map-owned file, {@code <world>/blockcraft/<Plugin>/<file>}. A file of that name still in the
     * plugin's data folder from before maps were bundles moves in the first time.
     */
    public Path file(Plugin plugin, String fileName) {
        Path target = root(world).resolve(DIRECTORY).resolve(plugin.getName()).resolve(fileName);
        Path legacy = plugin.getDataPath().resolve(fileName);
        try {
            Files.createDirectories(target.getParent());
            if (Files.notExists(target) && Files.isRegularFile(legacy)) {
                Files.move(legacy, target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare " + target, e);
        }
        return target;
    }

    /**
     * Publishes the running world to the template; the caller checks that one is configured. Every world is flushed,
     * because the other dimensions live in the same folder, and the copy runs on the main thread, so no chunk unload
     * writes a region file while it is taken. The template only changes once the copy is complete. Returns what could
     * not be restored after a publish that went through.
     */
    public List<RuntimeException> save() {
        var publishing = template().orElseThrow();
        return MapSaveEvent.publish(world, () -> {
            Bukkit.getWorlds().forEach(dimension -> dimension.save(true));
            publishing.publish(root(world));
        });
    }

    /**
     * The level directory, where level.dat and the dimensions live; since 26.2 {@link World#getWorldFolder()} is
     * the overworld's dimension folder inside it, which is not what a template restores or a bundle owns.
     */
    private static Path root(World world) {
        return Bukkit.getWorldContainer().toPath().resolve(world.getName());
    }

    private static Map<String, Object> loadMeta(Path file) {
        if (Files.notExists(file)) {
            return Map.of();
        }
        try (InputStream in = Files.newInputStream(file)) {
            return YamlFile.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
