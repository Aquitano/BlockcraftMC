package me.blockcraft.zombies.arena;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds Außenposten: a walled courtyard, four wings that each tell what they were used for, scenes in the yards
 * between them, and a forest beyond the fence. A fixed seed keeps every generated copy identical.
 */
final class StarterArena {

    static final int RADIUS = 63;
    static final int HEIGHT = 12;
    private static final int FENCE = 49;
    private static final long SEED = 0x5A0B1E5L;
    private static final String BULB = "minecraft:waxed_copper_bulb[lit=false,powered=false]";
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    private final Random random = new Random(SEED);
    private final SimplexNoiseGenerator noise = new SimplexNoiseGenerator(SEED);

    private enum Wing {
        EAST, NORTH, SOUTH, WEST;

        int x(int forward, int side) {
            return switch (this) {
                case EAST -> forward;
                case NORTH -> side;
                case SOUTH -> -side;
                case WEST -> -forward;
            };
        }

        int z(int forward, int side) {
            return switch (this) {
                case EAST -> side;
                case NORTH -> -forward;
                case SOUTH -> forward;
                case WEST -> -side;
            };
        }

        static Wing toward(int dx, int dz) {
            if (Math.abs(dx) >= Math.abs(dz)) {
                return dx > 0 ? EAST : WEST;
            }
            return dz > 0 ? SOUTH : NORTH;
        }
    }

    private record Shell(int height, Material base, Material body, Material band, Material cap, Material pillar,
                         Material roof, Material parapet, Material accent) { }

    private record Area(int minX, int minZ, int maxX, int maxZ) {
        boolean near(int bx, int bz, int margin) {
            return bx >= minX - margin && bx <= maxX + margin && bz >= minZ - margin && bz <= maxZ + margin;
        }
    }

    private static final List<Area> CLEARINGS = List.of(
            new Area(50, -5, 63, 5), new Area(-63, -5, -50, 5), new Area(-9, -63, 9, -50), new Area(-3, 50, 7, 63));

    private StarterArena(ArenaMap arena, BlockBox bounds) {
        world = arena.world();
        x = (bounds.minX() + bounds.maxX()) / 2;
        y = bounds.minY() + 1;
        z = (bounds.minZ() + bounds.maxZ()) / 2;
    }

    static void build(ArenaMap arena, BlockBox bounds) {
        var builder = new StarterArena(arena, bounds);
        builder.ground();
        builder.fence();
        builder.forest();
        builder.motorPool();
        builder.tankFarm();
        builder.graveyard();
        builder.helipad();
        builder.courtyard();
        builder.depot();
        builder.workshop();
        builder.generatorHall();
        builder.clinic();
        arena.windows().forEach(builder::spawnRoom);
        arena.stations().forEach(builder::station);
        arena.notes().forEach(note -> builder.note(arena, note));
    }

