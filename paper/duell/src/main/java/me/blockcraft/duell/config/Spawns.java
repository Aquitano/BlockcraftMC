package me.blockcraft.duell.config;

import me.blockcraft.api.config.Locations;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.Location;
import org.bukkit.World;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The lobby spawn and one spawn per map and participant slot, persisted in {@code locations.yml}. */
public final class Spawns {

    private static final String SPAWN = "spawn";

    private final Locations<String> locations;

    private Spawns(Locations<String> locations) {
        this.locations = locations;
    }

    public static Spawns load(Path dataDirectory) {
        return new Spawns(Locations.load(dataDirectory.resolve("locations.yml")));
    }

    public Optional<Location> spawn() {
        return locations.get(SPAWN);
    }

    public Optional<Location> mapSpawn(DuelMode mode, int map, int player) {
        return locations.get(mapKey(mode, map, player));
    }

    public void setSpawn(Location location) {
        locations.set(SPAWN, location);
    }

    public void setMapSpawn(DuelMode mode, int map, int player, Location location) {
        locations.set(mapKey(mode, map, player), location);
    }

    /** Every world a configured spawn points into. */
    public Set<World> worlds() {
        return locations.all().stream().map(Location::getWorld).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String mapKey(DuelMode mode, int map, int player) {
        return mode.configKey() + "." + map + ".p" + player;
    }
}
