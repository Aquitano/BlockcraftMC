package me.blockcraft.rush.config;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.world.WaitingLobby;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** The map's spawn points, one per {@link SpawnPoint}, persisted in the map's {@code locations.yml}. */
public final class Spawns {

    private final Locations<SpawnPoint> locations;
    private final Optional<WaitingLobby> lobby;

    private Spawns(Locations<SpawnPoint> locations, Optional<WaitingLobby> lobby) {
        this.locations = locations;
        this.lobby = lobby;
    }

    public static Spawns load(Path file) {
        return load(file, Optional.empty());
    }

    public static Spawns load(Path file, Optional<WaitingLobby> lobby) {
        return new Spawns(Locations.load(file, SpawnPoint::key), lobby);
    }

    public Optional<Location> get(SpawnPoint point) {
        return point == SpawnPoint.LOBBY && lobby.isPresent() ? lobby.map(WaitingLobby::spawn) : locations.get(point);
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
