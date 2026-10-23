package me.blockcraft.bedwars.item;

import me.blockcraft.bedwars.map.Border;
import me.blockcraft.bedwars.team.TeamColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RescuePlatformTest {

    @Test
    public void clipsEveryPlatformBlockToTheBorderAndProtectedSpawnSpace() {
        var border = new Border(-10, 10, -10, 10);
        var spawn = Set.of(new Position(0, 64, 0), new Position(0, 65, 0));
        for (var feet : List.of(new Position(10, 66, 0), new Position(0, 65, 0), new Position(0, 66, 0))) {
            var terrain = new Terrain();
            terrain.materials.put(new Position(11, 65, 0), Material.SHORT_GRASS);
            var tracked = new ArrayList<Position>();
            var platform = new RescuePlatform(2.5,
                    block -> border.contains(block.getX(), block.getZ()) && !spawn.contains(position(block)),
                    block -> block.getType() == Material.SHORT_GRASS, block -> tracked.add(position(block)));

            assertTrue(platform.spread(terrain.player(feet), TeamColor.RED));

            assertFalse(tracked.isEmpty());
            assertTrue(tracked.stream().allMatch(block -> border.contains(block.x(), block.z())));
            assertTrue(tracked.stream().noneMatch(spawn::contains));
            assertEquals(tracked, terrain.changed);
            assertEquals(Material.SHORT_GRASS, terrain.materials.get(new Position(11, 65, 0)));
        }
    }

    @Test
    public void aFullyProtectedPlatformDoesNotUseTheItemOrTrackBlocks() {
        var terrain = new Terrain();
        var tracked = new ArrayList<Block>();
        var platform = new RescuePlatform(2.5, block -> false, block -> true, tracked::add);

        assertFalse(platform.spread(terrain.player(new Position(0, 65, 0)), TeamColor.RED));

        assertTrue(terrain.changed.isEmpty());
        assertTrue(tracked.isEmpty());
    }

    private record Position(int x, int y, int z) { }

    private static Position position(Block block) {
        return new Position(block.getX(), block.getY(), block.getZ());
    }

    private static final class Terrain {
        private final Map<Position, Material> materials = new HashMap<>();
        private final List<Position> changed = new ArrayList<>();

        private Block block(Position position) {
            return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getX" -> position.x();
                        case "getY" -> position.y();
                        case "getZ" -> position.z();
                        case "isEmpty" -> materials.getOrDefault(position, Material.AIR) == Material.AIR;
                        case "getType" -> materials.getOrDefault(position, Material.AIR);
                        case "getRelative" -> {
                            if (args[0] instanceof BlockFace face) {
                                yield block(new Position(position.x() + face.getModX(), position.y() + face.getModY(),
                                        position.z() + face.getModZ()));
                            }
                            yield block(new Position(position.x() + (int) args[0], position.y() + (int) args[1],
                                    position.z() + (int) args[2]));
                        }
                        case "setType" -> {
                            materials.put(position, (Material) args[0]);
                            changed.add(position);
                            yield null;
                        }
                        default -> throw new AssertionError("Unexpected block call: " + method.getName());
                    });
        }

        private Player player(Position feet) {
            var location = new Location(null, feet.x() + 0.5, feet.y(), feet.z() + 0.5) {
                @Override
                public Block getBlock() {
                    return block(feet);
                }
            };
            return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getLocation")) {
                            return location;
                        }
                        throw new AssertionError("Unexpected player call: " + method.getName());
                    });
        }
    }
}
