package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BotAimTest {

    @Test
    public void turnsGraduallyEvenWhenTheEnemyMovesDirectlyBehind() {
        var start = new Vector(0, 0, 1);
        var turned = BotAim.turn(start, new Vector(0, 0, -1), 12);
        assertEquals(12, Math.toDegrees(start.angle(turned)), 0.001);
        assertEquals(1, turned.length(), 0.001);
        assertTrue(turned.getZ() > 0);
        assertEquals(new Vector(0, 0, 1), BotAim.turn(new Vector(0.01, 0, 1), start, 12));
    }
}
