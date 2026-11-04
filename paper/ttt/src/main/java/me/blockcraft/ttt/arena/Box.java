package me.blockcraft.ttt.arena;

import me.blockcraft.api.world.Point;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A block range, both corners included. */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    /** {@code {x1, y1, z1, x2, y2, z2}}, the corners in any order. */
    public static Box parse(Map<?, ?> values) {
        return of((int) Point.number(values, "x1"), (int) Point.number(values, "y1"), (int) Point.number(values, "z1"),
                (int) Point.number(values, "x2"), (int) Point.number(values, "y2"), (int) Point.number(values, "z2"));
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(Location location) {
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public List<Block> blocks(World world) {
        var blocks = new ArrayList<Block>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    blocks.add(world.getBlockAt(x, y, z));
                }
            }
        }
        return List.copyOf(blocks);
    }
}
