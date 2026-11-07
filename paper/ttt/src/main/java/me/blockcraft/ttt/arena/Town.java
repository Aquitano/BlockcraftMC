package me.blockcraft.ttt.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Optional;

/**
 * The map of a round, loaded or built: where players start, the tester and where spectators watch. Every chest in
 * its world is a loot chest and every ender chest holds the round's sword. The iron {@code traitorDoors} (each by its
 * lower half) open for living traitors only; a map with {@code tasks} can be played as TTT 2.
 */
public record Town(String name, World world, List<Location> spawns, TesterRoom tester, Location spectatorSpawn,
                   List<Block> traitorDoors, Optional<TaskLayout> tasks) {

    public Town {
        spawns = List.copyOf(spawns);
        traitorDoors = List.copyOf(traitorDoors);
    }

    public Location spawn(int index) {
        return spawns.get(Math.floorMod(index, spawns.size())).clone();
    }

    /** The column inside the world border, which every map draws round itself. */
    public BoundingBox bounds() {
        var border = world.getWorldBorder();
        var center = border.getCenter();
        double half = border.getSize() / 2;
        return new BoundingBox(center.getX() - half, world.getMinHeight(), center.getZ() - half,
                center.getX() + half, world.getMaxHeight(), center.getZ() + half);
    }
}
