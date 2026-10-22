package me.blockcraft.mlgrush.arena;

import me.blockcraft.mlgrush.config.MapPool;
import me.blockcraft.mlgrush.config.RushConfig.ArenaSettings;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ArenaLayoutTest {

    @Test
    public void allMapsHavePairedBedsInwardSpawnsAndProtectedHeadroom() {
        for (var layout : layouts()) {
            int floor = layout.dimensions().floorY();
            assertTrue(layout.spawnX(0) < 0);
            assertTrue(layout.spawnX(1) >= layout.dimensions().bridgeLength());
            assertEquals(-90, layout.spawnYaw(0), 0);
            assertEquals(90, layout.spawnYaw(1), 0);
            for (int slot = 0; slot < 2; slot++) {
                var foot = layout.bed(slot, false);
                var head = layout.bed(slot, true);
                assertEquals(slot, layout.bedOwner(foot));
                assertEquals(slot, layout.bedOwner(head));
                assertEquals(1, Math.abs(foot.x() - head.x()));
                assertEquals(foot.y(), head.y());
                assertEquals(foot.z(), head.z());
                assertEquals(slot == 0 ? Material.RED_BED : Material.BLUE_BED, layout.blocks().get(head));
                assertTrue(layout.blocks().containsKey(new ArenaLayout.Position(head.x(), floor, head.z())));
                assertFalse(layout.canBuild(foot));
                assertFalse(layout.canBuild(head));
                assertFalse(layout.canBuild(new ArenaLayout.Position(layout.spawnX(slot), floor + 1, 0)));
                assertFalse(layout.canBuild(new ArenaLayout.Position(layout.spawnX(slot), floor + 2, 0)));
                assertFalse(layout.blocks().containsKey(new ArenaLayout.Position(layout.spawnX(slot), floor + 1, 0)));
            }
        }
    }

    @Test
    public void allBridgesConnectTheBasesAndBuildLimitsContainTheMap() {
        for (var layout : layouts()) {
            var dimensions = layout.dimensions();
            for (int x = -1; x <= dimensions.bridgeLength(); x++) {
                for (int z = -dimensions.bridgeWidth() / 2; z <= dimensions.bridgeWidth() / 2; z++) {
                    assertTrue(layout.blocks().containsKey(new ArenaLayout.Position(x, dimensions.floorY(), z)));
                }
            }
            assertTrue(layout.blocks().keySet().stream().allMatch(layout::contains));
            assertTrue(layout.canBuild(new ArenaLayout.Position(1, dimensions.floorY() + 1, 0)));
            assertFalse(layout.canBuild(new ArenaLayout.Position(layout.minX() - 1, dimensions.floorY(), 0)));
            assertFalse(layout.canBuild(new ArenaLayout.Position(layout.maxX() + 1, dimensions.floorY(), 0)));
            assertFalse(layout.canBuild(new ArenaLayout.Position(1, dimensions.floorY() - 1, 0)));
            assertFalse(layout.canBuild(new ArenaLayout.Position(1, dimensions.floorY() + dimensions.buildHeight() + 1, 0)));
            assertFalse(layout.canBuild(new ArenaLayout.Position(1, dimensions.floorY(), layout.radiusZ() + 1)));
        }
    }

    @Test
    public void resetClearsPlacedBlocksAndRestoresBothBedsAndTerrainOnEveryMap() {
        for (var layout : layouts()) {
            var original = layout.blocks();
            var world = new HashMap<>(original);
            var placed = new HashSet<ArenaLayout.Position>();
            var block = new ArenaLayout.Position(1, layout.dimensions().floorY() + 1, 0);
            placed.add(block);
            world.put(block, Material.RED_TERRACOTTA);
            world.remove(layout.bed(0, false));
            world.remove(layout.bed(0, true));
            world.remove(layout.bed(1, true));

            layout.reset(placed, world::put);

            assertTrue(placed.isEmpty());
            assertEquals(Material.AIR, world.remove(block));
            assertEquals(original, world);
            layout.reset(placed, world::put);
            assertEquals(original, world);
        }
    }

    private static List<ArenaLayout> layouts() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(ArenaLayoutTest.class.getResourceAsStream("/maps.yml")), StandardCharsets.UTF_8));
        return MapPool.from(yaml, new ArenaSettings(4, 16, 64, 48)).maps().stream()
                .map(map -> new ArenaLayout(map.dimensions(), map.palette())).toList();
    }
}
