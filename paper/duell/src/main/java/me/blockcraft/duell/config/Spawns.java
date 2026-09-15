package me.blockcraft.duell.config;

import me.blockcraft.api.config.Locations;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.Location;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.IntStream;

/** The lobby spawn and one spawn per map and participant slot, persisted in the map's {@code locations.yml}. */
public final class Spawns {

    private static final String SPAWN = "spawn";

    private final Locations<String> locations;

    private Spawns(Locations<String> locations) {
        this.locations = locations;
    }

    public static Spawns load(Path file) {
        return new Spawns(Locations.load(file));
    }

    public Optional<Location> spawn() {
        return locations.get(SPAWN);
    }

    public Optional<Location> mapSpawn(DuelMode mode, int map, int player) {
        return locations.get(mapKey(mode, map, player));
    }

    public boolean hasMapSpawns(DuelMode mode, int map, int players) {
        return IntStream.rangeClosed(1, players).allMatch(player -> mapSpawn(mode, map, player).isPresent());
    }

    public void setSpawn(Location location) {
        locations.set(SPAWN, location);
    }

    public void setMapSpawn(DuelMode mode, int map, int player, Location location) {
        locations.set(mapKey(mode, map, player), location);
    }

    private static String mapKey(DuelMode mode, int map, int player) {
        return mode.configKey() + "." + map + ".p" + player;
    }
}
