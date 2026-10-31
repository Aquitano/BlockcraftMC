package me.blockcraft.knockbackffa;

import me.blockcraft.api.world.Worlds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class FfaArena {

    static final int FLOOR = 64;
    static final int FALL = 48;

    private final World world = Worlds.createArena(new NamespacedKey("knockbackffa", "inseln"));
    private final List<Location> spawns;
    private final List<Block> pickups;

    FfaArena() {
        for (int x = -25; x <= 25; x++) {
            for (int z = -25; z <= 25; z++) {
                boolean middle = x * x + z * z <= 100;
                boolean island = Math.pow(Math.abs(x) - 18, 2) + z * z <= 36
                        || x * x + Math.pow(Math.abs(z) - 18, 2) <= 36;
                boolean bridge = Math.abs(x) <= 1 && Math.abs(z) <= 18
                        || Math.abs(z) <= 1 && Math.abs(x) <= 18;
                world.getBlockAt(x, FLOOR, z).setType(middle || island || bridge
                        ? (middle ? Material.SMOOTH_QUARTZ : Material.CYAN_TERRACOTTA) : Material.AIR, false);
                for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
        for (int[] point : new int[][] {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}}) {
            world.getBlockAt(point[0], FLOOR + 1, point[1]).setType(Material.QUARTZ_PILLAR, false);
            world.getBlockAt(point[0], FLOOR + 2, point[1]).setType(Material.QUARTZ_PILLAR, false);
        }
        spawns = List.of(at(18, 0), at(-18, 0), at(0, 18), at(0, -18));
        pickups = List.of(world.getBlockAt(6, FLOOR, 6), world.getBlockAt(-6, FLOOR, 6),
                world.getBlockAt(6, FLOOR, -6), world.getBlockAt(-6, FLOOR, -6));
        pickups.forEach(block -> block.setType(Material.EMERALD_BLOCK, false));
        world.setSpawnLocation(watch());
        world.getWorldBorder().setCenter(0.5, 0.5);
        world.getWorldBorder().setSize(54);
    }

    private Location at(int x, int z) {
        var location = new Location(world, x + 0.5, FLOOR + 1, z + 0.5);
        location.setDirection(new org.bukkit.util.Vector(-x, 0, -z));
        return location;
    }

    World world() {
        return world;
    }

    Location spawn() {
        return spawns.get(ThreadLocalRandom.current().nextInt(spawns.size())).clone();
    }

    Location watch() {
        return new Location(world, 0.5, FLOOR + 12, 0.5);
    }

    List<Block> pickups() {
        return pickups;
    }
}
