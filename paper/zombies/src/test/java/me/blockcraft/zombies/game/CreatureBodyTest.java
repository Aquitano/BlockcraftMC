package me.blockcraft.zombies.game;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.*;

public class CreatureBodyTest {

    private static final CreatureBody BODY = new CreatureBody("test", 1, 2, 4, 1, 1);
    private static final Vector FEET = new Vector(10, 64, 10);
    private static final float SOUTH = 0;
    private static final float EAST = -90;

    @Test
    public void theBoxRunsAlongTheWayTheCreatureFacesAndGrowsWithItsScale() {
        var south = BODY.bounds(FEET, SOUTH, 1);
        assertEquals(1, south.getWidthX(), 1e-9);
        assertEquals(4, south.getWidthZ(), 1e-9);
        assertEquals(2, south.getHeight(), 1e-9);
        var east = BODY.bounds(FEET, EAST, 0.5);
        assertEquals(2, east.getWidthX(), 1e-9);
        assertEquals(0.5, east.getWidthZ(), 1e-9);
        assertEquals(64, east.getMinY(), 1e-9);
    }

    @Test
    public void onlyTheFrontOfTheBodyIsItsHead() {
        assertTrue(BODY.isHead(FEET, SOUTH, 1, new Vector(10, 65, 11.5)));
        assertFalse(BODY.isHead(FEET, SOUTH, 1, new Vector(10, 65, 10.5)));
        assertFalse(BODY.isHead(FEET, SOUTH, 1, new Vector(10, 65, 8.5)));
        assertTrue(BODY.isHead(FEET, EAST, 1, new Vector(11.5, 65, 10)));
    }
}
