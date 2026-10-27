package me.blockcraft.rush.game;

import me.blockcraft.rush.config.RushConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Places the round's emerald ore into the mine walls around the search spot. */
final class OreVeins {

    private static final List<BlockFace> SIDES = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST);

    private OreVeins() {
    }

    static void seed(Location centre, RushConfig.Ores ores) {
        var world = centre.getWorld();
        int radius = ores.radius();
        int minY = Math.max(world.getMinHeight(), centre.getBlockY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, centre.getBlockY() + radius);
        var walls = new ArrayList<Block>();
        for (int x = centre.getBlockX() - radius; x <= centre.getBlockX() + radius; x++) {
            for (int z = centre.getBlockZ() - radius; z <= centre.getBlockZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (ores.hosts().contains(block.getType()) && SIDES.stream().anyMatch(side -> block.getRelative(side).isEmpty())) {
                        walls.add(block);
                    }
                }
            }
        }
        Collections.shuffle(walls, ThreadLocalRandom.current());
        walls.stream().limit(ores.count()).forEach(block -> block.setType(Material.EMERALD_ORE, false));
    }
}
