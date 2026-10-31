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

    private final World world;
    private final Optional<WorldTemplate> template;
    private final String name;
    private final List<String> builders;

    private MapBundle(World world, Optional<WorldTemplate> template, String name, List<String> builders) {
        this.world = world;
        this.template = template;
        this.name = name;
        this.builders = builders;
    }

    public static MapBundle load(World world, Optional<WorldTemplate> template) {
        var meta = loadMeta(root(world).resolve(DIRECTORY).resolve(META));
        String name = meta.get("name") instanceof String value && !value.isBlank()
                ? value
                : template.map(configured -> configured.directory().getFileName().toString()).orElse(world.getName());
        List<String> builders = meta.get("builders") instanceof List<?> values ? values.stream().map(String::valueOf).toList() : List.of();
        return new MapBundle(world, template, name, builders);
    }

    public String name() {
        return name;
    }

    public List<String> builders() {
        return builders;
    }

    public Optional<WorldTemplate> template() {
        return template;
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
     * writes a region file while it is taken. The template only changes once the copy is complete.
     */
    public void save() {
        var publishing = template.orElseThrow();
        var event = new MapSaveEvent();
        try {
            Bukkit.getPluginManager().callEvent(event);
            Bukkit.getWorlds().forEach(dimension -> dimension.save(true));
            publishing.publish(root(world));
        } finally {
            event.complete();
        }
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
