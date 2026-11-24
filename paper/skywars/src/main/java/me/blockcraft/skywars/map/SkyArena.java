package me.blockcraft.skywars.map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Map;

/**
 * A built map: one spawn per island of the ring, each in a glass cage that opens at the start, the chests by what
 * they hold, and where the round's border and spectators go.
 */
public record SkyArena(Theme theme, World world, List<Location> spawns, List<Block> cages, Map<ChestKind, List<Block>> chests,
                       int borderRadius, Location spectatorSpawn) {
}
