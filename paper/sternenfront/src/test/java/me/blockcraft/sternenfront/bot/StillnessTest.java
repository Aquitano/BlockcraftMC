package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StillnessTest {

    @Test
    public void shufflingAboutOneSpotCountsAsStandingButAStepAwayStartsAfresh() {
        var stillness = new Stillness(Stillness.REACH);
        assertEquals(0, stillness.update(new Vector(0, 65, 0), 100));
        assertEquals(100, stillness.update(new Vector(1, 65, 1), 200));
        assertEquals(200, stillness.update(new Vector(-0.5, 65, 0.5), 300));
        assertEquals(0, stillness.update(new Vector(2, 65, 0), 320));
        assertEquals(20, stillness.update(new Vector(2, 65, 0), 340));
        stillness.clear();
        assertEquals(0, stillness.update(new Vector(2, 65, 0), 360));
    }

    @Test
    public void aPlayerPushedAboutWithinAFewBlocksStillCountsAsIdle() {
        var player = new Stillness(3);
        assertEquals(0, player.update(new Vector(0, 65, 0), 0));
        assertEquals(200, player.update(new Vector(2.5, 65, 0), 200));
        assertEquals(0, player.update(new Vector(4, 65, 0), 220));
    }
}