    private void ground() {
        for (int bx = -RADIUS; bx <= RADIUS; bx++) {
            for (int bz = -RADIUS; bz <= RADIUS; bz++) {
                int ring = Math.max(Math.abs(bx), Math.abs(bz));
                double patch = noise.noise(bx / 9.0, bz / 9.0);
                if (ring < FENCE && (Math.abs(bx) <= 16 || Math.abs(bz) <= 16)) {
                    double wear = random.nextDouble();
                    put(bx, -1, bz, wear < 0.06 ? Material.CRACKED_STONE_BRICKS : wear < 0.1 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
                    continue;
                }
                boolean outside = ring > FENCE;
                put(bx, -1, bz, patch > 0.45 ? (outside ? Material.PODZOL : Material.COARSE_DIRT)
                        : patch < -0.5 ? Material.MOSS_BLOCK : outside && patch > 0.2 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
                double cover = random.nextDouble();
                if (cover < (outside ? 0.16 : 0.07)) {
                    put(bx, 0, bz, Material.SHORT_GRASS);
                } else if (cover < (outside ? 0.21 : 0.09)) {
                    put(bx, 0, bz, outside ? Material.FERN : Material.SHORT_DRY_GRASS);
                } else if (cover < (outside ? 0.25 : 0.1)) {
                    litter(bx, bz);
                } else if (cover < 0.26) {
                    put(bx, 0, bz, Material.DEAD_BUSH);
                }
            }
        }
    }

    private void fence() {
        var collapsed = new HashSet<Integer>();
        for (int i = 0; i < 5; i++) {
            collapsed.add(random.nextInt(4 * 25));
        }
        for (int side = 0; side < 4; side++) {
            for (int offset = -FENCE; offset <= FENCE; offset++) {
                int bx = side < 2 ? offset : side == 2 ? FENCE : -FENCE;
                int bz = side < 2 ? (side == 0 ? FENCE : -FENCE) : offset;
                String bars = side < 2 ? "minecraft:iron_bars[east=true,west=true]" : "minecraft:iron_bars[north=true,south=true]";
                put(bx, -1, bz, Material.TUFF_BRICKS);
                put(bx, 0, bz, Material.TUFF_BRICKS);
                boolean post = Math.floorMod(offset, 6) == 0;
                int segment = side * 25 + (offset + FENCE) / 4;
                if (post) {
                    fill(bx, 1, bz, bx, 4, bz, Material.POLISHED_BASALT);
                    if (Math.floorMod(offset, 18) == 0 && !collapsed.contains(segment)) {
                        put(bx, 5, bz, Material.LANTERN);
                    }
                } else if (collapsed.contains(segment)) {
                    data(bx, 1, bz, bars);
                    if (random.nextBoolean()) {
                        put(bx, 1, bz, Material.TUFF);
                    }
                } else {
                    for (int h = 1; h <= 3; h++) {
                        data(bx, h, bz, bars);
                    }
                    if (random.nextDouble() < 0.06) {
                        put(bx, 4, bz, Material.COBWEB);
                    }
                }
            }
        }
    }

    private void forest() {
        for (int cx = -RADIUS; cx <= RADIUS; cx += 5) {
            for (int cz = -RADIUS; cz <= RADIUS; cz += 5) {
                int bx = cx + random.nextInt(-2, 3);
                int bz = cz + random.nextInt(-2, 3);
                int ring = Math.max(Math.abs(bx), Math.abs(bz));
                if (ring < FENCE + 3 || CLEARINGS.stream().anyMatch(area -> area.near(bx, bz, 2))
                        || random.nextDouble() > (ring > 57 ? 0.9 : 0.6)) {
                    continue;
                }
                double kind = random.nextDouble();
                if (kind < 0.55) {
                    spruce(bx, bz, random.nextInt(7, 13));
                } else if (kind < 0.88) {
                    deadTree(bx, bz, random.nextInt(5, 10));
                } else {
                    shrub(bx, bz);
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            int bx = random.nextInt(FENCE + 2, RADIUS) * (random.nextBoolean() ? 1 : -1);
            int bz = random.nextInt(-RADIUS, RADIUS);
            boolean swap = random.nextBoolean();
            int fx = swap ? bz : bx;
            int fz = swap ? bx : bz;
            if (CLEARINGS.stream().noneMatch(area -> area.near(fx, fz, 3))) {
                boolean alongX = random.nextBoolean();
                int length = random.nextInt(3, 6);
                for (int step = 0; step < length; step++) {
                    int lx = fx + (alongX ? step : 0);
                    int lz = fz + (alongX ? 0 : step);
                    data(lx, 0, lz, "minecraft:spruce_log[axis=" + (alongX ? "x" : "z") + "]");
                    if (random.nextBoolean()) {
                        put(lx, 1, lz, Material.MOSS_CARPET);
                    }
                }
            }
        }
        road();
        truck(54, -2, true, 9, 1, Material.GREEN_TERRACOTTA, Material.LIGHT_GRAY_WOOL);
        ambulance();
        scrapHeap();
        powerLine();
    }

    private void road() {
        for (int bx = FENCE + 1; bx <= RADIUS; bx++) {
            for (int bz = -3; bz <= 3; bz++) {
                double crack = random.nextDouble();
                put(bx, -1, bz, Math.abs(bz) == 3 ? Material.COARSE_DIRT : crack < 0.12 ? Material.COBBLED_DEEPSLATE
                        : crack < 0.2 ? Material.BLACKSTONE : Material.POLISHED_BLACKSTONE);
                put(bx, 0, bz, Material.AIR);
            }
            if (bx % 4 == 0) {
                put(bx, -1, 0, Material.YELLOW_TERRACOTTA);
            }
        }
    }

    private void ambulance() {
        for (int bx = -RADIUS; bx <= -FENCE - 1; bx++) {
            for (int bz = -2; bz <= 2; bz++) {
                put(bx, -1, bz, Math.abs(bz) == 2 ? Material.STONE_BRICKS : Material.SMOOTH_STONE);
                put(bx, 0, bz, Material.AIR);
            }
        }
        truck(-54, 0, true, 7, -1, Material.WHITE_CONCRETE, Material.WHITE_CONCRETE);
        for (int bx = -60; bx <= -56; bx++) {
            put(bx, 2, -1, Material.RED_CONCRETE);
            put(bx, 2, 1, Material.RED_CONCRETE);
        }
        put(-58, 3, -1, Material.RED_STAINED_GLASS);
        put(-58, 3, 1, Material.BLUE_STAINED_GLASS);
    }

    private void scrapHeap() {
        for (int bx = -7; bx <= 7; bx++) {
            for (int bz = -61; bz <= -51; bz++) {
                double distance = Math.hypot(bx / 7.0, (bz + 56) / 5.0);
                int height = (int) Math.round((1 - distance) * 4 + random.nextDouble() - 0.5);
                for (int h = 0; h < height; h++) {
                    put(bx, h, bz, switch (random.nextInt(6)) {
                        case 0 -> Material.WAXED_EXPOSED_COPPER;
                        case 1 -> Material.COBBLED_DEEPSLATE;
                        case 2 -> Material.IRON_BLOCK;
                        case 3 -> Material.WAXED_WEATHERED_CUT_COPPER;
                        case 4 -> Material.SPRUCE_PLANKS;
                        default -> Material.TUFF;
                    });
                }
                if (height > 0 && random.nextDouble() < 0.2) {
                    data(bx, height, bz, random.nextBoolean() ? "minecraft:iron_trapdoor[half=bottom,facing=north]" : "minecraft:damaged_anvil[facing=east]");
                }
            }
        }
        put(-3, 0, -51, Material.CAULDRON);
        data(5, 0, -52, "minecraft:barrel[facing=east]");
    }

    private void powerLine() {
        for (int pole : new int[] {52, 58, 63}) {
            fill(3, 0, pole, 3, 8, pole, Material.STRIPPED_SPRUCE_LOG);
            data(2, 7, pole, "minecraft:spruce_fence[east=true]");
            data(4, 7, pole, "minecraft:spruce_fence[west=true]");
        }
        for (int bz = 44; bz <= RADIUS; bz++) {
            if (bz != 52 && bz != 58 && bz != 63) {
                data(2, 7, bz, "minecraft:iron_chain[axis=z]");
                data(4, 7, bz, "minecraft:iron_chain[axis=z]");
            }
        }
    }

    private void motorPool() {
        truck(22, -23, true, 10, 1, Material.GREEN_TERRACOTTA, Material.GREEN_WOOL);
        container(33, -35, 40, -33, 0, Material.CYAN_TERRACOTTA);
        container(33, -39, 40, -37, 0, Material.RED_TERRACOTTA);
        container(34, -37, 41, -35, 3, Material.ORANGE_TERRACOTTA);
        for (int i = 0; i < 9; i++) {
            int bx = 22 + random.nextInt(5);
            int bz = -40 + random.nextInt(4);
            data(bx, 0, bz, random.nextDouble() < 0.7 ? "minecraft:barrel[facing=up]" : "minecraft:barrel[facing=north]");
        }
        spruce(44, -21, 9);
        deadTree(26, -45, 7);
    }

    private void container(int x1, int z1, int x2, int z2, int bottom, Material color) {
        fill(x1, bottom, z1, x2, bottom + 2, z2, color);
        for (int bz = z1; bz <= z2; bz++) {
            for (int h = bottom; h <= bottom + 2; h++) {
                data(x2, h, bz, "minecraft:iron_bars[north=true,south=true]");
            }
        }
        fill(x2 - 1, bottom, z1 + 1, x2 - 1, bottom + 1, z2 - 1, Material.BARREL);
    }

    private void truck(int bx, int bz, boolean alongX, int length, int direction, Material body, Material cover) {
        for (int i = 0; i < length; i++) {
            int along = bx + (alongX ? i * direction : 0);
            int across = bz + (alongX ? 0 : i * direction);
            boolean cab = i >= length - 3;
            boolean wheel = i == 1 || i == length - 2;
            for (int side = -1; side <= 1; side++) {
                int tx = alongX ? along : bx + side;
                int tz = alongX ? bz + side : across;
                if (wheel && side != 0) {
                    put(tx, 0, tz, Material.COAL_BLOCK);
                }
                put(tx, 1, tz, body);
                if (cab) {
                    put(tx, 2, tz, i == length - 1 ? Material.GLASS : body);
                    if (i < length - 1) {
                        put(tx, 3, tz, body);
                    }
                } else {
                    put(tx, 2, tz, body);
                    put(tx, 3, tz, cover);
                }
            }
        }
    }

    private void tankFarm() {
        tank(30, 30, 4, 6);
        tank(41, 21, 3, 5);
        for (int bx = 17; bx <= 25; bx++) {
            data(bx, 2, 30, "minecraft:waxed_lightning_rod[facing=west]");
            if (bx % 3 == 0) {
                fill(bx, 0, 30, bx, 1, 30, Material.DEEPSLATE_BRICK_WALL);
            }
        }
        for (int i = 0; i < 6; i++) {
            data(36 + random.nextInt(4), 0, 36 + random.nextInt(5), "minecraft:barrel[facing=up]");
        }
        deadTree(22, 44, 8);
        spruce(45, 40, 10);
    }

    private void tank(int cx, int cz, int radius, int height) {
        for (int bx = cx - radius - 1; bx <= cx + radius + 1; bx++) {
            for (int bz = cz - radius - 1; bz <= cz + radius + 1; bz++) {
                double distance = Math.hypot(bx - cx, bz - cz);
                if (distance <= radius + 1.5 && distance > radius + 0.5) {
                    put(bx, -1, bz, (bx + bz) % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
                }
                if (distance > radius + 0.5) {
                    continue;
                }
                for (int h = 0; h <= height; h++) {
                    put(bx, h, bz, h == 3 ? Material.YELLOW_TERRACOTTA : Material.LIGHT_GRAY_CONCRETE);
                }
                put(bx, height + 1, bz, distance > radius - 0.5 ? Material.SMOOTH_STONE_SLAB : Material.SMOOTH_STONE);
            }
        }
        for (int h = 0; h <= height; h++) {
            data(cx, h, cz + radius + 1, "minecraft:ladder[facing=south]");
        }
    }

    private void graveyard() {
        var cells = new ArrayList<int[]>();
        for (int bx = -44; bx <= -21; bx++) {
            for (int bz = 21; bz <= 41; bz++) {
                boolean edge = bx == -44 || bx == -21 || bz == 21 || bz == 41;
                if (edge && !(bz == 21 && Math.abs(bx + 32) <= 1) && random.nextDouble() > 0.15) {
                    cells.add(new int[] {bx, bz});
                }
            }
        }
        connected("minecraft:spruce_fence", 0, cells);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 5; column++) {
                int gx = -40 + column * 4 + random.nextInt(2);
                int gz = 25 + row * 5;
                put(gx, -1, gz, Material.COARSE_DIRT);
                put(gx, -1, gz + 1, Material.COARSE_DIRT);
                put(gx, -1, gz + 2, Material.ROOTED_DIRT);
                put(gx, 0, gz, Material.AIR);
                put(gx, 0, gz + 1, Material.AIR);
                put(gx, 0, gz + 2, Material.AIR);
                if (random.nextDouble() < 0.35) {
                    put(gx, 0, gz, Material.SPRUCE_FENCE);
                    data(gx, 1, gz, "minecraft:spruce_fence[east=true,west=true]");
                    data(gx - 1, 1, gz, "minecraft:spruce_fence[east=true]");
                    data(gx + 1, 1, gz, "minecraft:spruce_fence[west=true]");
                    put(gx, 2, gz, Material.SPRUCE_FENCE);
                } else {
                    put(gx, 0, gz, switch (random.nextInt(3)) {
                        case 0 -> Material.COBBLESTONE_WALL;
                        case 1 -> Material.MOSSY_COBBLESTONE_WALL;
                        default -> Material.STONE_BRICK_WALL;
                    });
                }
                if (random.nextDouble() < 0.4) {
                    data(gx, 0, gz + 1, "minecraft:candle[lit=true,candles=" + random.nextInt(1, 4) + "]");
                } else if (random.nextDouble() < 0.4) {
                    put(gx, 0, gz + 2, Material.DEAD_BUSH);
                }
            }
        }
        deadTree(-24, 38, 7);
        deadTree(-46, 19, 6);
        deadTree(-36, 44, 9);
    }

    private void helipad() {
        int cx = -32;
        int cz = -32;
        for (int bx = cx - 7; bx <= cx + 7; bx++) {
            for (int bz = cz - 7; bz <= cz + 7; bz++) {
                double distance = Math.hypot(bx - cx, bz - cz);
                if (distance <= 6.5) {
                    put(bx, -1, bz, distance > 5.5 ? Material.WHITE_CONCRETE : Material.GRAY_CONCRETE);
                    put(bx, 0, bz, Material.AIR);
                }
            }
        }
        fill(cx - 2, -1, cz - 3, cx - 2, -1, cz + 3, Material.YELLOW_CONCRETE);
        fill(cx + 2, -1, cz - 3, cx + 2, -1, cz + 3, Material.YELLOW_CONCRETE);
        fill(cx - 1, -1, cz, cx + 1, -1, cz, Material.YELLOW_CONCRETE);
        for (int i = 0; i < 40; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = random.nextDouble() * 5;
            put(-25 + (int) Math.round(Math.cos(angle) * distance), -1, -38 + (int) Math.round(Math.sin(angle) * distance),
                    random.nextBoolean() ? Material.BLACKSTONE : Material.COAL_BLOCK);
        }
        fill(-28, 0, -38, -23, 2, -36, Material.GREEN_TERRACOTTA);
        fill(-28, 1, -37, -28, 2, -37, Material.GLASS);
        fill(-26, 1, -44, -26, 1, -39, Material.GREEN_TERRACOTTA);
        put(-26, 2, -44, Material.GREEN_TERRACOTTA);
        data(-25, 3, -37, "minecraft:campfire[lit=true,signal_fire=true]");
        for (int bx = -33; bx <= -20; bx++) {
            data(bx, 0, -41, "minecraft:iron_trapdoor[half=bottom,facing=north]");
        }
        for (int bz = -47; bz <= -42; bz++) {
            data(-21 - (bz + 47) / 2, 0, bz, "minecraft:iron_trapdoor[half=bottom,facing=east]");
        }
        data(-40, 0, -24, "minecraft:barrel[facing=up]");
        put(-40, 1, -24, Material.COMPOSTER);
        for (int bx = -43; bx <= -37; bx++) {
            for (int bz = -27; bz <= -21; bz++) {
                if ((bx != -40 || bz != -24) && Math.hypot(bx + 40, bz + 24) < 3.6 && random.nextDouble() < 0.7) {
                    put(bx, 0, bz, Material.WHITE_CARPET);
                }
            }
        }
        for (int bz = -30; bz <= -24; bz++) {
            put(-19, 0, bz, Material.PACKED_MUD);
            put(-19, 1, bz, Material.MUD_BRICK_SLAB);
        }
        spruce(-21, -46, 8);
        deadTree(-45, -44, 8);
    }

    private void courtyard() {
        for (int bx = -15; bx <= 15; bx++) {
            for (int bz = -15; bz <= 15; bz++) {
                if (Math.abs(bx) <= 2 || Math.abs(bz) <= 2) {
                    put(bx, -1, bz, random.nextDouble() < 0.12 ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE);
                }
                if ((Math.abs(bx) == 3 && Math.abs(bz) <= 12 || Math.abs(bz) == 3 && Math.abs(bx) <= 12) && random.nextDouble() > 0.2) {
                    put(bx, -1, bz, Material.WAXED_WEATHERED_CUT_COPPER);
                }
            }
        }
        fill(-2, -1, -2, 2, -1, 2, Material.WAXED_OXIDIZED_CUT_COPPER);
        fill(-3, 0, 3, -3, 7, 3, Material.DARK_OAK_FENCE);
        data(-4, 6, 3, "minecraft:gray_wall_banner[facing=west]");
        armoryKiosk();
        armorKiosk();
        radioShelter();
        collapsedShelter();
        campfire();
        watchtower();
        for (int[] lamp : new int[][] {{-3, -8}, {3, 8}, {13, -4}}) {
            fill(lamp[0], 0, lamp[1], lamp[0], 3, lamp[1], Material.DEEPSLATE_BRICK_WALL);
            data(lamp[0], 4, lamp[1], BULB);
            data(lamp[0], 5, lamp[1], "minecraft:polished_andesite_slab[type=bottom]");
        }
        fill(12, 0, 12, 14, 0, 14, Material.TUFF_BRICKS);
        fill(13, 0, 13, 14, 0, 14, Material.MOSS_BLOCK);
        data(13, 1, 13, "minecraft:azalea_leaves[persistent=true]");
        data(14, 1, 14, "minecraft:azalea_leaves[persistent=true]");
        put(14, 1, 13, Material.FLOWERING_AZALEA);
        put(13, 1, 14, Material.FIREFLY_BUSH);
        for (int h = 1; h <= 5; h++) {
            data(15, h, 13, "minecraft:vine[east=true]");
            if (h <= 3) {
                data(15, h, 14, "minecraft:vine[east=true]");
            }
        }
        fill(-14, 0, -14, -12, 0, -12, Material.TUFF_BRICKS);
        fill(-13, 0, -13, -13, 0, -13, Material.COARSE_DIRT);
        put(-13, 1, -13, Material.DEAD_BUSH);
        put(-12, 1, -14, Material.SHORT_DRY_GRASS);
        for (int bx = -15; bx <= 15; bx++) {
            for (int bz = -15; bz <= 15; bz++) {
                if ((bx != 0 || bz != 0) && random.nextDouble() < 0.035 && isAir(bx, 0, bz)) {
                    litter(bx, bz);
                }
            }
        }
    }

    private void armoryKiosk() {
        fill(-6, -1, 11, -3, -1, 14, Material.POLISHED_ANDESITE);
        fill(-6, 0, 14, -3, 2, 14, Material.TUFF_BRICKS);
        fill(-6, 0, 12, -6, 2, 13, Material.TUFF_BRICKS);
        fill(-6, 0, 11, -6, 3, 11, Material.DARK_OAK_FENCE);
        fill(-2, 0, 11, -2, 3, 11, Material.DARK_OAK_FENCE);
        fill(-6, 3, 12, -6, 3, 14, Material.POLISHED_ANDESITE_SLAB);
        fill(-6, 4, 11, -2, 4, 14, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        data(-5, 1, 13, "minecraft:spruce_trapdoor[open=true,facing=north]");
        data(-3, 1, 13, "minecraft:spruce_trapdoor[open=true,facing=north]");
        data(-5, 0, 13, "minecraft:barrel[facing=up]");
        data(-3, 0, 13, "minecraft:spruce_slab[type=top]");
        put(-5, 3, 13, Material.COBWEB);
    }

    private void armorKiosk() {
        fill(3, -1, 11, 7, -1, 14, Material.SPRUCE_PLANKS);
        fill(3, 0, 14, 7, 2, 14, Material.STRIPPED_SPRUCE_WOOD);
        fill(7, 0, 12, 7, 2, 13, Material.STRIPPED_SPRUCE_WOOD);
        fill(2, 0, 11, 2, 3, 11, Material.SPRUCE_FENCE);
        fill(7, 0, 11, 7, 3, 11, Material.SPRUCE_FENCE);
        fill(2, 4, 11, 7, 4, 14, Material.LIGHT_GRAY_WOOL);
        data(6, 0, 13, "minecraft:anvil[facing=east]");
        data(5, 0, 13, "minecraft:barrel[facing=up]");
        put(6, 1, 13, Material.IRON_TRAPDOOR);
    }

    private void radioShelter() {
        fill(6, 0, 7, 10, 2, 7, Material.TUFF_BRICKS);
        fill(10, 0, 3, 10, 2, 7, Material.TUFF_BRICKS);
        fill(6, 3, 7, 10, 3, 7, Material.POLISHED_ANDESITE_SLAB);
        fill(10, 3, 3, 10, 3, 7, Material.POLISHED_ANDESITE_SLAB);
        fill(6, 4, 3, 10, 4, 7, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        fill(6, 0, 3, 6, 3, 3, Material.DARK_OAK_FENCE);
        data(8, 0, 6, "minecraft:spruce_slab[type=top]");
        data(9, 0, 6, "minecraft:spruce_slab[type=top]");
        put(9, 1, 6, Material.JUKEBOX);
        put(8, 1, 6, Material.LANTERN);
        data(9, 0, 5, "minecraft:spruce_stairs[facing=south]");
        fill(10, 5, 7, 10, 10, 7, Material.IRON_BARS);
        data(10, 11, 7, "minecraft:waxed_lightning_rod[facing=up]");
        data(9, 9, 7, "minecraft:iron_bars[east=true]");
        data(11, 9, 7, "minecraft:iron_bars[west=true]");
    }

    private void collapsedShelter() {
        for (int bx = 6; bx <= 10; bx++) {
            int top = bx >= 9 ? 1 : 2;
            fill(bx, 0, -10, bx, top, -10, random.nextBoolean() ? Material.TUFF_BRICKS : Material.CRACKED_STONE_BRICKS);
        }
        fill(6, 0, -5, 6, 3, -5, Material.DARK_OAK_FENCE);
        fill(6, 4, -9, 8, 4, -5, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        for (int bx = 10; bx <= 12; bx++) {
            for (int bz = -9; bz <= -4; bz++) {
                if (random.nextDouble() < 0.65) {
                    put(bx, 0, bz, switch (random.nextInt(4)) {
                        case 0 -> Material.WAXED_WEATHERED_CUT_COPPER_SLAB;
                        case 1 -> Material.COBBLESTONE;
                        case 2 -> Material.TUFF_BRICK_SLAB;
                        default -> Material.COBBLED_DEEPSLATE_SLAB;
                    });
                }
            }
        }
        put(11, 1, -7, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        data(8, 0, -8, "minecraft:barrel[facing=east]");
        data(7, 0, -9, "minecraft:barrel[facing=up]");
    }

    private void campfire() {
        data(-10, 0, 6, "minecraft:campfire[lit=true,signal_fire=false]");
        data(-12, 0, 6, "minecraft:stripped_spruce_log[axis=z]");
        data(-8, 0, 6, "minecraft:stripped_spruce_log[axis=z]");
        data(-10, 0, 8, "minecraft:stripped_spruce_log[axis=x]");
        for (int bz = 3; bz <= 9; bz++) {
            put(-14, 0, bz, Material.PACKED_MUD);
            put(-14, 1, bz, Material.MUD_BRICK_SLAB);
        }
        fill(-13, 0, 3, -11, 0, 3, Material.PACKED_MUD);
        fill(-13, 1, 3, -11, 1, 3, Material.MUD_BRICK_SLAB);
        data(-13, 0, 9, "minecraft:barrel[facing=up]");
        put(-13, 1, 9, Material.COMPOSTER);
        data(-12, 0, 9, "minecraft:barrel[facing=up]");
        put(-11, 0, 4, Material.CAULDRON);
    }

    private void watchtower() {
        for (int[] leg : new int[][] {{-10, -14}, {-7, -14}, {-10, -11}, {-7, -11}}) {
            fill(leg[0], 0, leg[1], leg[0], 7, leg[1], Material.STRIPPED_SPRUCE_LOG);
            fill(leg[0], 9, leg[1], leg[0], 10, leg[1], Material.SPRUCE_FENCE);
        }
        for (int bx = -9; bx <= -8; bx++) {
            data(bx, 3, -14, "minecraft:stripped_spruce_log[axis=x]");
            data(bx, 3, -11, "minecraft:stripped_spruce_log[axis=x]");
        }
        for (int bz = -13; bz <= -12; bz++) {
            data(-10, 3, bz, "minecraft:stripped_spruce_log[axis=z]");
            data(-7, 3, bz, "minecraft:stripped_spruce_log[axis=z]");
        }
        fill(-10, 8, -14, -7, 8, -11, Material.SPRUCE_PLANKS);
        var railing = new ArrayList<int[]>();
        for (int bx = -9; bx <= -8; bx++) {
            railing.add(new int[] {bx, -14});
            railing.add(new int[] {bx, -11});
        }
        for (int bz = -13; bz <= -12; bz++) {
            railing.add(new int[] {-10, bz});
            railing.add(new int[] {-7, bz});
        }
        connected("minecraft:spruce_fence", 9, railing);
        fill(-11, 11, -15, -6, 11, -10, Material.DARK_OAK_SLAB);
        data(-9, 10, -12, BULB);
        data(-8, 7, -13, "minecraft:lantern[hanging=true]");
        data(-9, 0, -13, "minecraft:barrel[facing=up]");
        data(-8, 0, -12, "minecraft:barrel[facing=north]");
    }

    private Shell shell(Wing wing, Shell style) {
        int top = style.height();
        for (int u = 16; u <= 43; u++) {
            for (int v = -16; v <= 16; v++) {
                local(wing, u, top, v, style.roof());
                for (int h = 0; h < top; h++) {
                    local(wing, u, h, v, Material.AIR);
                }
                if (v == -16 || v == 16 || u == 16 || u == 43) {
                    for (int h = 0; h < top; h++) {
                        local(wing, u, h, v, h == 0 ? style.base() : h == top - 1 ? style.cap() : h == top - 2 ? style.band() : style.body());
                    }
                    local(wing, u, top + 1, v, style.parapet());
                }
                if (Math.abs(v) == 15 && u > 17 && u < 43) {
                    local(wing, u, 1, v, style.accent());
                }
            }
        }
        for (int u : new int[] {16, 25, 34, 43}) {
            for (int v : new int[] {-16, 16}) {
                for (int h = 0; h <= top; h++) {
                    local(wing, u, h, v, style.pillar());
                }
            }
        }
        for (int v = -2; v <= 2; v++) {
            for (int h = 0; h <= 2; h++) {
                local(wing, 16, h, v, Material.WAXED_CUT_COPPER);
            }
        }
        return style;
    }

    private void floor(Wing wing, Material floor, Material lane, Material stain) {
        for (int u = 17; u <= 42; u++) {
            for (int v = -15; v <= 15; v++) {
                local(wing, u, -1, v, random.nextDouble() < 0.04 ? stain : Math.abs(v) <= 2 ? lane : floor);
            }
        }
    }

    private void windows(Wing wing, int[] starts, int width, int bottom, int top, Material glass, String pane) {
        for (int start : starts) {
            for (int side : new int[] {-16, 16}) {
                for (int u = start; u < start + width; u++) {
                    for (int h = bottom; h <= top; h++) {
                        if (random.nextDouble() < 0.12) {
                            localData(wing, u, h, side, pane);
                        } else {
                            local(wing, u, h, side, glass);
                        }
                    }
                }
            }
        }
    }

    private void cobwebs(Wing wing, int height) {
        for (int i = 0; i < 5; i++) {
            int u = random.nextBoolean() ? 17 + random.nextInt(2) : 41 + random.nextInt(2);
            int v = random.nextBoolean() ? -15 : 15;
            local(wing, u, height - 1 - random.nextInt(2), v, Material.COBWEB);
        }
    }

    private void depot() {
        var wing = Wing.EAST;
        var style = shell(wing, new Shell(7, Material.POLISHED_TUFF, Material.TUFF_BRICKS, Material.POLISHED_TUFF,
                Material.WAXED_WEATHERED_CUT_COPPER, Material.POLISHED_BASALT, Material.DEEPSLATE_TILES,
                Material.DEEPSLATE_TILE_SLAB, Material.CYAN_TERRACOTTA));
        floor(wing, Material.POLISHED_ANDESITE, Material.SMOOTH_STONE, Material.ANDESITE);
        for (int u = 21; u <= 38; u++) {
            for (int v = -3; v <= 3; v++) {
                local(wing, u, style.height(), v, Material.LIGHT_GRAY_STAINED_GLASS);
            }
        }
        windows(wing, new int[] {19, 27, 36}, 5, 4, 5, Material.GRAY_STAINED_GLASS, "minecraft:gray_stained_glass_pane");
        doorFrame(wing, Material.POLISHED_BASALT, Material.CYAN_TERRACOTTA, Material.WAXED_WEATHERED_CUT_COPPER_SLAB);
        rack(wing, 24, 34, -6);
        rack(wing, 27, 36, 6);
        for (int u = 30; u <= 31; u++) {
            for (int v = -2; v <= -1; v++) {
                local(wing, u, 0, v, Material.YELLOW_TERRACOTTA);
                local(wing, u, 1, v, u == 30 ? Material.YELLOW_TERRACOTTA : Material.AIR);
            }
        }
        localData(wing, 31, 1, -2, "minecraft:spruce_stairs[facing=" + face(wing, -1, 0) + "]");
        for (int v = -2; v <= -1; v++) {
            for (int h = 0; h <= 3; h++) {
                local(wing, 32, h, v, Material.IRON_BARS);
            }
            localData(wing, 33, 0, v, "minecraft:iron_trapdoor[half=bottom,facing=" + face(wing, 1, 0) + "]");
        }
        for (int v = -15; v <= 15; v++) {
            local(wing, 38, 6, v, Material.WAXED_WEATHERED_CUT_COPPER);
        }
        localData(wing, 38, 5, 3, "minecraft:iron_chain[axis=y]");
        localData(wing, 38, 4, 3, "minecraft:iron_chain[axis=y]");
        localData(wing, 38, 3, 3, "minecraft:waxed_lightning_rod[facing=down]");
        for (int u = 26; u <= 37; u++) {
            localData(wing, u, 0, 13, "minecraft:rail[shape=" + (axis(wing) == 'x' ? "east_west" : "north_south") + "]");
        }
        for (int v = 5; v <= 7; v++) {
            crate(wing, 39, 0, v);
            if (v != 7) {
                crate(wing, 39, 1, v);
            }
        }
        for (int u = 22; u <= 23; u++) {
            for (int v = -13; v <= -12; v++) {
                crate(wing, u, 0, v);
            }
        }
        crate(wing, 22, 1, -13);
        for (int u : new int[] {18, 19, 26, 27}) {
            localData(wing, u, 0, -15, "minecraft:barrel[facing=up]");
        }
        for (int u = 35; u <= 37; u++) {
            crate(wing, u, 0, -13);
            crate(wing, u, 0, -12);
        }
        localData(wing, 40, 0, 14, "minecraft:barrel[facing=up,open=true]");
        localData(wing, 39, 0, 14, "minecraft:spruce_slab[type=bottom]");
        localData(wing, 40, 0, 13, "minecraft:spruce_trapdoor[half=bottom,facing=" + face(wing, 0, 1) + "]");
        local(wing, 41, 0, 14, Material.COBWEB);
        for (int[] bulb : new int[][] {{21, -10}, {29, -10}, {37, -12}, {29, 0}, {33, 10}, {41, 4}, {21, 0}}) {
            localData(wing, bulb[0], 6, bulb[1], BULB);
        }
        hangingLantern(wing, 24, 4, -12, style.height());
        hangingLantern(wing, 40, 4, -8, style.height());
        cobwebs(wing, style.height());
        for (int[] unit : new int[][] {{24, -10}, {34, 10}}) {
            for (int u = unit[0]; u <= unit[0] + 1; u++) {
                for (int v = unit[1]; v <= unit[1] + 1; v++) {
                    local(wing, u, 8, v, Material.IRON_BLOCK);
                    local(wing, u, 9, v, Material.WAXED_COPPER_GRATE);
                }
            }
        }
    }

    private void rack(Wing wing, int from, int to, int v) {
        for (int u = from; u <= to; u++) {
            boolean post = u == from || u == to || (u - from) % 4 == 0;
            if (post) {
                for (int h = 0; h <= 4; h++) {
                    local(wing, u, h, v, Material.STRIPPED_SPRUCE_LOG);
                }
                continue;
            }
            for (int shelf : new int[] {0, 2}) {
                if (random.nextDouble() < 0.75) {
                    crate(wing, u, shelf, v);
                }
            }
            localData(wing, u, 1, v, "minecraft:spruce_slab[type=bottom]");
            localData(wing, u, 3, v, "minecraft:spruce_slab[type=bottom]");
            if (random.nextDouble() < 0.4) {
                crate(wing, u, 4, v);
            }
        }
    }

    private void crate(Wing wing, int u, int h, int v) {
        switch (random.nextInt(4)) {
            case 0 -> localData(wing, u, h, v, "minecraft:barrel[facing=up]");
            case 1 -> local(wing, u, h, v, Material.COMPOSTER);
            case 2 -> local(wing, u, h, v, Material.STRIPPED_SPRUCE_WOOD);
            default -> localData(wing, u, h, v, "minecraft:barrel[facing=" + face(wing, 1, 0) + "]");
        }
    }

    private void workshop() {
        var wing = Wing.NORTH;
        var style = shell(wing, new Shell(7, Material.POLISHED_TUFF, Material.BRICKS, Material.POLISHED_TUFF,
                Material.WAXED_EXPOSED_CUT_COPPER, Material.STRIPPED_DARK_OAK_LOG, Material.DEEPSLATE_TILES,
                Material.DEEPSLATE_TILE_SLAB, Material.TERRACOTTA));
        floor(wing, Material.POLISHED_ANDESITE, Material.STONE_BRICKS, Material.BLACK_CONCRETE);
        for (int u = 17; u <= 42; u++) {
            int phase = (u - 17) % 6;
            for (int v = -15; v <= 15; v++) {
                if (phase == 0) {
                    local(wing, u, 7, v, Material.LIGHT_GRAY_STAINED_GLASS);
                    local(wing, u, 8, v, Material.LIGHT_GRAY_STAINED_GLASS);
                    localData(wing, u, 9, v, "minecraft:deepslate_tile_slab[type=bottom]");
                } else if (phase == 1) {
                    localData(wing, u, 8, v, "minecraft:deepslate_tile_stairs[facing=" + face(wing, -1, 0) + "]");
                } else if (phase == 2) {
                    localData(wing, u, 8, v, "minecraft:deepslate_tile_slab[type=bottom]");
                }
            }
        }
        windows(wing, new int[] {19, 28, 37}, 5, 2, 3, Material.GRAY_STAINED_GLASS, "minecraft:gray_stained_glass_pane");
        doorFrame(wing, Material.STRIPPED_DARK_OAK_LOG, Material.TERRACOTTA, Material.SPRUCE_SLAB);
        for (int u = 26; u <= 33; u++) {
            for (int v = -2; v <= 2; v++) {
                localData(wing, u, 0, v, "minecraft:iron_trapdoor[half=bottom,facing=" + face(wing, 1, 0) + "]");
            }
        }
        for (int u : new int[] {26, 33}) {
            for (int v : new int[] {-3, 3}) {
                for (int h = 0; h <= 2; h++) {
                    local(wing, u, h, v, Material.IRON_BARS);
                }
            }
        }
        for (int u = 27; u <= 32; u++) {
            for (int v = -1; v <= 1; v++) {
                local(wing, u, 1, v, Material.GREEN_TERRACOTTA);
            }
        }
        for (int u : new int[] {27, 32}) {
            for (int v : new int[] {-2, 2}) {
                local(wing, u, 1, v, Material.COAL_BLOCK);
            }
        }
        for (int v = -1; v <= 1; v++) {
            localData(wing, 30, 2, v, "minecraft:glass_pane[" + paneSides(wing) + "]");
            localData(wing, 28, 2, v, "minecraft:dark_oak_slab[type=bottom]");
        }
        local(wing, 26, 2, 0, Material.COAL_BLOCK);
        localData(wing, 32, 1, -1, BULB);
        localData(wing, 32, 1, 1, BULB);
        String toward = face(wing, 0, 1);
        for (int u = 22; u <= 34; u++) {
            localData(wing, u, 0, -14, "minecraft:spruce_slab[type=top]");
            if (u % 3 == 0) {
                localData(wing, u, 0, -15, "minecraft:barrel[facing=up]");
            }
        }
        localData(wing, 23, 1, -14, "minecraft:grindstone[face=floor,facing=" + toward + "]");
        local(wing, 25, 1, -14, Material.SMITHING_TABLE);
        local(wing, 27, 1, -14, Material.LANTERN);
        local(wing, 29, 1, -14, Material.CRAFTER);
        localData(wing, 31, 1, -14, "minecraft:stonecutter[facing=" + toward + "]");
        localData(wing, 33, 1, -14, "minecraft:chipped_anvil[facing=" + face(wing, 1, 0) + "]");
        localData(wing, 37, 0, 10, "minecraft:anvil[facing=" + face(wing, 1, 0) + "]");
        local(wing, 36, 0, 11, Material.SMITHING_TABLE);
        localData(wing, 37, 0, 11, "minecraft:barrel[facing=up]");
        for (int v = 6; v <= 13; v++) {
            for (int h = 0; h <= 3; h++) {
                local(wing, 29, h, v, h == 3 ? Material.POLISHED_ANDESITE_SLAB : h == 2 && v % 2 == 0 ? Material.WHITE_STAINED_GLASS : Material.TUFF_BRICKS);
            }
        }
        local(wing, 18, 0, 14, Material.COAL_BLOCK);
        local(wing, 18, 1, 14, Material.COAL_BLOCK);
        local(wing, 19, 0, 14, Material.COAL_BLOCK);
        local(wing, 18, 0, 13, Material.COAL_BLOCK);
        for (int u = 39; u <= 41; u++) {
            for (int v = 5; v <= 7; v++) {
                if (random.nextDouble() < 0.7) {
                    switch (random.nextInt(4)) {
                        case 0 -> local(wing, u, 0, v, Material.CAULDRON);
                        case 1 -> local(wing, u, 0, v, Material.WAXED_EXPOSED_COPPER);
                        case 2 -> localData(wing, u, 0, v, "minecraft:iron_trapdoor[half=bottom,facing=" + toward + "]");
                        default -> localData(wing, u, 0, v, "minecraft:iron_chain[axis=" + axis(wing) + "]");
                    }
                }
            }
        }
        for (int[] bulb : new int[][] {{21, -8}, {28, -8}, {35, -8}, {28, 6}, {35, 0}, {40, 8}, {22, 0}}) {
            localData(wing, bulb[0], 6, bulb[1], BULB);
        }
        hangingLantern(wing, 38, 4, -6, style.height());
        cobwebs(wing, style.height());
    }

    private void generatorHall() {
        var wing = Wing.SOUTH;
        var style = shell(wing, new Shell(7, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_BRICKS, Material.POLISHED_DEEPSLATE,
                Material.WAXED_OXIDIZED_CUT_COPPER, Material.POLISHED_BASALT, Material.DEEPSLATE_TILES,
                Material.POLISHED_DEEPSLATE_SLAB, Material.YELLOW_TERRACOTTA));
        floor(wing, Material.POLISHED_DEEPSLATE, Material.DEEPSLATE_TILES, Material.CRACKED_DEEPSLATE_TILES);
        windows(wing, new int[] {21, 30, 39}, 1, 4, 4, Material.GRAY_STAINED_GLASS, "minecraft:gray_stained_glass_pane");
        for (int v = -3; v <= 3; v++) {
            for (int h = 0; h <= 3; h++) {
                if (Math.abs(v) == 3 || h == 3) {
                    local(wing, 16, h, v, (h + v) % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
                }
            }
        }
        for (int u = 26; u <= 34; u++) {
            for (int v = -4; v <= 4; v++) {
                if (u == 26 || u == 34 || Math.abs(v) == 4) {
                    local(wing, u, -1, v, (u + v) % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
                }
            }
        }
        for (int u = 27; u <= 33; u++) {
            for (int v = -3; v <= 3; v++) {
                local(wing, u, 0, v, Material.POLISHED_BLACKSTONE);
                for (int h = 1; h <= 3; h++) {
                    local(wing, u, h, v, h == 2 ? Material.WAXED_CUT_COPPER : Material.WAXED_COPPER_BLOCK);
                }
                local(wing, u, 4, v, Math.abs(v) <= 2 && u > 27 && u < 33 ? Material.WAXED_COPPER_GRATE : Material.POLISHED_BLACKSTONE_SLAB);
            }
        }
        for (int u = 29; u <= 31; u++) {
            for (int side : new int[] {-1, 1}) {
                local(wing, u, 2, side * 3, Material.ORANGE_STAINED_GLASS);
                local(wing, u, 2, side * 2, Material.MAGMA_BLOCK);
            }
        }
        for (int u : new int[] {27, 33}) {
            for (int v : new int[] {-3, 3}) {
                localData(wing, u, 5, v, "minecraft:waxed_lightning_rod[facing=up]");
            }
        }
        for (int h = 5; h <= 6; h++) {
            local(wing, 30, h, 0, Material.WAXED_COPPER_GRATE);
        }
        String pipe = "minecraft:waxed_lightning_rod[facing=" + face(wing, 0, 1) + "]";
        for (int v = -15; v <= 15; v++) {
            if (Math.abs(v) > 3) {
                localData(wing, 30, 5, v, pipe);
            }
        }
        cage(wing, 36, -14, 40, -12);
        cage(wing, 25, -14, 28, -12);
        for (int h = 0; h <= 11; h++) {
            for (int u = 40; u <= 41; u++) {
                for (int v = 12; v <= 13; v++) {
                    local(wing, u, h, v, Material.BRICKS);
                }
            }
        }
        localData(wing, 40, 12, 12, "minecraft:campfire[lit=true,signal_fire=true]");
        for (int u = 34; u <= 38; u++) {
            for (int h = 0; h <= 2; h++) {
                local(wing, u, h, 15, h == 1 && u % 2 == 0 ? Material.WAXED_COPPER_GRATE : Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        localData(wing, 36, 2, 15, BULB);
        for (int u = 19; u <= 22; u++) {
            localData(wing, u, 0, -14, "minecraft:spruce_slab[type=top]");
        }
        local(wing, 19, 1, -14, Material.LANTERN);
        for (int[] bulb : new int[][] {{21, -8}, {29, -8}, {29, 8}, {37, -6}, {37, 4}, {41, -10}, {21, 0}}) {
            localData(wing, bulb[0], 6, bulb[1], BULB);
        }
        hangingLantern(wing, 24, 4, -9, style.height());
        cobwebs(wing, style.height());
        for (int u = 25; u <= 27; u++) {
            local(wing, u, 8, 8, Material.WAXED_COPPER_GRATE);
        }
    }

    private void cage(Wing wing, int u1, int v1, int u2, int v2) {
        var cells = new ArrayList<int[]>();
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                if (u == u1 || u == u2 || v == v1 || v == v2) {
                    cells.add(new int[] {wing.x(u, v), wing.z(u, v)});
                } else {
                    local(wing, u, 0, v, Material.WAXED_EXPOSED_COPPER);
                    local(wing, u, 1, v, Material.WAXED_EXPOSED_COPPER);
                    localData(wing, u, 2, v, "minecraft:waxed_lightning_rod[facing=up]");
                }
            }
        }
        for (int h = 0; h <= 2; h++) {
            connected("minecraft:iron_bars", h, cells);
        }
    }

    private void clinic() {
        var wing = Wing.WEST;
        var style = shell(wing, new Shell(8, Material.POLISHED_DIORITE, Material.CALCITE, Material.POLISHED_DIORITE,
                Material.WAXED_WEATHERED_CUT_COPPER, Material.QUARTZ_PILLAR, Material.LIGHT_GRAY_CONCRETE,
                Material.SMOOTH_STONE_SLAB, Material.LIGHT_BLUE_TERRACOTTA));
        for (int u = 17; u <= 42; u++) {
            for (int v = -15; v <= 15; v++) {
                local(wing, u, -1, v, random.nextDouble() < 0.04 ? Material.CRACKED_STONE_BRICKS
                        : (u + v) % 2 == 0 ? Material.POLISHED_DIORITE : Material.SMOOTH_STONE);
            }
        }
        windows(wing, new int[] {19, 27, 35, 39}, 3, 2, 4, Material.LIGHT_GRAY_STAINED_GLASS, "minecraft:light_gray_stained_glass_pane");
        doorFrame(wing, Material.QUARTZ_PILLAR, Material.LIGHT_BLUE_TERRACOTTA, Material.SMOOTH_STONE_SLAB);
        local(wing, 16, 5, 0, Material.RED_TERRACOTTA);
        local(wing, 16, 6, -1, Material.RED_TERRACOTTA);
        local(wing, 16, 6, 0, Material.RED_TERRACOTTA);
        local(wing, 16, 6, 1, Material.RED_TERRACOTTA);
        local(wing, 16, 7, 0, Material.RED_TERRACOTTA);
        for (int u = 27; u <= 33; u++) {
            for (int v = -1; v <= 1; v++) {
                local(wing, u, 8, v, Material.RED_CONCRETE);
            }
        }
        for (int u = 29; u <= 31; u++) {
            for (int v = -3; v <= 3; v++) {
                local(wing, u, 8, v, Material.RED_CONCRETE);
            }
        }
        for (int u : new int[] {19, 22, 25, 28, 31}) {
            for (int v = -15; v <= -12; v++) {
                for (int h = 0; h <= 2; h++) {
                    local(wing, u, h, v, Material.WHITE_WOOL);
                }
            }
        }
        for (int u : new int[] {20, 23, 26, 29}) {
            bed(wing, u, -13, -1);
            local(wing, u + 1, 0, -15, u % 2 == 0 ? Material.CAULDRON : Material.FLOWER_POT);
        }
        for (int u : new int[] {26, 29, 32}) {
            for (int v = 12; v <= 15; v++) {
                for (int h = 0; h <= 2; h++) {
                    local(wing, u, h, v, Material.WHITE_WOOL);
                }
            }
        }
        for (int u : new int[] {27, 30, 33}) {
            bed(wing, u, 13, 1);
        }
        for (int u = 28; u <= 34; u++) {
            for (int v = -4; v <= 4; v++) {
                boolean wall = u == 28 || u == 34 || Math.abs(v) == 4;
                if (!wall || Math.abs(v) <= 1 && (u == 28 || u == 34)) {
                    continue;
                }
                for (int h = 0; h <= 2; h++) {
                    local(wing, u, h, v, h > 0 && Math.abs(v) == 4 && u >= 30 && u <= 32 ? Material.LIGHT_GRAY_STAINED_GLASS : Material.QUARTZ_BRICKS);
                }
            }
        }
        localData(wing, 31, 0, 0, "minecraft:smooth_quartz_slab[type=top]");
        localData(wing, 32, 0, 0, "minecraft:smooth_quartz_slab[type=top]");
        local(wing, 31, 1, 0, Material.WHITE_CARPET);
        local(wing, 29, 0, -3, Material.CAULDRON);
        local(wing, 33, 0, 3, Material.CAULDRON);
        localData(wing, 31, 7, 0, BULB);
        for (int u = 18; u <= 20; u++) {
            localData(wing, u, 0, -5, "minecraft:smooth_quartz_slab[type=top]");
        }
        local(wing, 19, 1, -5, Material.LANTERN);
        for (int u = 37; u <= 41; u++) {
            local(wing, u, 0, 15, Material.WHITE_TERRACOTTA);
            local(wing, u, 1, 15, Material.WHITE_TERRACOTTA);
            local(wing, u, 2, 15, u % 2 == 0 ? Material.FLOWER_POT : Material.DECORATED_POT);
        }
        bed(wing, 39, -6, 0);
        for (int v = 12; v <= 14; v++) {
            localData(wing, 42, 0, v, "minecraft:spruce_slab[type=top]");
        }
        for (int[] bulb : new int[][] {{21, -9}, {27, -9}, {33, -9}, {39, -9}, {39, 9}, {27, 9}, {21, 0}}) {
            localData(wing, bulb[0], 7, bulb[1], BULB);
        }
        hangingLantern(wing, 38, 5, 6, style.height());
        cobwebs(wing, style.height());
    }

    private void bed(Wing wing, int u, int footV, int towardV) {
        int du = towardV == 0 ? 1 : 0;
        String facing = face(wing, du, towardV);
        localData(wing, u, 0, footV, "minecraft:white_bed[part=foot,facing=" + facing + "]");
        localData(wing, u + du, 0, footV + towardV, "minecraft:white_bed[part=head,facing=" + facing + "]");
    }

    private void doorFrame(Wing wing, Material frame, Material accent, Material canopy) {
        for (int h = 0; h <= 3; h++) {
            local(wing, 16, h, -3, frame);
            local(wing, 16, h, 3, frame);
        }
        for (int v = -3; v <= 3; v++) {
            local(wing, 16, 3, v, frame);
        }
        for (int v = -4; v <= 4; v++) {
            local(wing, 16, 4, v, accent);
            local(wing, 15, 5, v, canopy);
        }
    }

    private void hangingLantern(Wing wing, int u, int h, int v, int roof) {
        for (int chain = h + 1; chain < roof; chain++) {
            localData(wing, u, chain, v, "minecraft:iron_chain[axis=y]");
        }
        localData(wing, u, h, v, "minecraft:lantern[hanging=true]");
    }

    private void spawnRoom(ArenaMap.Window window) {
        window.bay().ifPresent(bay -> {
            int cx = (bay.minX() + bay.maxX()) / 2 - x;
            int cz = (bay.minZ() + bay.maxZ()) / 2 - z;
            var wing = Wing.toward(cx, cz);
            boolean outer = !window.room().equals("yard");
            var walls = bayWalls(wing, outer);
            bay.blocks(world).forEach(block -> {
                boolean shell = block.getX() == bay.minX() || block.getX() == bay.maxX() || block.getZ() == bay.minZ() || block.getZ() == bay.maxZ();
                if (!outer && !shell && block.getY() == bay.maxY()) {
                    block.setType(walls, false);
                } else if (shell && block.getType().isAir()) {
                    block.setType(outer && block.getY() == bay.maxY() && random.nextDouble() < 0.3 ? Material.AIR : walls, false);
                } else if (!shell) {
                    block.setType(Material.AIR, false);
                }
            });
            var boards = window.boards();
            boolean alongX = boards.minZ() == boards.maxZ();
            String bars = alongX ? "minecraft:iron_bars[east=true,west=true]" : "minecraft:iron_bars[north=true,south=true]";
            for (int along = -1; along <= (alongX ? boards.maxX() - boards.minX() : boards.maxZ() - boards.minZ()) + 1; along++) {
                int bx = alongX ? boards.minX() + along : boards.minX();
                int bz = alongX ? boards.minZ() : boards.minZ() + along;
                world.getBlockAt(bx, boards.maxY() + 1, bz).setBlockData(Bukkit.createBlockData(bars), false);
            }
            boards.blocks(world).forEach(block -> block.setBlockData(Bukkit.createBlockData("minecraft:oak_slab[type=top]"), false));
            if (outer) {
                gate(wing);
            } else {
                storeroom(wing);
            }
        });
    }

    private Material bayWalls(Wing wing, boolean outer) {
        return switch (wing) {
            case EAST -> outer ? Material.WAXED_WEATHERED_CUT_COPPER : Material.WHITE_CONCRETE;
            case NORTH -> outer ? Material.COBBLESTONE : Material.SPRUCE_PLANKS;
            case SOUTH -> outer ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            case WEST -> outer ? Material.WHITE_CONCRETE : Material.SMOOTH_QUARTZ;
        };
    }

    private void gate(Wing wing) {
        String bars = "minecraft:iron_bars[" + paneSides(wing) + "]";
        for (int v = -3; v <= 3; v++) {
            for (int h = 0; h <= 5; h++) {
                localData(wing, 49, h, v, bars);
            }
        }
        for (int u = 44; u <= 48; u++) {
            for (int v : new int[] {-4, 4}) {
                switch (wing) {
                    case EAST -> {
                        local(wing, u, 4, v, Material.WAXED_WEATHERED_COPPER_GRATE);
                        if (u % 2 == 0) {
                            localData(wing, u, 0, v - Integer.signum(v), "minecraft:spruce_slab[type=bottom]");
                        }
                    }
                    case NORTH -> {
                        for (int h = 0; h <= 4; h++) {
                            local(wing, u, h, v, switch (random.nextInt(5)) {
                                case 0 -> Material.MOSSY_COBBLESTONE;
                                case 1 -> Material.WAXED_EXPOSED_COPPER;
                                case 2 -> Material.SPRUCE_PLANKS;
                                case 3 -> Material.TUFF;
                                default -> Material.COBBLESTONE;
                            });
                        }
                    }
                    case SOUTH -> {
                        localData(wing, u, 4, v - Integer.signum(v), "minecraft:waxed_lightning_rod[facing=" + face(wing, 1, 0) + "]");
                        if (u % 2 == 1) {
                            localData(wing, u, 3, v - Integer.signum(v), "minecraft:iron_chain[axis=y]");
                        }
                    }
                    case WEST -> local(wing, u, 2, v, Material.RED_CONCRETE);
                }
            }
        }
        for (int u = 44; u <= 48; u++) {
            for (int v = -3; v <= 3; v++) {
                local(wing, u, -1, v, wing == Wing.EAST ? Material.POLISHED_BLACKSTONE : Material.COBBLED_DEEPSLATE);
            }
        }
    }

    private void storeroom(Wing wing) {
        String facing = face(wing, 1, 0);
        switch (wing) {
            case EAST -> {
                ironDoor(wing, facing);
                for (int u : new int[] {19, 21}) {
                    localData(wing, u, 4, 8, "minecraft:iron_chain[axis=y]");
                    localData(wing, u, 3, 8, "minecraft:iron_chain[axis=y]");
                    localData(wing, u, 2, 8, "minecraft:waxed_lightning_rod[facing=down]");
                }
                for (int u = 17; u <= 23; u += 2) {
                    localData(wing, u, 0, 11, "minecraft:barrel[facing=up]");
                    local(wing, u, 0, 6, Material.PACKED_ICE);
                }
            }
            case NORTH -> {
                local(wing, 24, 0, 8, Material.STRIPPED_SPRUCE_WOOD);
                local(wing, 24, 1, 9, Material.STRIPPED_SPRUCE_WOOD);
                localData(wing, 24, 0, 9, "minecraft:spruce_log[axis=" + (axis(wing) == 'x' ? "z" : "x") + "]");
                localData(wing, 24, 1, 8, "minecraft:spruce_log[axis=" + (axis(wing) == 'x' ? "z" : "x") + "]");
                for (int u = 17; u <= 23; u++) {
                    if (random.nextDouble() < 0.6) {
                        crate(wing, u, 0, 6);
                    }
                    if (random.nextDouble() < 0.6) {
                        crate(wing, u, 0, 11);
                    }
                }
                local(wing, 17, 4, 6, Material.COBWEB);
                local(wing, 23, 4, 11, Material.COBWEB);
            }
            case SOUTH -> {
                ironDoor(wing, facing);
                for (int v = 7; v <= 10; v++) {
                    for (int h = 0; h <= 2; h++) {
                        if (v == 7 || v == 10 || h == 2) {
                            local(wing, 24, h, v, (h + v) % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE);
                        }
                    }
                }
                for (int u = 17; u <= 23; u++) {
                    for (int h = 0; h <= 2; h++) {
                        local(wing, u, h, 6, Material.WAXED_EXPOSED_COPPER_GRATE);
                        local(wing, u, h, 11, Material.WAXED_EXPOSED_COPPER_GRATE);
                    }
                }
            }
            case WEST -> {
                ironDoor(wing, facing);
                for (int u = 17; u <= 23; u++) {
                    for (int h = 0; h <= 2; h++) {
                        local(wing, u, h, 6, (u + h) % 2 == 0 ? Material.IRON_BLOCK : Material.POLISHED_ANDESITE);
                        local(wing, u, h, 11, (u + h) % 2 == 0 ? Material.IRON_BLOCK : Material.POLISHED_ANDESITE);
                    }
                }
            }
        }
    }

    private void ironDoor(Wing wing, String facing) {
        for (int v = 8; v <= 9; v++) {
            String hinge = v == 8 ? "left" : "right";
            localData(wing, 24, 0, v, "minecraft:iron_door[facing=" + facing + ",half=lower,hinge=" + hinge + ",open=false]");
            localData(wing, 24, 1, v, "minecraft:iron_door[facing=" + facing + ",half=upper,hinge=" + hinge + ",open=false]");
        }
    }

    private void station(ArenaMap.Station station) {
        var location = station.location();
        location.clone().add(0, -1, 0).getBlock().setType(Material.WAXED_CUT_COPPER, false);
        location.getBlock().setType(station.kind().material(), false);
        location.clone().add(0, 3, 0).getBlock().setBlockData(Bukkit.createBlockData("minecraft:lantern[hanging=true]"), false);
        if (!station.room().equals("yard")) {
            for (int height = 4; height <= 10 && location.clone().add(0, height, 0).getBlock().getType().isAir(); height++) {
                location.clone().add(0, height, 0).getBlock().setType(Material.IRON_CHAIN, false);
            }
        }
    }

    private void note(ArenaMap arena, ArenaMap.Note note) {
        var location = note.location();
        var room = arena.rooms().stream().filter(each -> each.id().equals(note.room())).findFirst().orElseThrow().bounds();
        double dx = (room.minX() + room.maxX() + 1) / 2.0 - location.getBlockX();
        double dz = (room.minZ() + room.maxZ() + 1) / 2.0 - location.getBlockZ();
        String facing = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
        location.getBlock().setBlockData(Bukkit.createBlockData("minecraft:lectern[facing=" + facing + ",has_book=true]"), false);
    }

    private void spruce(int bx, int bz, int height) {
        for (int h = 0; h < height; h++) {
            put(bx, h, bz, Material.SPRUCE_LOG);
        }
        for (int h = 2; h <= height; h++) {
            int radius = h == height ? 0 : (height - h) % 3 == 0 ? 1 : Math.min(2, 1 + (height - h) / 4);
            for (int lx = -radius; lx <= radius; lx++) {
                for (int lz = -radius; lz <= radius; lz++) {
                    if ((lx != 0 || lz != 0 || h >= height - 1) && Math.abs(lx) + Math.abs(lz) <= radius + 1 && isAir(bx + lx, h, bz + lz)) {
                        data(bx + lx, h, bz + lz, "minecraft:spruce_leaves[persistent=true]");
                    }
                }
            }
        }
    }

    private void deadTree(int bx, int bz, int height) {
        for (int h = 0; h < height; h++) {
            put(bx, h, bz, Material.DARK_OAK_LOG);
        }
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int branch = 0; branch < 3; branch++) {
            var direction = directions[random.nextInt(4)];
            int h = random.nextInt(height / 2, height);
            int length = random.nextInt(1, 3);
            for (int i = 1; i <= length; i++) {
                data(bx + direction[0] * i, h, bz + direction[1] * i, "minecraft:dark_oak_log[axis=" + (direction[0] != 0 ? "x" : "z") + "]");
            }
            put(bx + direction[0] * (length + 1), h + 1, bz + direction[1] * (length + 1), Material.DARK_OAK_FENCE);
        }
    }

    private void shrub(int bx, int bz) {
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                if (random.nextDouble() < 0.8) {
                    data(bx + lx, 0, bz + lz, "minecraft:azalea_leaves[persistent=true]");
                }
            }
        }
        data(bx, 1, bz, "minecraft:spruce_leaves[persistent=true]");
    }

    private void litter(int bx, int bz) {
        String[] facings = {"north", "east", "south", "west"};
        data(bx, 0, bz, "minecraft:leaf_litter[segment_amount=" + random.nextInt(1, 5) + ",facing=" + facings[random.nextInt(4)] + "]");
    }

    private void connected(String id, int h, List<int[]> cells) {
        Set<Long> keys = new HashSet<>();
        cells.forEach(cell -> keys.add(key(cell[0], cell[1])));
        for (var cell : cells) {
            int cx = cell[0];
            int cz = cell[1];
            data(cx, h, cz, id + "[east=" + keys.contains(key(cx + 1, cz)) + ",west=" + keys.contains(key(cx - 1, cz))
                    + ",north=" + keys.contains(key(cx, cz - 1)) + ",south=" + keys.contains(key(cx, cz + 1)) + "]");
        }
    }

    private static long key(int bx, int bz) {
        return ((long) bx << 32) ^ (bz & 0xffffffffL);
    }

    private static String face(Wing wing, int forward, int side) {
        int dx = wing.x(forward, side);
        int dz = wing.z(forward, side);
        return dx > 0 ? "east" : dx < 0 ? "west" : dz > 0 ? "south" : "north";
    }

    private static char axis(Wing wing) {
        return wing.x(1, 0) != 0 ? 'x' : 'z';
    }

    /** The connections of a pane or bar that runs across the wing, along its side axis. */
    private static String paneSides(Wing wing) {
        return axis(wing) == 'x' ? "north=true,south=true" : "east=true,west=true";
    }

    private void local(Wing wing, int forward, int height, int side, Material material) {
        put(wing.x(forward, side), height, wing.z(forward, side), material);
    }

    private void localData(Wing wing, int forward, int height, int side, String data) {
        data(wing.x(forward, side), height, wing.z(forward, side), data);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int bx = Math.min(x1, x2); bx <= Math.max(x1, x2); bx++) {
            for (int by = Math.min(y1, y2); by <= Math.max(y1, y2); by++) {
                for (int bz = Math.min(z1, z2); bz <= Math.max(z1, z2); bz++) {
                    put(bx, by, bz, material);
                }
            }
        }
    }

    private boolean isAir(int bx, int by, int bz) {
        return inside(bx, by, bz) && world.getBlockAt(x + bx, y + by, z + bz).getType().isAir();
    }

    private static boolean inside(int bx, int by, int bz) {
        return Math.abs(bx) <= RADIUS && Math.abs(bz) <= RADIUS && by >= -1 && by <= HEIGHT;
    }

    private void put(int bx, int by, int bz, Material material) {
        if (inside(bx, by, bz)) {
            world.getBlockAt(x + bx, y + by, z + bz).setType(material, false);
        }
    }

    private void data(int bx, int by, int bz, String data) {
        if (inside(bx, by, bz)) {
            world.getBlockAt(x + bx, y + by, z + bz).setBlockData(Bukkit.createBlockData(data), false);
        }
    }
}
