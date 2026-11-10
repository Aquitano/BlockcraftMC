package me.blockcraft.sternenfront.combat;

import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FlaresTest {

    private static final List<Vector> FACES = List.of(new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(0, 1, 0),
            new Vector(0, -1, 0), new Vector(0, 0, 1), new Vector(0, 0, -1));

    private static Vector3f modelLength(Vector normal, Vector along) {
        return Flares.lying(normal, along, 0).transform(new Vector3f(1, 0, 0));
    }

    @Test
    public void aCutRunsAlongWhatItsDirectionLeavesOnTheFace() {
        var along = new Vector(0.6, 0.7, -0.4);
        for (var normal : FACES) {
            var face = normal.toVector3f();
            var wanted = along.toVector3f();
            wanted.fma(-face.dot(wanted), face).normalize();
            var length = modelLength(normal, along);
            assertEquals(1, length.dot(wanted), 1e-4);
            assertEquals(0, length.dot(face), 1e-4);
        }
    }

    @Test
    public void aDirectionStraightAtTheFaceStillLaysTheCutOnIt() {
        for (var normal : FACES) {
            var length = modelLength(normal, normal.clone().multiply(-1));
            assertTrue(length.isFinite());
            assertEquals(0, length.dot(normal.toVector3f()), 1e-4);
        }
    }
}
