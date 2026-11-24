package me.blockcraft.api.scenery;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.block.data.type.Light;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class FurnitureSolidsTest {
    @Test
    public void sharedLightsKeepTheStrongestOwnerUntilItLeaves() {
        var low = light(7);
        var high = light(12);
        var requested = new java.util.ArrayList<BlockData>(List.of(low, high));
        assertSame(high, FurnitureSolids.effective(requested));
        requested.remove(high);
        assertSame(low, FurnitureSolids.effective(requested));
    }

    private static Light light(int level) {
        return (Light) Proxy.newProxyInstance(Light.class.getClassLoader(), new Class<?>[]{Light.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMaterial" -> Material.LIGHT;
                    case "getLevel" -> level;
                    case "equals" -> proxy == args[0];
                    default -> throw new AssertionError(method.getName());
                });
    }
    @Test
    public void quarterTurnsPreserveTheGridAtNegativeYaw() {
        var point = new Vector(2, 1, 3);
        assertEquals(new Vector(3, 1, -2), FurnitureSolids.rotate(point, -90));
        assertEquals(new Vector(-2, 1, -3), FurnitureSolids.rotate(point, 180));
        assertEquals(new Vector(2, 1, 3), point);
    }

    @Test
    public void aSmallSolidStopsTheRayBeforeTheWall() {
        var wall = new BoundingBox(5, 0, -1, 6, 2, 1);
        var collider = (Entity) Proxy.newProxyInstance(Entity.class.getClassLoader(), new Class<?>[]{Entity.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getBoundingBox")) return new BoundingBox(2, 0, -.25, 2.5, 1, .25);
                    throw new AssertionError(method.getName());
                });
        var world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "rayTraceBlocks" -> wall.rayTrace(((Location) args[0]).toVector(), (Vector) args[1], (double) args[2]);
                    case "getNearbyEntities" -> List.of(collider);
                    case "getBlockAt" -> null;
                    default -> throw new AssertionError(method.getName());
                });
        var hit = FurnitureSolids.rayTrace(new Location(world, 0, .5, 0), new Vector(1, 0, 0), 10);
        assertEquals(new Vector(2, .5, 0), hit.getHitPosition());
        var above = FurnitureSolids.rayTrace(new Location(world, 0, 1.5, 0), new Vector(1, 0, 0), 10);
        assertEquals(new Vector(5, 1.5, 0), above.getHitPosition());
    }
}
