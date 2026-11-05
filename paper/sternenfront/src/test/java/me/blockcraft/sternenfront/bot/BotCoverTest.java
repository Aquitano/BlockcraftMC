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
