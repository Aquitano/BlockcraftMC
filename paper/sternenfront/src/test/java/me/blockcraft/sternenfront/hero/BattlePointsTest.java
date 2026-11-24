package me.blockcraft.sternenfront.hero;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BattlePointsTest {

    @Test
    public void pointsAddUpAndAreSpentOnlyWhenThereAreEnough() {
        var points = new BattlePoints();
        var player = UUID.randomUUID();

        points.earn(player, 100);
        points.earn(player, 50);
        assertFalse(points.spend(player, 200));
        assertEquals(150, points.of(player));

        points.earn(player, 100);
        assertTrue(points.spend(player, 200));
        assertEquals(50, points.of(player));
    }
}
