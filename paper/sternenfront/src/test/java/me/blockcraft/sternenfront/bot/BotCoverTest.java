package me.blockcraft.sternenfront.bot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotCoverTest {

    @Test
    public void coverMustBlockTheHeadAndTorsoAndPeekingMustClearTheWall() {
        var world = wall(new BoundingBox(0, 0, -1, 1, 2.5, 1));
        var threat = new Location(world, -5, 1.6, 0);
        assertTrue(BotCover.shields(new Location(world, 2, 0, 0), 1.7, threat));
        assertFalse(BotCover.shields(new Location(world, 2, 0, 3), 1.7, threat));
        assertFalse(BotCover.blocked(new Location(world, 2, 1.7, 3), threat));
    }

    @Test
    public void peekArrivalKeepsMovingUntilTheActualEyeClearsTheCorner() {
        var world = wall(new BoundingBox(0, 0, -1, 1, 2.5, 1));
        var threat = new Location(world, -5, 1.6, 0);
        var destination = new Location(world, 2, 0, 1.5);
        var feet = destination.clone().subtract(0, 0, 0.55);
        assertTrue(feet.distanceSquared(destination) < 0.36);
        assertFalse(BotCover.blocked(destination.clone().add(0, 1.7, 0), threat));
        assertTrue(BotCover.blocked(feet.clone().add(0, 1.7, 0), threat));
        assertFalse(BotCover.arrived(feet, feet.clone().add(0, 1.7, 0), destination, threat, true));
        assertTrue(BotCover.arrived(destination, destination.clone().add(0, 1.7, 0), destination, threat, true));
        assertFalse(BotCover.arrived(destination, destination.clone().add(0, 1.7, 0), destination, threat, false));
    }

    @Test
    public void peekSelectionClearsTheWallBeforePaperCompletesTheFinalPathNode() {
        var world = wall(new BoundingBox(0, 0, -1, 1, 2.5, 1));
        var threat = new Location(world, -5, 1.6, 0);
        var close = new Location(world, 1.5, 0, 1.5);
        assertFalse(BotCover.blocked(close.clone().add(0, 1.7, 0), threat));
        assertTrue(BotCover.blocked(close.clone().add(0.44, 1.7, -0.44), threat));
        assertFalse(BotCover.clearPeek(close, 1.7, threat));
        var clear = new Location(world, 1.5, 0, 2.5);
        assertTrue(BotCover.clearPeek(clear, 1.7, threat));
        assertFalse(BotCover.blocked(clear.clone().add(0.44, 1.7, -0.44), threat));
    }

    @Test
    public void lowBarricadeIsNotCoverForAStandingSoldier() {
        var world = wall(new BoundingBox(0, 0, -1, 1, 1.3, 1));
        var threat = new Location(world, -5, 1.6, 0);
        var feet = new Location(world, 2, 0, 0);
        assertTrue(BotCover.blocked(threat, feet.clone().add(0, 0.9, 0)));
        assertFalse(BotCover.shields(feet, 1.7, threat));
    }

    private static World wall(BoundingBox wall) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> {
            if (method.getName().equals("rayTraceBlocks")) {
                return wall.rayTrace(((Location) args[0]).toVector(), (Vector) args[1], (double) args[2]);
            }
            throw new AssertionError(method.getName());
        });
    }
}
