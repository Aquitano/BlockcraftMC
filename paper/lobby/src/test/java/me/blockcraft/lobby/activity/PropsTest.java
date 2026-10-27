package me.blockcraft.lobby.activity;

import me.blockcraft.lobby.config.PropsConfig.BlockLook;
import me.blockcraft.lobby.config.PropsConfig.Part;
import org.bukkit.Material;
import org.joml.Vector3f;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropsTest {

    private static final float EPSILON = 1e-4f;

    @Test
    public void aTurningBlockStaysCentredOnItsPartOfTheCircle() {
        var part = new Part(new BlockLook(Material.GOLD_BLOCK), new Vector3f(1, 0.5f, 0), new Vector3f(0.4f), 0, 0, false);

        var pose = Props.pose(part, (float) (Math.PI / 2), 0.25f);

        var centre = new Vector3f(0.2f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        assertEquals(0, centre.x, EPSILON);
        assertEquals(0.75f, centre.y, EPSILON);
        assertEquals(-1, centre.z, EPSILON);
    }

    @Test
    public void aRolledBlockTiltsRoundItsOwnCentre() {
        var part = new Part(new BlockLook(Material.STONE), new Vector3f(0, 1, 0), new Vector3f(0.2f, 1, 0.2f), 0, 90, false);

        var pose = Props.pose(part, 0, 0);

        var centre = new Vector3f(0.1f, 0.5f, 0.1f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        var top = new Vector3f(0.1f, 1, 0.1f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        assertEquals(0, centre.distance(0, 1, 0), EPSILON);
        assertEquals(0, top.distance(-0.5f, 1, 0), EPSILON);
    }

    @Test
    public void aStillPartNeitherTurnsNorBobs() {
        var part = new Part(new BlockLook(Material.STONE), new Vector3f(0, 0.3f, 0), new Vector3f(1.6f, 0.6f, 1.6f), 0, 0, true);

        var pose = Props.pose(part, 2f, 0.5f);

        assertEquals(new Vector3f(-0.8f, 0, -0.8f), pose.getTranslation());
    }
}
