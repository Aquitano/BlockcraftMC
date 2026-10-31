package me.blockcraft.endergames.world;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The island everyone starts on, an island of end stone floating {@link #HEIGHT_ABOVE_GROUND} blocks over the
 * highest ground below it at the round's centre. Players stand on a ring round a small obsidian pillar with the
 * start chests between them and it, and jump down into the world from there. When the protection time ends the
 * island crumbles, so nobody holds out up here for the rest of the round. Its outline draws from a fixed seed, so it
 * is the same island in every round.
 */
public final class StartIsland {

    public static final int HEIGHT_ABOVE_GROUND = 40;

    private static final int RADIUS = 11;
    private static final int DEPTH = 8;
    private static final double SPAWN_RADIUS = 7.0;
    private static final double CHEST_RADIUS = 3.0;
    private static final int PILLAR = 3;
    private static final int SPECTATOR_HEIGHT = 15;
    private static final int TOP_MARGIN = 24;
    private static final long SEED = 0x57A27L;

    private final World world;
    private final int centerX;
    private final int centerZ;
    private final int top;
    private final List<Block> blocks = new ArrayList<>();
    private final List<Block> chests = new ArrayList<>();

    private StartIsland(World world, int centerX, int centerZ, int top) {
        this.world = world;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.top = top;
    }

    /** Builds the island over centerX/centerZ, high enough above every tree and hill beneath it. */
    public static StartIsland build(World world, int centerX, int centerZ, int chestCount) {
        int ground = world.getMinHeight();
        for (int x = centerX - RADIUS; x <= centerX + RADIUS; x += 2) {
            for (int z = centerZ - RADIUS; z <= centerZ + RADIUS; z += 2) {
                ground = Math.max(ground, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING));
            }
        }
        int top = Math.min(ground + HEIGHT_ABOVE_GROUND, world.getMaxHeight() - TOP_MARGIN);
        var island = new StartIsland(world, centerX, centerZ, top);
        island.shape();
        island.pillar();
        island.chests(Math.clamp(chestCount, 1, 8));
        return island;
    }

    private void shape() {
        var random = new Random(SEED);
        var outline = new double[360];
        for (int angle = 0; angle < outline.length; angle++) {
            double radians = Math.toRadians(angle);
            outline[angle] = RADIUS + Math.sin(radians * 3 + 0.4) + 0.6 * Math.cos(radians * 5) + random.nextDouble() * 0.4;
        }
        int reach = RADIUS + 2;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double distance = Math.hypot(dx, dz);
                double edge = outline[Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(dz, dx))), outline.length)];
                if (distance > edge) {
                    continue;
                }
                var surface = distance < 1.5 ? Material.PURPUR_BLOCK
                        : Math.abs(distance - CHEST_RADIUS) < 0.6 || Math.abs(distance - SPAWN_RADIUS) < 0.6 ? Material.END_STONE_BRICKS
                        : Material.END_STONE;
                place(dx, top, dz, surface);
                int below = Math.max(1, (int) Math.round(DEPTH * (1 - distance / edge)) + random.nextInt(2));
                for (int y = 1; y <= below; y++) {
                    place(dx, top - y, dz, y == below && random.nextInt(4) == 0 ? Material.OBSIDIAN : Material.END_STONE);
                }
            }
        }
    }

    private void pillar() {
        for (int y = 1; y <= PILLAR; y++) {
            place(0, top + y, 0, y == PILLAR ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN);
        }
        place(0, top + PILLAR + 1, 0, Material.END_ROD);
    }

    /** Evenly round the pillar, each chest's front turned out towards the players. */
    private void chests(int count) {
        for (var point : Ring.points(0, 0, CHEST_RADIUS, count, Math.PI / count)) {
            int dx = (int) Math.round(point.x());
            int dz = (int) Math.round(point.z());
            var facing = Math.abs(dx) >= Math.abs(dz)
                    ? (dx >= 0 ? BlockFace.EAST : BlockFace.WEST)
                    : (dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH);
            var block = world.getBlockAt(centerX + dx, top + 1, centerZ + dz);
            block.setBlockData(Material.CHEST.createBlockData(data -> ((Directional) data).setFacing(facing)), false);
            blocks.add(block);
            chests.add(block);
        }
    }

    private void place(int dx, int y, int dz, Material material) {
        var block = world.getBlockAt(centerX + dx, y, centerZ + dz);
        block.setType(material, false);
        blocks.add(block);
    }

    /** Where each of {@code count} players starts: evenly on the ring round the pillar, facing it. */
    public List<Location> spawns(int count) {
        var spawns = new ArrayList<Location>();
        for (var point : Ring.points(centerX + 0.5, centerZ + 0.5, SPAWN_RADIUS, Math.max(1, count), 0)) {
            var spawn = new Location(world, point.x(), top + 1, point.z());
            spawn.setDirection(new Location(world, centerX + 0.5, top + 1, centerZ + 0.5).toVector().subtract(spawn.toVector()));
            spawn.setPitch(0);
            spawns.add(spawn);
        }
        return spawns;
    }

    public List<Block> chests() {
        return List.copyOf(chests);
    }

    /** Above the island, where spectators float and the dead come back to watch. */
    public Location spectatorSpawn() {
        return new Location(world, centerX + 0.5, top + SPECTATOR_HEIGHT, centerZ + 0.5);
    }

    /** True if x/z lies under or on the island, where nothing that belongs on the ground may be placed. */
    public boolean covers(int x, int z) {
        return Math.hypot(x - centerX, z - centerZ) <= RADIUS + 2;
    }

    /** True if {@code location} stands on the island or just below it, so that it would fall when the island goes. */
    public boolean carries(Location location) {
        return covers(location.getBlockX(), location.getBlockZ()) && location.getY() >= top - DEPTH - 2;
    }

    /** Takes the whole island away, chests and their loot included, with a crumble of dust. */
    public void crumble() {
        var center = new Location(world, centerX + 0.5, top, centerZ + 0.5);
        world.spawnParticle(Particle.BLOCK, center, 300, RADIUS / 2.0, 2, RADIUS / 2.0, Material.END_STONE.createBlockData());
        world.playSound(center, Sound.BLOCK_STONE_BREAK, 2.0f, 0.6f);
        blocks.forEach(block -> block.setType(Material.AIR, false));
        blocks.clear();
        chests.clear();
    }
}
