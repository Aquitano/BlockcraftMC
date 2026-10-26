package me.blockcraft.endergames.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.EndPortalFrame;

import java.util.Random;

/**
 * The waiting lobby, built into an empty world at every start: a floating island of end stone that tapers into a
 * point below, paths of end stone bricks, an end portal frame with every eye set at its heart, obsidian pillars
 * crowned with end rods and a few chorus plants. Barriers at the edge keep everyone on it; the island is the same
 * at every start because its outline draws from a fixed seed.
 */
public final class LobbyPlatform {

    private static final int FLOOR = 100;
    private static final int RADIUS = 14;
    private static final int DEPTH = 12;
    private static final int PILLAR = 9;
    private static final long SEED = 0xE6L;

    private LobbyPlatform() {
    }

    public static Location build(World world) {
        var random = new Random(SEED);
        var outline = new double[360];
        for (int angle = 0; angle < outline.length; angle++) {
            double radians = Math.toRadians(angle);
            outline[angle] = RADIUS + 1.6 * Math.sin(radians * 3 + 0.7) + 1.1 * Math.cos(radians * 5) + random.nextDouble() * 0.6;
        }
        int reach = RADIUS + 4;
        for (int x = -reach; x <= reach; x++) {
            for (int z = -reach; z <= reach; z++) {
                double distance = Math.hypot(x, z);
                double edge = outline[Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(z, x))), outline.length)];
                if (distance > edge) {
                    continue;
                }
                var top = distance < 3.5 ? Material.PURPUR_BLOCK
                        : Math.abs(x) <= 1 || Math.abs(z) <= 1 || Math.abs(distance - 8) < 0.7 ? Material.END_STONE_BRICKS
                        : Material.END_STONE;
                world.getBlockAt(x, FLOOR, z).setType(top, false);
                int below = (int) Math.round(DEPTH * (1 - distance / edge) + random.nextInt(2));
                for (int y = 1; y <= below; y++) {
                    world.getBlockAt(x, FLOOR - y, z).setType(Material.END_STONE, false);
                }
                if (distance > edge - 1) {
                    for (int y = 1; y <= 3; y++) {
                        world.getBlockAt(x, FLOOR + y, z).setType(Material.BARRIER, false);
                    }
                }
            }
        }
        portal(world);
        for (int x : new int[]{-8, 8}) {
            for (int z : new int[]{-8, 8}) {
                pillar(world, x, z);
            }
        }
        for (int[] spot : new int[][]{{-10, 2}, {10, -3}, {3, 11}, {-4, -11}, {-11, -5}}) {
            chorus(world, spot[0], spot[1], 3 + random.nextInt(3));
        }
        return new Location(world, 0.5, FLOOR + 1, 6.5, 180, 0);
    }

    /** Twelve frames round a pool of crying obsidian, each with its eye set, all facing in. */
    private static void portal(World world) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean corner = Math.abs(x) == 2 && Math.abs(z) == 2;
                if (corner) {
                    continue;
                }
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    world.getBlockAt(x, FLOOR, z).setType(Material.CRYING_OBSIDIAN, false);
                    continue;
                }
                var facing = x == -2 ? BlockFace.EAST : x == 2 ? BlockFace.WEST : z == -2 ? BlockFace.SOUTH : BlockFace.NORTH;
                world.getBlockAt(x, FLOOR + 1, z).setBlockData(Material.END_PORTAL_FRAME.createBlockData(data -> {
                    var frame = (EndPortalFrame) data;
                    frame.setEye(true);
                    frame.setFacing(facing);
                }), false);
            }
        }
    }

    private static void pillar(World world, int x, int z) {
        for (int y = 1; y <= PILLAR; y++) {
            world.getBlockAt(x, FLOOR + y, z).setType(y == PILLAR - 2 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN, false);
        }
        world.getBlockAt(x, FLOOR + PILLAR + 1, z).setType(Material.END_ROD, false);
    }

    /** A stem of chorus with a flower on top; every piece of stem joins the one above and the one below. */
    private static void chorus(World world, int x, int z, int height) {
        var stem = Material.CHORUS_PLANT.createBlockData(data -> {
            ((MultipleFacing) data).setFace(BlockFace.UP, true);
            ((MultipleFacing) data).setFace(BlockFace.DOWN, true);
        });
        for (int y = 1; y < height; y++) {
            world.getBlockAt(x, FLOOR + y, z).setBlockData(stem, false);
        }
        world.getBlockAt(x, FLOOR + height, z).setType(Material.CHORUS_FLOWER, false);
    }
}
