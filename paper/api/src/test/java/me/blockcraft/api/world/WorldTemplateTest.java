package me.blockcraft.api.world;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorldTemplateTest {

    @Test
    public void aSingleDirectoryIsAlwaysPicked() {
        assertEquals("/worlds/a", WorldTemplate.pick("/worlds/a", new Random()));
    }

    @Test
    public void aListPicksOneOfItsDirectories() {
        var picked = WorldTemplate.pick("/worlds/a, /worlds/b,", new Random());
        assertTrue(picked, picked.equals("/worlds/a") || picked.equals("/worlds/b"));
    }
}
