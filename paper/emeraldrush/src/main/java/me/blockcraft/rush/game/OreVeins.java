package me.blockcraft.rush.game;

import me.blockcraft.rush.config.RushConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Places the round's emerald ore into the mine walls around the search spot. */
final class OreVeins {

    private static final List<BlockFace> SIDES = List.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST);

    private OreVeins() {
    }

    /**
     * Fills outward from the search spot through every block a player can move through within the radius, and takes
     * the host blocks that border it, so no ore ends up next to a sealed pocket nobody can reach.
     */
    static void seed(Location centre, RushConfig.Ores ores) {
        var start = centre.getBlock();
        int radius = ores.radius();
        var open = new HashSet<Block>(List.of(start));
        var queue = new ArrayDeque<Block>(List.of(start));
        var walls = new LinkedHashSet<Block>();
        while (!queue.isEmpty()) {
            var block = queue.poll();
            for (var side : SIDES) {
                var next = block.getRelative(side);
                if (Math.abs(next.getX() - start.getX()) > radius || Math.abs(next.getY() - start.getY()) > radius
                        || Math.abs(next.getZ() - start.getZ()) > radius || open.contains(next)) {
                    continue;
                }
                if (next.isPassable()) {
                    open.add(next);
                    queue.add(next);
                } else if (ores.hosts().contains(next.getType())) {
                    walls.add(next);
                }
            }
        }
        var shuffled = new ArrayList<>(walls);
        Collections.shuffle(shuffled, ThreadLocalRandom.current());
        shuffled.stream().limit(ores.count()).forEach(block -> block.setType(Material.EMERALD_ORE, false));
    }
}
