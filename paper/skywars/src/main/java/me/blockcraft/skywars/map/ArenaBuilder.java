package me.blockcraft.skywars.map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds a SkyWars map into an empty world: twelve spawn islands in a ring round the middle, four semi-middle islands
 * between them and the middle with its shrine. Every island is a lump of the theme's ground with a wobbling outline
 * that tapers to a point below; a spawn island carries a tree, a little hill and three chests behind a glass cage,
 * a semi-middle two chests, the middle four round an enchanting table. Blocks go in without physics.
 */
public final class ArenaBuilder {

    static final int TOP = 80;
    private static final int SLOTS = 12;
    private static final int SPAWN_RING = 40;
    private static final int SEMI_RING = 21;
    private static final int CLEAR = 76;
    private static final int CLEAR_LOW = 50;
    private static final int CLEAR_HIGH = 112;
    private static final int BORDER_MARGIN = 14;
    private static final double TAU = Math.PI * 2;

    private final World world;
    private final Theme theme;
    private final Random random;
    private final List<Block> cages = new ArrayList<>();
    private final Map<ChestKind, List<Block>> chests = new EnumMap<>(ChestKind.class);

    private ArenaBuilder(World world, Theme theme) {
        this.world = world;
        this.theme = theme;
        this.random = new Random(theme.seed());
        for (var kind : ChestKind.values()) {
            chests.put(kind, new ArrayList<>());
        }
    }

    public static SkyArena build(World world, Theme theme) {
        return new ArenaBuilder(world, theme).build();
    }

