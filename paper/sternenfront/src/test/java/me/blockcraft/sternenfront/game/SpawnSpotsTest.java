package me.blockcraft.sternenfront.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.Test;

import java.util.List;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class SpawnSpotsTest {

    @Test
    public void campersBlockDeploymentOnlyWhenEverySpawnIsWithinEnemyReach() {
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("equals") ? proxy == args[0] : null);
        var spawns = List.of(new Location(world, -20, 64, 0), new Location(world, 20, 64, 0));
        var west = new Location(world, -20, 64, 1);
        var east = new Location(world, 20, 64, 1);
        assertTrue(SpawnSpots.hasClearSpawn(spawns, List.of(), 12));
        assertTrue(SpawnSpots.hasClearSpawn(spawns, List.of(west), 12));
        assertFalse(SpawnSpots.hasClearSpawn(spawns, List.of(west, east), 12));
    }
}
