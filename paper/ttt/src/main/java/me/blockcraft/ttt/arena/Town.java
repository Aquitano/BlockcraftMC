package me.blockcraft.ttt.arena;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;

/**
 * The map of a round, loaded or built: where players start, the tester and where spectators watch. Every chest in
 * its world is a loot chest and every ender chest holds the round's sword.
 */
public record Town(String name, World world, List<Location> spawns, TesterRoom tester, Location spectatorSpawn) {

    public Location spawn(int index) {
        return spawns.get(Math.floorMod(index, spawns.size())).clone();
    }
}
