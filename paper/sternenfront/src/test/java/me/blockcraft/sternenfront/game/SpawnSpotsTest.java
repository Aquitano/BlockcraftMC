package me.blockcraft.sternenfront.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class SpawnSpotsTest {

    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
            (proxy, method, args) -> method.getName().equals("equals") ? proxy == args[0] : null);

    @Test
    public void campersBlockDeploymentOnlyWhenEverySpawnIsWithinEnemyReach() {
        var spawns = List.of(new Location(world, -20, 64, 0), new Location(world, 20, 64, 0));
        var west = new Location(world, -20, 64, 1);
        var east = new Location(world, 20, 64, 1);
        assertTrue(SpawnSpots.hasClearSpawn(spawns, List.of(), 12));
        assertTrue(SpawnSpots.hasClearSpawn(spawns, List.of(west), 12));
        assertFalse(SpawnSpots.hasClearSpawn(spawns, List.of(west, east), 12));
    }

    @Test
    public void aDeployerLandsAwayFromNearEnemiesThenFromOthersThenOnAnyFreeSpot() {
        var west = new Location(world, -10, 64, 0);
        var east = new Location(world, 10, 64, 0);
        var spots = List.of(west, east);
        var random = new Random(1);
        assertEquals(east, SpawnSpots.safest(spots, List.of(new Location(world, -20, 64, 0)), List.of(), random));
        assertEquals(west, SpawnSpots.safest(spots, List.of(new Location(world, 200, 64, 0)),
                List.of(new Location(world, 10, 64, 1)), random));
        var picked = IntStream.range(0, 20).mapToObj(ignored -> SpawnSpots.safest(spots, List.of(), List.of(), random)).toList();
        assertTrue(picked.stream().anyMatch(west::equals));
        assertTrue(picked.stream().anyMatch(east::equals));
    }
}
