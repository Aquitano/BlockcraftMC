package me.blockcraft.sternenfront.bot;

import me.blockcraft.sternenfront.bot.Fireteam.Place;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FireteamTest {

    private static final Vector POST = new Vector(0, 65, 0);
    private static final Vector LEADER = new Vector(40, 65, 0);

    private static Place place(double x, double z) {
        return Fireteam.place(LEADER, new Vector(x, 65, z), POST, 3, 12, 32);
    }

    @Test
    public void onlyAMemberBehindTheLeaderHoldsTheTeamUp() {
        assertEquals(Place.IN_COLUMN, place(46, 0));
        assertEquals(Place.STRAGGLING, place(60, 0));
        assertEquals(Place.AHEAD, place(20, 0));
        assertEquals(Place.AWAY, place(5, 0));
    }

    @Test
    public void aMemberKeepsTheLeadersPaceInItsPlaceAndRunsOnlyWhenFarBehind() {
        assertEquals(0.9, Fireteam.followPace(1, 3, 0.9, 1.25), 1e-9);
        assertEquals(0.9, Fireteam.followPace(3, 3, 0.9, 1.25), 1e-9);
        assertEquals(1.075, Fireteam.followPace(6, 3, 0.9, 1.25), 1e-9);
        assertEquals(1.25, Fireteam.followPace(20, 3, 0.9, 1.25), 1e-9);
    }

}
