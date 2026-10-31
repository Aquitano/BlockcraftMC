package me.blockcraft.rush.config;

import me.blockcraft.api.config.Locations;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** The map's spawn points, one per {@link SpawnPoint}, persisted in the map's {@code locations.yml}. */
public final class Spawns {

    private final Locations<SpawnPoint> locations;

    private Spawns(Locations<SpawnPoint> locations) {
        this.locations = locations;
    }

    public static Spawns load(Path file) {
        return new Spawns(Locations.load(file, SpawnPoint::key));
    }

    public Optional<Location> get(SpawnPoint point) {
        return locations.get(point);
    }

    public void set(SpawnPoint point, Location location) {
        locations.set(point, location);
    }

    public void teleport(Player player, SpawnPoint point) {
        get(point).ifPresent(player::teleport);
    }

    public SpawnPoint deathmatch(int index) {
        return SpawnPoint.DEATHMATCH.get(Math.floorMod(index, SpawnPoint.DEATHMATCH.size()));
    }

    public SpawnPoint randomDeathmatch() {
        return deathmatch(ThreadLocalRandom.current().nextInt(SpawnPoint.DEATHMATCH.size()));
    }
}
