package me.blockcraft.sternenfront.combat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SaberSweepTest {

    @Test
    public void animationHitsEveryEnemyOnceAcrossFramesEvenWhenSeveralRaysCrossThem() {
        var origin = new Location(null, 0, 1.3, 0);
        var sweep = new SaberSweep(origin, Color.BLUE, 10, 3.5, 120, 4, 20, false);
        var targets = new HashMap<UUID, BoundingBox>();
        for (double x : new double[] {-2.4, -0.8, 0.8, 2.4}) {
            targets.put(UUID.randomUUID(), new BoundingBox(x - 0.3, 0, 1.5, x + 0.3, 2, 2.1));
        }
        var hits = new HashMap<UUID, Integer>();
        for (long tick = 20; tick < 24; tick++) {
            assertFalse(sweep.finished());
            for (var ray : sweep.advance(tick)) {
                for (var target : targets.entrySet()) {
                    if (CombatArc.hits(origin.toVector(), ray, target.getValue(), 3.5) && sweep.contact(target.getKey())) {
                        hits.merge(target.getKey(), 1, Integer::sum);
                    }
                }
            }
            assertTrue(sweep.advance(tick).isEmpty());
        }
        assertTrue(sweep.finished());
        assertTrue(sweep.advance(24).isEmpty());
        assertEquals(4, hits.size());
        hits.values().forEach(count -> assertEquals(1, count.intValue()));
    }

    @Test
    public void trailConnectsFramesWithoutAddingAnotherDamageRay() {
        var sweep = new SaberSweep(new Location(null, 0, 1.3, 0), Color.BLUE, 10, 3.5, 120, 4, 20, false);
        assertTrue(sweep.trailStart().isEmpty());
        var first = sweep.advance(20);
        assertEquals(first.getLast(), sweep.trailStart().orElseThrow());
        var second = sweep.advance(21);
        assertFalse(second.contains(first.getLast()));
        assertEquals(second.getLast(), sweep.trailStart().orElseThrow());
    }

    @Test
    public void sweepCannotOutlastTheSwingCooldown() {
        var config = new MemoryConfiguration();
        config.set("combat.melee.swing-ticks", 2);
        config.set("combat.melee.sweep-ticks", 8);
        assertEquals(2, CombatConfig.from(config).sweepTicks());
    }

    @Test
    public void theStanceStartsOnTheSideTheFirstRayCuts() {
        for (boolean reverse : new boolean[] {false, true}) {
            var origin = new Location(null, 0, 1.3, 0, 0, 0);
            var sweep = new SaberSweep(origin, Color.BLUE, 10, 3.5, 120, 4, 20, reverse);
            boolean firstRayLeft = sweep.advance(20).getFirst().getX() > 0;
            assertEquals(firstRayLeft, sweep.stance(20).fromLeft());
            assertEquals(0, sweep.stance(20).elapsed());
            assertEquals(4, sweep.stance(30).elapsed());
        }
    }
}
