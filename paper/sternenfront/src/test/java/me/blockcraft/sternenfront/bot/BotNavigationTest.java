package me.blockcraft.sternenfront.bot;

import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotNavigationTest {

    @Test
    public void startsAtHeadquartersButSkipsRearWaypointsWhenArrivingFromAnotherPost() {
        var objective = new Vector(0, 0, 0);
        var approach = List.of(new Vector(44, 0, 12), new Vector(32, 0, 12), new Vector(20, 0, 4));
        assertEquals(0, BotNavigation.enter(new Vector(82, 0, 0), objective, approach, point -> true));
        assertEquals(2, BotNavigation.enter(new Vector(0, 0, 30), objective, approach, point -> true));
        assertEquals(3, BotNavigation.enter(new Vector(3, 0, 2), objective, approach, point -> true));
    }

    @Test
    public void preservesOutwardDetoursAndStairsToRoomsAboveTheBot() {
        assertEquals(0, BotNavigation.enter(new Vector(5, 65, 0), new Vector(0, 65, 0),
                List.of(new Vector(8, 65, 0), new Vector(8, 65, 8), new Vector(0, 65, 8)), point -> true));
        assertEquals(0, BotNavigation.enter(new Vector(0, 65, 0), new Vector(0, 75, 0),
                List.of(new Vector(0, 65, 4), new Vector(8, 65, 4), new Vector(8, 75, 4), new Vector(0, 75, 4)), point -> true));
    }

    @Test
    public void joinsAnAuthoredSegmentAheadWithoutWalkingBackToItsNearestWaypoint() {
        assertEquals(1, BotNavigation.enter(new Vector(41, 65, 12), new Vector(0, 65, 0),
                List.of(new Vector(44, 65, 12), new Vector(32, 65, 12), new Vector(20, 65, 4)), point -> true));
    }

    @Test
    public void keepsTheStairEntranceWhenAnUpperOrBlockedSegmentIsCloser() {
        assertEquals(0, BotNavigation.enter(new Vector(0, 65, 0), new Vector(20, 69, 0),
                List.of(new Vector(5, 65, 0), new Vector(5, 69, 0), new Vector(-5, 69, 0)), point -> true));
        assertEquals(0, BotNavigation.enter(new Vector(41, 65, 12), new Vector(0, 65, 0),
                List.of(new Vector(44, 65, 12), new Vector(32, 65, 12), new Vector(20, 65, 4)), point -> false));
    }

    @Test
    public void arrivingFromAFarPostSkipsTheRearWaypointsWithoutAPathProbe() {
        var approach = List.of(new Vector(44.5, 65, 34.5), new Vector(30.5, 65, 34.5), new Vector(9.5, 65, 40.5));
        assertEquals(2, BotNavigation.enter(new Vector(0.5, 65, -43.5), new Vector(0.5, 65, 44.5), approach, point -> false));
    }

    @Test
    public void triesOneAlternateAuthoredRouteBeforeAbandoningAndResetsForANewObjective() {
        var navigation = new BotNavigation();
        navigation.start(5, 3);
        assertEquals(2, navigation.route());
        assertTrue(navigation.retry(3));
        assertEquals(0, navigation.route());
        assertFalse(navigation.retry(3));
        navigation.start(4, 2);
        assertEquals(0, navigation.route());
        assertTrue(navigation.retry(2));
        assertEquals(1, navigation.route());
        assertFalse(navigation.retry(2));
        navigation.start(1, 1);
        assertFalse(navigation.retry(1));
    }
}
