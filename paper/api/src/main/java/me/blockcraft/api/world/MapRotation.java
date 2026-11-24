package me.blockcraft.api.world;

import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The maps of a game whose server names several level templates. The main world is the first of them, which
 * {@code /setup} and {@code /map save} edit; a round plays a fresh copy of the chosen map's overworld as the world
 * {@code <namespace>:<key>}, with the map's {@code blockcraft/} bundle in its folder. With one template there is
 * nothing to choose and the round plays the main world.
 */
public final class MapRotation {

    /** {@code key} is the name of the template's directory, {@code name} and {@code builders} come from its map.yml. */
    public record Entry(String key, String name, List<String> builders, WorldTemplate template) {

        public Entry {
            builders = List.copyOf(builders);
        }
    }

    private final List<Entry> maps;
    private final TemplateDimensions dimensions;

    public MapRotation(List<WorldTemplate> templates, String namespace, Consumer<World> settle) {
        this.maps = templates.size() > 1 ? templates.stream().map(MapRotation::entry).toList() : List.of();
        this.dimensions = new TemplateDimensions(namespace, settle);
    }

    private static Entry entry(WorldTemplate template) {
        var key = template.directory().getFileName().toString();
        if (!TemplateDimensions.isValidName(key)) {
            throw new IllegalArgumentException("A rotated map's folder may only hold a-z, 0-9, _, . and -: " + template.directory());
        }
        var meta = MapBundle.Meta.of(template.directory(), key);
        return new Entry(key, meta.name(), meta.builders(), template);
    }

    public List<Entry> maps() {
        return maps;
    }

    public boolean rotates() {
        return !maps.isEmpty();
    }

    public Optional<Entry> map(String key) {
        return maps.stream().filter(map -> map.key().equals(key)).findFirst();
    }

    /** Blocking file work before {@link #load}: replaces the map's world with a fresh copy of its template. */
    public void copy(Entry map) {
        dimensions.copyLevel(map.template(), map.key());
    }

    /** On the main thread, after {@link #copy}. */
    public World load(Entry map) {
        return dimensions.load(map.key());
    }

    /** A plugin's map-owned file in a copied map, as {@link MapBundle#file} names it in the main world. */
    public Path file(Entry map, Plugin plugin, String fileName) {
        return dimensions.folder(map.key()).resolve("blockcraft").resolve(plugin.getName()).resolve(fileName);
    }
}
