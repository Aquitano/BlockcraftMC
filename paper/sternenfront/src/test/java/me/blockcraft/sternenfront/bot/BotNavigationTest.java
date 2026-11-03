package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class BotNavigationTest {

    @Test
    public void startsAtHeadquartersButSkipsRearWaypointsWhenArrivingFromAnotherPost() {
        var objective = new Vector(0, 0, 0);
        var approach = List.of(new Vector(44, 0, 12), new Vector(32, 0, 12), new Vector(20, 0, 4));
        assertEquals(0, BotNavigation.enter(new Vector(82, 0, 0), objective, approach));
        assertEquals(2, BotNavigation.enter(new Vector(0, 0, 30), objective, approach));
        assertEquals(3, BotNavigation.enter(new Vector(3, 0, 2), objective, approach));
    }
}
