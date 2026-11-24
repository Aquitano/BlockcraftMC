package me.blockcraft.sternenfront.combat;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DamageDirectionTest {

    @Test
    public void reportsScreenDirectionsAndRotatesWithThePlayersView() {
        var south = new Vector(0, 0, 1);
        assertEquals(DamageDirection.FRONT, DamageDirection.from(south, south).orElseThrow());
        assertEquals(DamageDirection.BACK, DamageDirection.from(south, new Vector(0, 0, -1)).orElseThrow());
        assertEquals(DamageDirection.LEFT, DamageDirection.from(south, new Vector(1, 0, 0)).orElseThrow());
        assertEquals(DamageDirection.RIGHT, DamageDirection.from(south, new Vector(-1, 0, 0)).orElseThrow());
        assertEquals(DamageDirection.FRONT, DamageDirection.from(new Vector(1, 0, 0), new Vector(1, 8, 0)).orElseThrow());
        assertTrue(DamageDirection.from(south, new Vector(0, 8, 0)).isEmpty());
    }
}
