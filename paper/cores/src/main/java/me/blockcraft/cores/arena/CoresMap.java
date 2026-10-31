package me.blockcraft.cores.arena;

import me.blockcraft.cores.game.Core;
import me.blockcraft.cores.game.Side;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The built map of a round: where each team spawns, its cores, and which blocks belong to the bases. Base blocks
 * do not break, except their iron; nothing is placed in a core's cube or on a spawn; everything else is the
 * players' to break and build, inside the border and below the build height.
 */
public record CoresMap(String name, World world, Map<Side, Location> spawns, List<Core> cores, Set<Long> protectedBlocks,
                       List<Box> noBuild, Box border, Location lobbySpawn, Location spectatorSpawn) {

    /** A block range, both corners included. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public boolean contains(Block block) {
            return contains(block.getX(), block.getY(), block.getZ());
        }
    }

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    public Location spawn(Side side) {
        return spawns.get(side).clone();
    }

    public boolean isProtected(Block block) {
        return protectedBlocks.contains(key(block.getX(), block.getY(), block.getZ()));
    }

    public boolean mayBuild(Block block, int buildHeight) {
        return block.getWorld().equals(world) && border.contains(block.getX(), block.getY(), block.getZ())
                && block.getY() <= buildHeight && noBuild.stream().noneMatch(box -> box.contains(block));
    }
}
