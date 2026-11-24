package me.blockcraft.rush.config;

import me.blockcraft.api.config.Locations;
import me.blockcraft.api.world.WaitingLobby;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * One round's spawn points, one per {@link SpawnPoint}, set in the main world and persisted in the map's
 * {@code locations.yml}. They lie in the main world until the round plays a copy of it.
 */
public final class Spawns {

    private final Locations<SpawnPoint> locations;
    private final Optional<WaitingLobby> lobby;
    private Function<SpawnPoint, Optional<Location>> arena;

    public Spawns(Locations<SpawnPoint> locations, Optional<WaitingLobby> lobby) {
        this.locations = locations;
        this.lobby = lobby;
        this.arena = locations::get;
    }

    /** Every point but the lobby now lies in {@code world}, a fresh copy of the main world. */
    public void playIn(World world) {
        arena = locations.in(world);
    }

    public Optional<Location> get(SpawnPoint point) {
        if (point == SpawnPoint.LOBBY) {
            return lobby.isPresent() ? lobby.map(WaitingLobby::spawn) : locations.get(point);
        }
        return arena.apply(point);
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
