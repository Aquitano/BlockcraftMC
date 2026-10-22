package me.blockcraft.mlgrush.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

public record MapPool(List<ArenaMap> maps, String selection) {

    private static final Set<Material> SURFACES = Set.of(Material.SMOOTH_SANDSTONE, Material.STONE_BRICKS,
            Material.QUARTZ_BLOCK, Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.RED_SANDSTONE,
            Material.PRISMARINE_BRICKS, Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE);

    public record Palette(Material bridge, Material redBase, Material blueBase) {
        public Palette {
            if (!SURFACES.contains(bridge) || !SURFACES.contains(redBase) || !SURFACES.contains(blueBase)) {
                throw new IllegalArgumentException("Map materials must be supported full solid blocks: " + SURFACES);
            }
        }
    }

    public record ArenaMap(String key, String name, RushConfig.Dimensions dimensions, Palette palette) { }

    public MapPool {
        maps = List.copyOf(maps);
        if (maps.isEmpty()) {
            throw new IllegalArgumentException("maps.yml: enable at least one map");
        }
        if (!selection.equals("random") && maps.stream().noneMatch(map -> map.key().equals(selection))) {
            throw new IllegalArgumentException("maps.yml: selection must be random or an enabled map key: " + selection);
        }
    }

    public ArenaMap choose(RandomGenerator random) {
        return selection.equals("random") ? maps.get(random.nextInt(maps.size()))
                : maps.stream().filter(map -> map.key().equals(selection)).findFirst().orElseThrow();
    }

    public static MapPool load(Path file, RushConfig.ArenaSettings bounds) {
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalArgumentException("Could not load " + file, e);
        }
        return from(yaml, bounds);
    }

    public static MapPool from(ConfigurationSection yaml, RushConfig.ArenaSettings bounds) {
        var maps = new ArrayList<ArenaMap>();
        var section = yaml.getConfigurationSection("maps");
        if (section == null) {
            throw new IllegalArgumentException("maps.yml: maps must be a section of named maps");
        }
        for (var key : section.getKeys(false)) {
            var map = section.getConfigurationSection(key);
            if (!key.matches("[a-z][a-z0-9_-]{0,31}") || key.equals("random") || map == null) {
                throw new IllegalArgumentException("maps.yml: invalid map key or section: " + key);
            }
            try {
                if (!(map.get("enabled") instanceof Boolean enabled)) {
                    throw new IllegalArgumentException("enabled must be true or false");
                }
                var dimensions = new RushConfig.Dimensions(integer(map, "bridge-length"), integer(map, "bridge-width"),
                        integer(map, "base-radius"), bounds.buildMargin(), bounds.buildHeight(), bounds.floorY(), bounds.voidY());
                var definition = new ArenaMap(key, text(map, "name"), dimensions,
                        new Palette(surface(map, "materials.bridge"), surface(map, "materials.red-base"),
                                surface(map, "materials.blue-base")));
                if (enabled) {
                    maps.add(definition);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("maps.yml: maps." + key + ": " + e.getMessage(), e);
            }
        }
        return new MapPool(maps, text(yaml, "selection"));
    }

    private static String text(ConfigurationSection section, String path) {
        if (section.get(path) instanceof String value && !value.isBlank()) {
            return value;
        }
        throw new IllegalArgumentException(path + " must be a nonempty string");
    }

    private static int integer(ConfigurationSection section, String path) {
        if (section.get(path) instanceof Integer value) {
            return value;
        }
        throw new IllegalArgumentException(path + " must be an integer");
    }

    private static Material surface(ConfigurationSection section, String path) {
        var material = Material.getMaterial(text(section, path));
        if (material == null || !SURFACES.contains(material)) {
            throw new IllegalArgumentException(path + " must be one of " + SURFACES);
        }
        return material;
    }
}
