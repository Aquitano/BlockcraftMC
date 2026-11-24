package me.blockcraft.mlgrush.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StandaloneRushGameTest {
    @Test
    public void waitingPlayersAndSpectatorsRecoverAtTheConfiguredThresholdWithinTheirArena() {
        for (var spawn : List.of(new Location(null, -4.5, 65, 0.5), new Location(null, 30.5, 65, 0.5),
                new Location(null, 12.5, 70, 0.5))) {
            for (int threshold : List.of(48, -8)) {
                var calls = new ArrayList<String>();
                StandaloneRushGame.recoverVoid(player(new Location(null, 0, threshold, 0), spawn, calls), spawn, threshold);
                assertEquals(List.of("teleport", "velocity", "fall"), calls);
            }
        }
    }

    @Test
    public void recoveryLeavesPlayersAboveTheThresholdOrInAnotherWorldAlone() {
        var calls = new ArrayList<String>();
        var spawn = new Location(null, 0.5, 65, 0.5);
        StandaloneRushGame.recoverVoid(player(new Location(null, 0, 49, 0), spawn, calls), spawn, 48);
        var otherWorld = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("equals") && proxy == args[0]);
        StandaloneRushGame.recoverVoid(player(new Location(otherWorld, 0, 47, 0), spawn, calls), spawn, 48);
        assertTrue(calls.isEmpty());
    }

    private static Player player(Location location, Location expectedSpawn, List<String> calls) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocation" -> location;
                    case "isDead" -> false;
                    case "teleport" -> {
                        assertEquals(expectedSpawn, args[0]);
                        calls.add("teleport");
                        yield true;
                    }
                    case "setVelocity" -> {
                        assertEquals(new Vector(), args[0]);
                        calls.add("velocity");
                        yield null;
                    }
                    case "setFallDistance" -> {
                        assertEquals(0.0f, (float) args[0], 0);
                        calls.add("fall");
                        yield null;
                    }
                    default -> throw new AssertionError(method.getName());
                });
    }
}