    /** The waiting lobby: a grassy island with a tree among clouds, barriers round its edge. */
    public static Location lobby(World world) {
        var builder = new ArenaBuilder(world, Theme.WOLKENHAIN);
        builder.island(0, 0, 9.5, 9);
        builder.hill(-4, 3, 3.2, 2);
        builder.oak(4, -3);
        builder.oak(-5, -4);
        builder.scatter(0, 0, 13);
        builder.clouds(10, 20, 32, 5);
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                if (builder.isGround(x, z) && !builder.isGround(x + 1, z) || builder.isGround(x, z) && !builder.isGround(x - 1, z)
                        || builder.isGround(x, z) && !builder.isGround(x, z + 1) || builder.isGround(x, z) && !builder.isGround(x, z - 1)) {
                    for (int y = TOP + 1; y <= TOP + 3; y++) {
                        if (world.getBlockAt(x, y, z).isEmpty()) {
                            world.getBlockAt(x, y, z).setType(Material.BARRIER, false);
                        }
                    }
                }
            }
        }
        return new Location(world, 0.5, builder.surface(0, 0) + 1, 0.5, 180, 0);
    }

    private SkyArena build() {
        clear();
        var spawns = new ArrayList<Location>();
        for (var point : Ring.points(SLOTS, SPAWN_RING, 0)) {
            spawns.add(spawnIsland((int) Math.round(point.x()), (int) Math.round(point.z())));
        }
        for (var point : Ring.points(4, SEMI_RING, 45)) {
            semiMiddle((int) Math.round(point.x()), (int) Math.round(point.z()));
        }
        middle();
        if (theme == Theme.WOLKENHAIN) {
            clouds(18, SPAWN_RING + BORDER_MARGIN + 4, SPAWN_RING + BORDER_MARGIN + 16, 6);
        }
        var byKind = new EnumMap<ChestKind, List<Block>>(ChestKind.class);
        chests.forEach((kind, blocks) -> byKind.put(kind, List.copyOf(blocks)));
        return new SkyArena(theme, world, List.copyOf(spawns), List.copyOf(cages), byKind, SPAWN_RING + BORDER_MARGIN,
                new Location(world, 0.5, TOP + 22, SPAWN_RING * 0.6, 180, 35));
    }

    /** The world keeps what the last round built, and the last map may have been another one. */
    private void clear() {
        for (int x = -CLEAR; x <= CLEAR; x++) {
            for (int z = -CLEAR; z <= CLEAR; z++) {
                for (int y = CLEAR_LOW; y <= CLEAR_HIGH; y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /**
     * A spawn island facing the middle: the player starts a step towards the middle in a glass cage, three chests
     * wait behind them, a tree grows on one side and a hill rises on the other.
     */
    private Location spawnIsland(int cx, int cz) {
        island(cx, cz, 5.4, 9);
        double length = Math.hypot(cx, cz);
        double ox = cx / length;
        double oz = cz / length;
        double px = -oz;
        double pz = ox;
        int sx = cx - (int) Math.round(ox);
        int sz = cz - (int) Math.round(oz);
        hill(cx + (int) Math.round(-px * 3 + ox), cz + (int) Math.round(-pz * 3 + oz), 2.2, 1);
        tree(cx + (int) Math.round(px * 3 + ox), cz + (int) Math.round(pz * 3 + oz));
        scatter(cx, cz, 7);
        var kinds = List.of(ChestKind.ARMOUR, ChestKind.WEAPONS, ChestKind.UTILITY);
        for (int index = 0; index < kinds.size(); index++) {
            int side = index - 1;
            int x = sx + (int) Math.round(ox * 2 + px * side * 1.5);
            int z = sz + (int) Math.round(oz * 2 + pz * side * 1.5);
            chest(x, surface(x, z) + 1, z, facing(sx - x, sz - z), kinds.get(index));
        }
        int floor = surface(sx, sz);
        cage(sx, floor + 1, sz);
        float yaw = (float) Math.toDegrees(Math.atan2(cx, -cz));
        return new Location(world, sx + 0.5, floor + 1, sz + 0.5, yaw, 0);
    }

    /** A semi-middle between two spawn islands: two chests, a crafting table and something growing. */
    private void semiMiddle(int cx, int cz) {
        island(cx, cz, 5.8, 10);
        double length = Math.hypot(cx, cz);
        int ox = (int) Math.round(cx / length * 3);
        int oz = (int) Math.round(cz / length * 3);
        if (theme == Theme.FROSTSPITZE) {
            iceSpike(cx + ox, cz + oz);
        } else {
            tree(cx + ox, cz + oz);
        }
        scatter(cx, cz, 8);
        int px = -oz / 2;
        int pz = ox / 2;
        chest(cx + px, surface(cx + px, cz + pz) + 1, cz + pz, facing(-px, -pz), ChestKind.SEMI_MIDDLE);
        chest(cx - px, surface(cx - px, cz - pz) + 1, cz - pz, facing(px, pz), ChestKind.SEMI_MIDDLE);
        set(cx - ox / 3, surface(cx - ox / 3, cz - oz / 3) + 1, cz - oz / 3, Material.CRAFTING_TABLE);
    }

    /**
     * The middle: a plateau two blocks over the island with a step round it, and on it a shrine of four pillars under
     * a roof round the enchanting table, with the four best chests at its sides.
     */
    private void middle() {
        island(0, 0, 10.5, 15);
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double distance = Math.hypot(x, z);
                if (distance <= 6.5 && isGround(x, z)) {
                    int height = distance <= 5.2 ? 2 : 1;
                    for (int y = 0; y < height; y++) {
                        set(x, TOP + y, z, theme.soil());
                    }
                    set(x, TOP + height, z, theme.top());
                }
            }
        }
        int floor = TOP + 2;
        set(0, floor + 1, 0, Material.ENCHANTING_TABLE);
        for (int x : new int[]{-2, 2}) {
            for (int z : new int[]{-2, 2}) {
                for (int y = floor + 1; y <= floor + 4; y++) {
                    set(x, y, z, theme.pillar());
                }
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 && Math.abs(z) == 3) {
                    continue;
                }
                set(x, floor + 5, z, theme.roof().createBlockData(data -> ((Slab) data).setType(Slab.Type.BOTTOM)));
            }
        }
        set(0, floor + 4, 0, Material.LANTERN.createBlockData(data -> ((Lantern) data).setHanging(true)));
        for (var side : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            chest(side.getModX() * 3, floor + 1, side.getModZ() * 3, side.getOppositeFace(), ChestKind.MIDDLE);
        }
        for (var point : Ring.points(3, 8, 20)) {
            int x = (int) Math.round(point.x());
            int z = (int) Math.round(point.z());
            if (isGround(x, z)) {
                tree(x, z);
            }
        }
        scatter(0, 0, 12);
    }

    /**
     * Columns of an island round cx/cz: the outline wobbles round {@code radius}, and below the top the island
     * reaches {@code depth} blocks down in its middle and tapers to its rim, soil first, then stone with veins.
     */
    private void island(int cx, int cz, double radius, int depth) {
        double a = random.nextDouble() * TAU;
        double b = random.nextDouble() * TAU;
        int reach = (int) Math.ceil(radius * 1.3) + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double distance = Math.hypot(dx, dz);
                double angle = Math.atan2(dz, dx);
                double edge = radius * (1 + 0.16 * Math.sin(3 * angle + a) + 0.1 * Math.cos(5 * angle + b));
                if (distance > edge) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int below = (int) Math.round(depth * Math.pow(1 - distance / edge, 0.75)) + random.nextInt(2);
                set(x, TOP, z, theme.top());
                for (int y = 1; y <= below; y++) {
                    set(x, TOP - y, z, y <= 2 ? theme.soil() : random.nextInt(8) == 0 ? vein() : theme.stone());
                }
                if (theme == Theme.WUESTENWIND && below > 4) {
                    set(x, TOP - 4, z, Material.ORANGE_TERRACOTTA);
                }
            }
        }
    }

    private Material vein() {
        return theme.veins().get(random.nextInt(theme.veins().size()));
    }

    /** A mound of {@code height} blocks round x/z on the island, never past its rim. */
    private void hill(int cx, int cz, double radius, int height) {
        int reach = (int) Math.ceil(radius);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double distance = Math.hypot(dx, dz);
                int x = cx + dx;
                int z = cz + dz;
                if (distance > radius || !isGround(x, z)) {
                    continue;
                }
                int raise = distance <= radius * 0.55 ? height + 1 : height;
                for (int y = 0; y < raise; y++) {
                    set(x, TOP + y, z, theme.soil());
                }
                set(x, TOP + raise, z, theme.top());
            }
        }
    }

    private void tree(int x, int z) {
        switch (theme) {
            case WOLKENHAIN -> oak(x, z);
            case FROSTSPITZE -> spruce(x, z);
            case WUESTENWIND -> {
                if (random.nextBoolean()) {
                    palm(x, z);
                } else {
                    cactus(x, z);
                }
            }
        }
    }

    private void oak(int x, int z) {
        int base = surface(x, z) + 1;
        int trunk = 4 + random.nextInt(2);
        for (int y = trunk - 2; y <= trunk + 1; y++) {
            int reach = y >= trunk ? 1 : 2;
            leafRing(x, base + y, z, reach, Material.OAK_LEAVES, true);
        }
        set(x, base + trunk + 2, z, leaves(Material.OAK_LEAVES));
        for (int y = 0; y < trunk; y++) {
            set(x, base + y, z, Material.OAK_LOG);
        }
    }

    private void spruce(int x, int z) {
        int base = surface(x, z) + 1;
        int height = 6 + random.nextInt(2);
        for (int y = 2; y < height; y++) {
            int reach = (height - y) % 2 == 0 ? 1 : 2;
            if (y >= height - 2) {
                reach = 1;
            }
            leafRing(x, base + y, z, reach, Material.SPRUCE_LEAVES, false);
        }
        set(x, base + height, z, leaves(Material.SPRUCE_LEAVES));
        set(x, base + height + 1, z, snow(1));
        for (int y = 0; y < height; y++) {
            set(x, base + y, z, Material.SPRUCE_LOG);
        }
    }

    private void palm(int x, int z) {
        int base = surface(x, z) + 1;
        int height = 5;
        for (int y = 0; y < height; y++) {
            set(x, base + y, z, Material.JUNGLE_LOG);
        }
        int crown = base + height;
        set(x, crown, z, leaves(Material.JUNGLE_LEAVES));
        for (var side : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            for (int step = 1; step <= 2; step++) {
                set(x + side.getModX() * step, crown, z + side.getModZ() * step, leaves(Material.JUNGLE_LEAVES));
            }
            set(x + side.getModX() * 3, crown - 1, z + side.getModZ() * 3, leaves(Material.JUNGLE_LEAVES));
        }
    }

    private void cactus(int x, int z) {
        int base = surface(x, z) + 1;
        int height = 2 + random.nextInt(2);
        for (int y = 0; y < height; y++) {
            set(x, base + y, z, Material.CACTUS);
        }
    }

    private void iceSpike(int x, int z) {
        int base = surface(x, z) + 1;
        int height = 6 + random.nextInt(3);
        for (int y = 0; y < height; y++) {
            set(x, base + y, z, Material.PACKED_ICE);
            if (y < height / 2) {
                for (var side : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
                    set(x + side.getModX(), base + y, z + side.getModZ(), Material.PACKED_ICE);
                }
            }
        }
    }

    private void leafRing(int x, int y, int z, int reach, Material leaves, boolean roundCorners) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                boolean corner = Math.abs(dx) == reach && Math.abs(dz) == reach;
                if (corner && (roundCorners ? random.nextInt(3) != 0 : reach > 1)) {
                    continue;
                }
                if (world.getBlockAt(x + dx, y, z + dz).isEmpty()) {
                    set(x + dx, y, z + dz, leaves(leaves));
                    if (theme == Theme.FROSTSPITZE && world.getBlockAt(x + dx, y + 1, z + dz).isEmpty()) {
                        set(x + dx, y + 1, z + dz, snow(1));
                    }
                }
            }
        }
    }

    /** Grass and flowers, snow drifts or dead bushes on the open ground of an island round cx/cz. */
    private void scatter(int cx, int cz, int reach) {
        for (int x = cx - reach; x <= cx + reach; x++) {
            for (int z = cz - reach; z <= cz + reach; z++) {
                int top = surface(x, z);
                if (top < 0 || world.getBlockAt(x, top, z).getType() != theme.top() || !world.getBlockAt(x, top + 1, z).isEmpty()) {
                    continue;
                }
                int roll = random.nextInt(100);
                switch (theme) {
                    case WOLKENHAIN -> {
                        if (roll < 22) {
                            set(x, top + 1, z, Material.SHORT_GRASS);
                        } else if (roll < 27) {
                            set(x, top + 1, z, roll % 2 == 0 ? Material.DANDELION : Material.POPPY);
                        }
                    }
                    case FROSTSPITZE -> {
                        if (roll < 30) {
                            set(x, top + 1, z, snow(1 + random.nextInt(2)));
                        }
                    }
                    case WUESTENWIND -> {
                        if (roll < 6) {
                            set(x, top + 1, z, Material.DEAD_BUSH);
                        }
                    }
                }
            }
        }
    }

    /**
     * Flat white clouds in a ring from {@code inner} to {@code outer} blocks round the middle, a little above and below
     * the islands. They lie outside the border, so they are only seen, never walked on.
     */
    private void clouds(int count, int inner, int outer, int size) {
        for (int index = 0; index < count; index++) {
            double angle = random.nextDouble() * TAU;
            double distance = inner + random.nextDouble() * (outer - inner);
            int cx = (int) Math.round(Math.sin(angle) * distance);
            int cz = (int) Math.round(Math.cos(angle) * distance);
            int y = TOP - 8 + random.nextInt(16);
            int lengthX = size / 2 + random.nextInt(size);
            int lengthZ = size / 3 + random.nextInt(size / 2 + 1);
            for (int dx = -lengthX; dx <= lengthX; dx++) {
                for (int dz = -lengthZ; dz <= lengthZ; dz++) {
                    double inside = Math.pow((double) dx / lengthX, 2) + Math.pow((double) dz / lengthZ, 2);
                    if (inside > 1 + random.nextDouble() * 0.25 || Math.hypot(cx + dx, cz + dz) < inner) {
                        continue;
                    }
                    for (int dy = 0; dy < (inside < 0.35 ? 2 : 1); dy++) {
                        var block = world.getBlockAt(cx + dx, y + dy, cz + dz);
                        if (block.isEmpty()) {
                            block.setType(Material.WHITE_WOOL, false);
                        }
                    }
                }
            }
        }
    }

    /** A cell of the theme's glass round x/y/z, open again when the round starts. */
    private void cage(int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    boolean inside = dx == 0 && dz == 0 && dy < 2;
                    if (!inside) {
                        cages.add(set(x + dx, y + dy, z + dz, theme.cage()));
                    }
                }
            }
        }
    }

    private void chest(int x, int y, int z, BlockFace facing, ChestKind kind) {
        chests.get(kind).add(set(x, y, z, Material.CHEST.createBlockData(data -> ((Directional) data).setFacing(facing))));
    }

    private static BlockFace facing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        }
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** The highest block of the column at or above the island's top, or -1 if the column has no island. */
    private int surface(int x, int z) {
        if (!isGround(x, z)) {
            return -1;
        }
        int y = TOP;
        while (y < TOP + 6 && isSolidGround(world.getBlockAt(x, y + 1, z).getType())) {
            y++;
        }
        return y;
    }

    private boolean isGround(int x, int z) {
        return isSolidGround(world.getBlockAt(x, TOP, z).getType());
    }

    private boolean isSolidGround(Material material) {
        return material == theme.top() || material == theme.soil();
    }

    private Block set(int x, int y, int z, Material material) {
        var block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        return block;
    }

    private Block set(int x, int y, int z, BlockData data) {
        var block = world.getBlockAt(x, y, z);
        block.setBlockData(data, false);
        return block;
    }

    private static BlockData leaves(Material material) {
        return material.createBlockData(data -> ((Leaves) data).setPersistent(true));
    }

    private static BlockData snow(int layers) {
        return Material.SNOW.createBlockData(data -> ((Snow) data).setLayers(layers));
    }
}
