package me.blockcraft.ttt.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Set;

/** The built map of a round: the town's chests, its ender chest and tester, where players start and wait. */
public record Town(String name, World world, List<Location> spawns, Set<Block> chests, Block enderChest,
                   TesterRoom tester, Location lobbySpawn, Location spectatorSpawn) {

    public Location spawn(int index) {
        return spawns.get(Math.floorMod(index, spawns.size())).clone();
    }
}
