package me.blockcraft.endergames.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

/** The waiting lobby, built into an empty world at every start: a floor of end stone in a ring of purple glass. */
public final class LobbyPlatform {

    private static final int FLOOR = 100;
    private static final int HALF = 8;
    private static final int WALL = 3;

    private LobbyPlatform() {
    }

    public static Location build(World world) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                boolean pattern = (Math.abs(x) + Math.abs(z)) % 4 == 0;
                world.getBlockAt(x, FLOOR, z).setType(edge ? Material.OBSIDIAN
                        : pattern ? Material.PURPUR_BLOCK : Material.END_STONE_BRICKS, false);
                for (int y = 1; y <= WALL; y++) {
                    world.getBlockAt(x, FLOOR + y, z).setType(edge ? Material.PURPLE_STAINED_GLASS : Material.AIR, false);
                }
            }
        }
        world.getBlockAt(0, FLOOR, 0).setType(Material.CRYING_OBSIDIAN, false);
        return new Location(world, 0.5, FLOOR + 1, 0.5);
    }
}
