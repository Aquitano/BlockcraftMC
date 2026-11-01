package me.blockcraft.lobby.activity;

import me.blockcraft.lobby.config.PropsConfig.BlockLook;
import me.blockcraft.lobby.config.PropsConfig.Part;
import org.bukkit.Material;
import org.joml.Vector3f;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropsTest {

    private static final float EPSILON = 1e-4f;
    private static final Vector3f UP = new Vector3f(0, 1, 0);
    private static final Vector3f SPOT = new Vector3f();

    @Test
    public void aTurningBlockStaysCentredOnItsPartOfTheCircle() {
        var part = new Part(new BlockLook(Material.GOLD_BLOCK), new Vector3f(1, 0.5f, 0), new Vector3f(0.4f), 0, 0, 0, false);

        var pose = Props.pose(part, UP, SPOT, (float) (Math.PI / 2), 0.25f);

        var centre = new Vector3f(0.2f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        assertEquals(0, centre.x, EPSILON);
        assertEquals(0.75f, centre.y, EPSILON);
        assertEquals(-1, centre.z, EPSILON);
    }

    @Test
    public void aRolledBlockTiltsRoundItsOwnCentre() {
        var part = new Part(new BlockLook(Material.STONE), new Vector3f(0, 1, 0), new Vector3f(0.2f, 1, 0.2f), 0, 0, 90, false);

        var pose = Props.pose(part, UP, SPOT, 0, 0);

        var centre = new Vector3f(0.1f, 0.5f, 0.1f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        var top = new Vector3f(0.1f, 1, 0.1f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        assertEquals(0, centre.distance(0, 1, 0), EPSILON);
        assertEquals(0, top.distance(-0.5f, 1, 0), EPSILON);
    }

    @Test
    public void aStillPartNeitherTurnsNorBobs() {
        var part = new Part(new BlockLook(Material.STONE), new Vector3f(0, 0.3f, 0), new Vector3f(1.6f, 0.6f, 1.6f), 0, 0, 0, true);

        var pose = Props.pose(part, UP, SPOT, 2f, 0.5f);

        assertEquals(new Vector3f(-0.8f, 0, -0.8f), pose.getTranslation());
    }

    @Test
    public void aSailTurnsRoundAHorizontalAxisWithItsArm() {
        var sail = new Part(new BlockLook(Material.WHITE_WOOL), new Vector3f(0, 3, 0), new Vector3f(0.1f, 4, 1), 0, 0, 0, false);

        var pose = Props.pose(sail, new Vector3f(1, 0, 0), SPOT, (float) (Math.PI / 2), 0);

        var centre = new Vector3f(0.05f, 2, 0.5f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        var tip = new Vector3f(0.05f, 4, 0.5f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        assertEquals(0, centre.distance(0, 0, 3), EPSILON);
        assertEquals(0, tip.distance(0, 0, 5), EPSILON);
    }

    @Test
    public void aHubOnThePivotStaysWhereItIsWhileTheSailsTurn() {
        var hub = new Part(new BlockLook(Material.DARK_OAK_LOG), new Vector3f(0, 0.5f, 0), new Vector3f(1), 0, 0, 0, false);

        var pose = Props.pose(hub, new Vector3f(1, 0, 0), new Vector3f(0, 0.5f, 0), (float) Math.PI, 0);

        var centre = new Vector3f(0.5f).rotate(pose.getLeftRotation()).add(pose.getTranslation());
        assertEquals(0, centre.distance(0, 0.5f, 0), EPSILON);
    }
}
