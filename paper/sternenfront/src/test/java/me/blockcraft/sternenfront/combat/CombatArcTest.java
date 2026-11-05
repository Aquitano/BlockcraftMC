package me.blockcraft.sternenfront.combat;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatArcTest {

    @Test
    public void sweepCatchesBothSidesButNotBehindOrBeyondReach() {
        var origin = new Vector(0, 1.3, 0);
        var rays = CombatArc.rays(new Vector(0, 0, 1), new Vector(1, 0, 0), 3.5, 120, false);
        for (double x : new double[] {-2.4, 0, 2.4}) {
            var target = new BoundingBox(x - 0.3, 0, 1.5, x + 0.3, 2, 2.1);
            assertTrue(rays.stream().anyMatch(ray -> CombatArc.hits(origin, ray, target, 3.5)));
        }
        for (var target : new BoundingBox[] {new BoundingBox(-0.3, 0, -2, 0.3, 2, -1.4),
                new BoundingBox(-0.3, 0, 4, 0.3, 2, 4.6), new BoundingBox(2, 0, -0.3, 2.6, 2, 0.3)}) {
            assertFalse(rays.stream().anyMatch(ray -> CombatArc.hits(origin, ray, target, 3.5)));
        }
    }

    @Test
    public void wallClippedRayOnlyHitsBodiesBeforeTheWall() {
        var origin = new Vector(0, 1.3, 0);
        var direction = new Vector(0, 0, 1);
        assertTrue(CombatArc.hits(origin, direction, new BoundingBox(-0.3, 0, 1, 0.3, 2, 1.6), 2));
        assertFalse(CombatArc.hits(origin, direction, new BoundingBox(-0.3, 0, 2.1, 0.3, 2, 2.7), 2));
        assertFalse(CombatArc.hits(origin, direction, new BoundingBox(-0.3, 0, 0, 0.3, 2, 0.6), 0));
    }

    @Test
    public void verticalAimKeepsTheFullArcAndReverseUsesTheSamePath() {
        var facing = new Vector(0, 1, 0);
        var side = new Vector(1, 0, 0);
        var rays = CombatArc.rays(facing, side, 3.5, 120, false);
        var reverse = CombatArc.rays(facing, side, 3.5, 120, true);
        for (int i = 0; i < rays.size(); i++) {
            assertEquals(1, rays.get(i).length(), 1e-9);
            assertEquals(0, rays.get(i).distance(reverse.get(rays.size() - 1 - i)), 1e-9);
        }
        assertEquals(Math.sqrt(3), rays.getFirst().distance(rays.getLast()), 1e-9);
        var overhead = new BoundingBox(-0.3, 2, -0.3, 0.3, 3, 0.3);
        assertTrue(rays.stream().anyMatch(ray -> CombatArc.hits(new Vector(), ray, overhead, 3.5)));
    }
}
