package me.blockcraft.zombies.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public record BlockBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public BlockBox {
        long width = (long) maxX - minX + 1;
        long height = (long) maxY - minY + 1;
        long depth = (long) maxZ - minZ + 1;
        if (width <= 0 || height <= 0 || depth <= 0 || width > 128 || height > 128 || depth > 128
                || width * height * depth > 250_000) {
            throw new IllegalArgumentException("Arena box has invalid bounds or exceeds 250000 blocks");
        }
    }

    public boolean contains(Location location) {
        return location.getX() >= minX && location.getX() < maxX + 1
                && location.getY() >= minY && location.getY() < maxY + 1
                && location.getZ() >= minZ && location.getZ() < maxZ + 1;
    }

    public boolean contains(BlockBox other) {
        return other.minX >= minX && other.maxX <= maxX && other.minY >= minY && other.maxY <= maxY
                && other.minZ >= minZ && other.maxZ <= maxZ;
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
        return blocks;
    }
}
