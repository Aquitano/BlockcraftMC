package me.blockcraft.sternenfront.combat;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatArcTest {

    @Test
    public void catchesAnOffCentreBodyButNotAnEnemyBehindOrBeyondReach() {
        var eye = new Vector(0, 1.6, 0);
        var facing = new Vector(0, 0, 1);
        assertTrue(CombatArc.hits(eye, facing, new BoundingBox(0.8, 0, 2, 1.4, 2, 2.6), 3.5, 85));
        assertFalse(CombatArc.hits(eye, facing, new BoundingBox(-0.3, 0, -2, 0.3, 2, -1.4), 3.5, 85));
        assertFalse(CombatArc.hits(eye, facing, new BoundingBox(-0.3, 0, 4, 0.3, 2, 4.6), 3.5, 85));
    }
}
