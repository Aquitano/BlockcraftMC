package me.blockcraft.endergames.world;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class RoundWorldTest {

    @Test
    public void theCentreSearchVisitsEveryCellOfTheGridOnceRingByRing() {
        var seen = new HashSet<List<Integer>>();
        for (int attempt = 0; attempt < 25; attempt++) {
            int x = RoundWorld.spiral(attempt, true);
            int z = RoundWorld.spiral(attempt, false);
            int ring = attempt == 0 ? 0 : attempt < 9 ? 1 : 2;
            assertEquals("attempt " + attempt, ring, Math.max(Math.abs(x), Math.abs(z)));
            seen.add(List.of(x, z));
        }
        assertEquals(25, seen.size());
    }
}
