package me.blockcraft.sternenfront.hero;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlastPatternTest {

    @Test
    public void fixesEveryImpactBeforeTheWarningAndKeepsTheFirstOnTheBeacon() {
        var plan = BlastPattern.plan(6, 5, new Random(42));
        assertEquals(6, plan.size());
        assertEquals(new BlastPattern.Impact(0, 0), plan.getFirst());
        assertEquals(plan, BlastPattern.plan(6, 5, new Random(42)));
        assertTrue(plan.stream().allMatch(impact -> Math.abs(impact.x()) <= 5 && Math.abs(impact.z()) <= 5));
    }
}
