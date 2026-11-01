package me.blockcraft.api.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class WaitingLobby {

    private static final String NAME = "waiting_lobby";

    private final Path directory;
    private final TemplateDimensions dimensions;
    private final World world;
    private final YamlConfiguration layout;

    private WaitingLobby(Path directory, TemplateDimensions dimensions, World world, YamlConfiguration layout) {
        this.directory = directory;
        this.dimensions = dimensions;
        this.world = world;
        this.layout = layout;
    }

    public static WaitingLobby load(Path directory) {
        var source = directory.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source.resolve("lobby.yml"))) {
            throw new IllegalArgumentException("Missing shared waiting lobby at " + source);
        }
        var layout = YamlConfiguration.loadConfiguration(source.resolve("lobby.yml").toFile());
        var spawn = layout.getConfigurationSection("spawn");
        if (spawn == null || !(spawn.get("x") instanceof Number) || !(spawn.get("y") instanceof Number)
                || !(spawn.get("z") instanceof Number) || !Double.isFinite(spawn.getDouble("x"))
                || !Double.isFinite(spawn.getDouble("y")) || !Double.isFinite(spawn.getDouble("z"))) {
            throw new IllegalArgumentException("Missing shared waiting lobby spawn at " + source);
        }
        WorldTemplate.at(source.resolve("world")).recover();
        if (!Files.isDirectory(source.resolve("world/region"))) {
            throw new IllegalArgumentException("Missing shared waiting lobby world at " + source);
        }
        var dimensions = new TemplateDimensions("blockcraft", Worlds::settle);
        dimensions.copy(source.resolve("world"), NAME);
        var world = dimensions.load(NAME);
        var lobby = new WaitingLobby(source, dimensions, world, layout);
        world.setSpawnLocation(lobby.spawn());
        return lobby;
    }

    public Path directory() {
        return directory;
    }

    public Path layout() {
        return directory.resolve("lobby.yml");
    }

    public World world() {
        return world;
    }

    public Location spawn() {
        return point("spawn").orElseThrow();
    }

    public Optional<Location> point(String key) {
        var value = layout.getConfigurationSection(key);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Point.parse(value.getValues(false)).in(world));
    }

    /** Returns what could not be restored after a publish that went through. */
    public List<RuntimeException> save() {
        return MapSaveEvent.publish(world, () -> dimensions.publish(world, directory.resolve("world")));
    }
}
