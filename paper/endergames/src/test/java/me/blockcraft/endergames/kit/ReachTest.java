package me.blockcraft.endergames.kit;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class ReachTest {

    private static List<Integer> line(int at) {
        return List.of(at - 1, at + 1);
    }

    @Test
    public void spreadsOnlyThroughIncludedNeighboursNearestFirst() {
        var logs = Set.of(1, 2, 3, 5);

        assertEquals(List.of(1, 2, 3), Reach.from(0, ReachTest::line, logs::contains, 10));
    }

    @Test
    public void stopsAtTheLimit() {
        assertEquals(List.of(-1, 1), Reach.from(0, ReachTest::line, at -> true, 2));
    }
}
