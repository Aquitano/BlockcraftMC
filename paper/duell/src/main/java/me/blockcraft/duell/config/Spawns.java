package me.blockcraft.duell.config;

import me.blockcraft.api.config.Locations;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.Location;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

/** The lobby spawn and one spawn per map and participant slot, persisted in the map's {@code locations.yml}. */
public final class Spawns {

    private static final String SPAWN = "spawn";
    /** What /setup calls the lobby spot in every game; the stored key stays {@link #SPAWN}. */
    private static final String LOBBY = "lobby";

    /** A spawn {@code /setup} puts, by the name staff type and the key it is kept under. */
    public record Place(String name, String key) {
    }

    private final Locations<String> locations;

    private Spawns(Locations<String> locations) {
        this.locations = locations;
    }

    public static Spawns load(Path file) {
        return new Spawns(Locations.load(file));
    }

    /**
     * Every spawn {@code /setup} offers: {@code spawn} for the lobby, and {@code <1vs1|sg|sj><map>p<slot>} for each
     * of the {@code slots} on each configured map, e.g. {@code sg2p1}.
     */
    public static List<Place> places(DuellConfig config, int slots) {
        var places = new ArrayList<Place>();
        places.add(new Place(LOBBY, SPAWN));
        for (var mode : DuelMode.values()) {
            for (int map = 1; map <= config.maps(mode).size(); map++) {
                for (int slot = 1; slot <= slots; slot++) {
                    places.add(new Place((mode.setupKey() + map + "p" + slot).toLowerCase(Locale.ROOT), mapKey(mode, map, slot)));
                }
            }
        }
        return places;
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

    public void set(Place place, Location location) {
        locations.set(place.key(), location);
    }

    private static String mapKey(DuelMode mode, int map, int player) {
        return mode.configKey() + "." + map + ".p" + player;
    }
}
