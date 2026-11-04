package me.blockcraft.duell.arena;

import me.blockcraft.duell.config.SumoConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SumoArenaTest {

    @Test
    public void generatesOneSymmetricCircularFloorAtTheConfiguredHeight() {
        var config = new SumoConfig(4, 64, 61, 2, 3, 3);
        var blocks = new HashSet<>(SumoArena.floorBlocks(config));

        assertEquals(49, blocks.size());
        assertTrue(blocks.contains(new SumoArena.Position(0, 64, 0)));
        assertTrue(blocks.contains(new SumoArena.Position(4, 64, 0)));
        assertFalse(blocks.contains(new SumoArena.Position(4, 64, 1)));
        for (var block : blocks) {
            assertEquals(64, block.y());
            assertTrue(block.x() * block.x() + block.z() * block.z() <= 16);
            assertTrue(blocks.contains(new SumoArena.Position(-block.x(), block.y(), -block.z())));
        }
    }

    @Test
    public void changingRadiusAndHeightRemovesOldFloorAndClearsSupportedSpawns() {
        var previous = new SumoConfig(16, 200, 197, 15, 3, 3);
        var next = new SumoConfig(2, 199, 196, 1, 3, 3);
        var world = new HashMap<SumoArena.Position, Material>();
        for (var position : SumoArena.floorBlocks(previous)) {
            world.put(position, Material.STONE);
        }
        world.put(new SumoArena.Position(0, 0, 0), Material.STONE);
        world.put(new SumoArena.Position(0, 202, 0), Material.STONE);
        for (int slot = 1; slot <= 2; slot++) {
            var spawn = SumoArena.spawn(next, null, slot);
            world.put(new SumoArena.Position(spawn.getBlockX(), spawn.getBlockY() + 1, spawn.getBlockZ()), Material.STONE);
        }

        SumoArena.generate(next, position -> world.getOrDefault(position, Material.AIR), (position, material) -> {
            if (material == Material.AIR) {
                assertFalse(world.getOrDefault(position, Material.AIR) == Material.AIR);
                world.remove(position);
            } else {
                world.put(position, material);
            }
        });

        assertEquals(new HashSet<>(SumoArena.floorBlocks(next)), world.keySet());
        for (int slot = 1; slot <= 2; slot++) {
            var spawn = SumoArena.spawn(next, null, slot);
            assertEquals(Material.STONE, world.get(new SumoArena.Position(spawn.getBlockX(),
                    spawn.getBlockY() - 1, spawn.getBlockZ())));
            for (int y = spawn.getBlockY(); y <= spawn.getBlockY() + 1; y++) {
                assertEquals(Material.AIR, world.getOrDefault(new SumoArena.Position(spawn.getBlockX(), y,
                        spawn.getBlockZ()), Material.AIR));
            }
        }
    }

    @Test
    public void bothSpawnsStandOnTheRingAndFaceEachOtherAcrossItsCenter() {
        for (var config : new SumoConfig[] {new SumoConfig(4, 64, 61, 2, 3, 3),
                new SumoConfig(6, 80, 75, 3.5, 5, 4)}) {
            var first = SumoArena.spawn(config, null, 1);
            var second = SumoArena.spawn(config, null, 2);
            var blocks = SumoArena.floorBlocks(config);

            assertEquals(1, first.getX() + second.getX(), 0);
            assertEquals(2 * config.spawnDistance(), second.getX() - first.getX(), 0);
            assertEquals(-90, first.getYaw(), 0);
            assertEquals(90, second.getYaw(), 0);
            for (var spawn : new Location[] {first, second}) {
                assertEquals(config.floorY() + 1, spawn.getY(), 0);
                assertEquals(0.5, spawn.getZ(), 0);
                assertTrue(blocks.contains(new SumoArena.Position(spawn.getBlockX(), config.floorY(), spawn.getBlockZ())));
            }
            assertThrows(IllegalArgumentException.class, () -> SumoArena.spawn(config, null, 0));
            assertThrows(IllegalArgumentException.class, () -> SumoArena.spawn(config, null, 3));
        }
    }
}
