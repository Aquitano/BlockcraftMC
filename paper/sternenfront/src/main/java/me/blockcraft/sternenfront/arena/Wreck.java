package me.blockcraft.sternenfront.arena;

import org.bukkit.Material;

import java.util.Random;

/**
 * Post B: the cruiser that came down across the basin and broke in two. The bow lies to the north with its nose
 * ploughed into the sand, the stern to the south under its bridge tower and dead engines, and the post sits in the
 * gap between the torn ends, where only the ribs are left. Each piece has a corridor along its keel, a breach in
 * either flank to cross it and a deck to fight on; a ladder in the stern climbs to the bridge.
 */
final class Wreck {

    private static final int FLOOR = TessarMap.FLOOR;
    private static final double GAP = 7.5;
    private static final double TORN = 5;

    private final Canvas canvas;
    private final Terrain terrain;
    private final Random random;

    Wreck(Canvas canvas, Terrain terrain, Random random) {
        this.canvas = canvas;
        this.terrain = terrain;
        this.random = random;
    }

    void build() {
        scorch();
        var bow = new Piece(Math.toRadians(12), 22, true);
        var stern = new Piece(Math.toRadians(192), 20, false);
        bow.build();
        stern.build();
        stern.engines();
        stern.tower();
        bow.turret(9, 0);
        bow.turret(15.5, 0);
        stern.turret(6.5, 4);
        bow.drifts();
        stern.drifts();
        debris();
        fragments();
    }

    /** One half of the cruiser, laid out along {@code heading} (0 north, clockwise) from the gap in the middle. */
    private final class Piece {

        private final double ax;
        private final double az;
        private final double ox;
        private final double oz;
        private final double length;
        private final boolean bow;

        Piece(double heading, double length, boolean bow) {
            this.ax = Math.sin(heading);
            this.az = -Math.cos(heading);
            this.ox = ax * GAP;
            this.oz = az * GAP;
            this.length = length;
            this.bow = bow;
        }

        private double u(double x, double z) {
            return (x - ox) * ax + (z - oz) * az;
        }

        private double v(double x, double z) {
            return (x - ox) * -az + (z - oz) * ax;
        }

        private int worldX(double u, double v) {
            return (int) Math.floor(ox + ax * u - az * v);
        }

        private int worldZ(double u, double v) {
            return (int) Math.floor(oz + az * u + ax * v);
        }

        private double half(double u) {
            if (bow) {
                return (20 - 15 * Math.pow(Math.clamp(u / length, 0, 1), 1.4)) / 2;
            }
            return u < length - 3 ? 11 : 11 - (u - (length - 3)) * 1.2;
        }

        private double height(double u) {
            return bow ? 11 - Math.max(0, u - 12) * 0.5 : 12;
        }

        private int top(double u, double v) {
            double half = half(u);
            return (int) Math.round(FLOOR + height(u) * (1 - 0.5 * Math.abs(v) / half) + (Math.abs(v) < 1.5 ? 1 : 0));
        }

        private boolean breach(double u, double v, int y) {
            double from = bow ? 10 : 9;
            double to = from + 3.5;
            double jag = 0.6 * terrain.noise(u * 0.9, y * 0.9 + (bow ? 3 : 9));
            return u > from - jag && u < to + jag && Math.abs(v) > half(u) - 2.6 && y >= FLOOR + 1 && y <= FLOOR + 3 + (u > from + 1 && u < to - 1 ? 1 : 0);
        }

        private boolean bulkhead(double u) {
            double first = bow ? 7 : 5;
            return (u >= first && u < first + 1.3) || (u >= first * 2 + 2 && u < first * 2 + 3.3);
        }

        void build() {
            int reach = (int) length + 14;
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach - 8; z <= reach + 8; z++) {
                    double u = u(x + 0.5, z + 0.5);
                    double v = v(x + 0.5, z + 0.5);
                    if (u < 0 || u > length || Math.abs(v) > half(u)) {
                        continue;
                    }
                    column(x, z, u, v);
                }
            }
        }

        private void column(int x, int z, double u, double v) {
            double half = half(u);
            int top = top(u, v);
            boolean rib = Math.floor(u) % 3 == 0;
            boolean torn = u < TORN && !rib && random.nextDouble() > u / TORN * 0.45;
            canvas.set(x, FLOOR, z, (Math.floor(u) + Math.floor(Math.abs(v))) % 4 == 0 ? Material.POLISHED_ANDESITE : Material.POLISHED_DEEPSLATE);
            for (int y = FLOOR + 1; y <= top; y++) {
                boolean wall = Math.abs(v) > half - 2;
                boolean outer = Math.abs(v) > half - 1 || y == top;
                boolean roof = y > top - 2;
                if (torn || breach(u, v, y)) {
                    canvas.clear(x, y, z);
                } else if (!wall && !roof) {
                    if (bulkhead(u) && (Math.abs(v) > 1.6 || y > FLOOR + 3)) {
                        canvas.set(x, y, z, Math.abs(v) > 3 && Math.abs(v) < 5 && (y == FLOOR + 3 || y == FLOOR + 4)
                                ? Material.IRON_BARS : Material.POLISHED_ANDESITE);
                    } else {
                        canvas.clear(x, y, z);
                    }
                } else if (rib && u < TORN) {
                    canvas.set(x, y, z, outer ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
                } else if (!outer) {
                    boolean light = y == top - 1 && Math.abs(v) < 1 && Math.floor(u) % 5 == 2;
                    canvas.set(x, y, z, light ? Material.SEA_LANTERN : Material.GRAY_CONCRETE);
                } else {
                    canvas.set(x, y, z, plate(u, v, y, top, half));
                }
            }
        }

        private Material plate(double u, double v, int y, int top, double half) {
            double scorch = Math.max(0, 1 - u / 7) * 0.5;
            boolean side = Math.abs(v) > half - 1 && y < top;
            if (random.nextDouble() < scorch) {
                return switch (random.nextInt(side && y < top - 1 ? 6 : 5)) {
                    case 0 -> Material.BLACKSTONE;
                    case 1 -> Material.BLACK_CONCRETE;
                    case 2 -> Material.COAL_BLOCK;
                    case 3 -> Material.POLISHED_BASALT;
                    case 4 -> Material.GRAY_CONCRETE;
                    default -> Material.MAGMA_BLOCK;
                };
            }
            if (side && y == FLOOR + 4 && Math.floor(u) % 3 == 1) {
                return random.nextInt(3) == 0 ? Material.BLACK_STAINED_GLASS : Material.SEA_LANTERN;
            }
            if (side && y == FLOOR + 2) {
                return Material.RED_TERRACOTTA;
            }
            if (Math.abs(v) < 1.5 && y == top) {
                return Material.IRON_BLOCK;
            }
            if (Math.floor(u) % 6 == 0 || (side && y == FLOOR + 6)) {
                return Material.GRAY_CONCRETE;
            }
            int pick = random.nextInt(14);
            return pick == 0 ? Material.POLISHED_ANDESITE : pick == 1 ? Material.SMOOTH_STONE : pick == 2 ? Material.LIGHT_GRAY_TERRACOTTA
                    : Material.LIGHT_GRAY_CONCRETE;
        }

        /** A gun turret on the hull's back: a squat housing with two barrels, still pointing at the sky. */
        void turret(double u, double v) {
            int x = worldX(u, v);
            int z = worldZ(u, v);
            int base = top(u, v) + 1;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    canvas.set(x + dx, base, z + dz, Math.abs(dx) + Math.abs(dz) == 2 ? Material.POLISHED_DEEPSLATE : Material.GRAY_CONCRETE);
                }
            }
            canvas.set(x, base + 1, z, Material.POLISHED_DEEPSLATE);
            canvas.set(x + 1, base + 1, z, "minecraft:polished_deepslate_stairs[facing=west]");
            canvas.set(x - 1, base + 1, z, "minecraft:polished_deepslate_stairs[facing=east]");
            for (int i = 1; i <= 3; i++) {
                canvas.set(x, base + 1, z - 1 - i, i == 3 ? Material.IRON_BLOCK : Material.IRON_CHAIN);
                canvas.set(x, base + 1 + (i == 3 ? 1 : 0), z - 1, Material.POLISHED_DEEPSLATE);
            }
        }

        /** Sand blown against the flanks, deepest where the wind comes from, in the west. */
        void drifts() {
            for (double u = 0.5; u < length; u += 0.5) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    double half = half(u);
                    for (double out = 0.5; out < 3.5; out += 0.5) {
                        int x = worldX(u, sign * (half + out));
                        int z = worldZ(u, sign * (half + out));
                        int ground = terrain.height(x, z);
                        if (ground > FLOOR + 1 || !canvas.isAir(x, ground + 1, z)) {
                            continue;
                        }
                        boolean windward = (ox + ax * u - az * sign * half) < ox;
                        int pile = (int) Math.round((windward ? 2.6 : 1.4) * (1 - out / 3.5) + 0.4 * terrain.noise(u * 0.3, out));
                        for (int y = ground + 1; y <= ground + pile; y++) {
                            if (canvas.isAir(x, y, z) && canvas.type(x, y - 1, z).isSolid()) {
                                canvas.set(x, y, z, Material.SAND);
                            }
                        }
                    }
                }
            }
            if (bow) {
                for (double u = length - 4; u <= length + 3; u += 0.5) {
                    for (double v = -4; v <= 4; v += 0.5) {
                        int x = worldX(u, v);
                        int z = worldZ(u, v);
                        int ground = Math.max(terrain.height(x, z), FLOOR);
                        int pile = (int) Math.round(3.5 - Math.abs(v) * 0.6 - Math.max(0, u - length) * 0.8);
                        for (int y = ground + 1; y <= ground + pile; y++) {
                            if (canvas.isAir(x, y, z) && canvas.type(x, y - 1, z).isSolid()) {
                                canvas.set(x, y, z, Material.SAND);
                            }
                        }
                    }
                }
            }
        }

        /** Three dead nozzles at the stern's end, still glowing deep inside. */
        void engines() {
            double[] centres = {-6.5, 0, 6.5};
            double yc = FLOOR + 5.5;
            double radius = 3.1;
            for (double u = length - 1; u <= length + 3.5; u += 0.25) {
                for (double v = -11; v <= 11; v += 0.25) {
                    for (double centre : centres) {
                        for (int y = FLOOR + 1; y <= FLOOR + 10; y++) {
                            double d = Math.hypot(v - centre, y + 0.5 - yc);
                            if (d > radius) {
                                continue;
                            }
                            int x = worldX(u, v);
                            int z = worldZ(u, v);
                            boolean ring = d > radius - 1.1;
                            if (ring) {
                                canvas.set(x, y, z, u > length + 2.8 ? Material.POLISHED_DEEPSLATE : Material.POLISHED_BLACKSTONE);
                            } else if (u > length + 1) {
                                canvas.clear(x, y, z);
                            } else {
                                canvas.set(x, y, z, d < 0.9 ? Material.SHROOMLIGHT : Material.BLACK_CONCRETE);
                            }
                        }
                    }
                }
            }
        }

        /** The bridge on the stern: a neck with the ladder in it and a wide bridge deck with windows all round. */
        void tower() {
            int deck = FLOOR + 15;
            int roof = FLOOR + 19;
            for (int x = -30; x <= 30; x++) {
                for (int z = -10; z <= 40; z++) {
                    double u = u(x + 0.5, z + 0.5);
                    double v = v(x + 0.5, z + 0.5);
                    boolean neck = u >= 12.5 && u <= 16.5 && Math.abs(v) <= 2.6;
                    boolean bridge = u >= 11.5 && u <= 17.5 && Math.abs(v) <= 6.2 - Math.max(0, 14.5 - u) * 0.5;
                    if (neck) {
                        for (int y = top(u, v) - 1; y < deck; y++) {
                            canvas.set(x, y, z, Math.abs(v) > 1.6 || u < 13.5 || u > 15.5 ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE);
                        }
                    }
                    if (!bridge) {
                        continue;
                    }
                    boolean wall = u < 12.5 || u > 16.5 || Math.abs(v) > 5.2 - Math.max(0, 14.5 - u) * 0.5;
                    for (int y = deck; y <= roof + 1; y++) {
                        if (y == deck) {
                            canvas.set(x, y, z, wall ? Material.GRAY_CONCRETE : Material.POLISHED_ANDESITE);
                        } else if (y == roof) {
                            canvas.set(x, y, z, wall ? Material.LIGHT_GRAY_CONCRETE : Material.SMOOTH_STONE);
                        } else if (y == roof + 1) {
                            if (wall) {
                                canvas.set(x, y, z, Material.SMOOTH_STONE_SLAB);
                            }
                        } else if (wall) {
                            boolean window = y == deck + 2 || y == deck + 3;
                            canvas.set(x, y, z, window ? (random.nextInt(5) == 0 ? Material.AIR : Material.BLACK_STAINED_GLASS)
                                    : Material.WHITE_CONCRETE);
                        } else {
                            canvas.clear(x, y, z);
                        }
                    }
                }
            }
            int sx = worldX(14.5, 0);
            int sz = worldZ(14.5, 0);
            for (int y = FLOOR + 1; y <= deck; y++) {
                canvas.set(sx, y, sz - 1, Material.IRON_BLOCK);
                canvas.set(sx, y, sz, "minecraft:ladder[facing=south]");
            }
            canvas.set(worldX(15.5, 3), deck + 1, worldZ(15.5, 3), "minecraft:observer[facing=up]");
            canvas.set(worldX(15.5, -3), deck + 1, worldZ(15.5, -3), "minecraft:observer[facing=up]");
            canvas.set(worldX(14.5, 2), roof - 1, worldZ(14.5, 2), Material.SEA_LANTERN);
            int ax = worldX(16, 2);
            int az = worldZ(16, 2);
            for (int y = roof + 1; y <= roof + 5; y++) {
                canvas.set(ax, y, az, y == roof + 3 ? Material.IRON_BARS : Material.WAXED_LIGHTNING_ROD);
            }
            canvas.set(ax, roof + 6, az, "minecraft:end_rod[facing=up]");
            int dx = worldX(13.5, -2.5);
            int dz = worldZ(13.5, -2.5);
            canvas.set(dx, roof + 1, dz, Material.POLISHED_DEEPSLATE_WALL);
            canvas.set(dx, roof + 2, dz, Material.DAYLIGHT_DETECTOR);
            canvas.set(dx + 1, roof + 2, dz, "minecraft:smooth_stone_slab[type=bottom]");
            canvas.set(dx - 1, roof + 2, dz, "minecraft:smooth_stone_slab[type=bottom]");
        }
    }

    /** Burnt ground round the gap, darkest where the hull broke. */
    private void scorch() {
        for (int x = -22; x <= 22; x++) {
            for (int z = -22; z <= 22; z++) {
                double distance = Math.hypot(x, z);
                if (random.nextDouble() > 0.85 - distance / 26) {
                    continue;
                }
                int y = terrain.height(x, z);
                canvas.set(x, y, z, switch (random.nextInt(5)) {
                    case 0 -> Material.BLACKSTONE;
                    case 1 -> Material.SMOOTH_BASALT;
                    case 2 -> Material.GRAY_TERRACOTTA;
                    case 3 -> Material.BLACK_TERRACOTTA;
                    default -> Material.COARSE_DIRT;
                });
            }
        }
    }

    /** Torn plates and girders in the gap, cover round the post without hiding its beacon. */
    private void debris() {
        plate(-7, -2, true, 3);
        plate(6, 1, true, 3);
        plate(-2, 6, false, 4);
        plate(2, -6, false, 3);
        girder(-12, FLOOR + 1, -3, -8, FLOOR + 3, 2);
        girder(12, FLOOR + 1, 3, 8, FLOOR + 3, -2);
        for (var crate : new int[][]{{-4, -4}, {4, 4}, {-9, 4}, {9, -4}}) {
            canvas.set(crate[0], FLOOR + 1, crate[1], Material.BARREL);
        }
        canvas.set(-4, FLOOR + 2, -4, "minecraft:iron_chain[axis=y]");
    }

    /** Hull plates the crash flung across the sand, cover on the way to the gap. */
    private void fragments() {
        int[][] plates = {{-17, -11, 4, 3}, {16, 12, 4, 3}, {-21, 9, 3, 2}, {20, -8, 3, 2}, {-19, 22, 3, 3}, {14, -22, 3, 3}};
        for (var plate : plates) {
            int x0 = plate[0];
            int z0 = plate[1];
            for (int dx = 0; dx < plate[2]; dx++) {
                for (int dz = 0; dz < plate[3]; dz++) {
                    int x = x0 + dx;
                    int z = z0 + dz;
                    int ground = terrain.height(x, z);
                    boolean edge = dx == 0 || dz == plate[3] - 1;
                    canvas.set(x, ground + 1, z, edge ? "minecraft:light_gray_concrete" : "minecraft:smooth_stone_slab[type=bottom]");
                    if (edge && (dx + dz) % 2 == 0) {
                        canvas.set(x, ground + 2, z, random.nextInt(3) == 0 ? Material.BLACK_CONCRETE : Material.GRAY_CONCRETE);
                    }
                }
            }
        }
    }

    private void plate(int x, int z, boolean alongZ, int length) {
        for (int i = 0; i < length; i++) {
            int px = alongZ ? x : x + i;
            int pz = alongZ ? z + i : z;
            int height = i == 0 || i == length - 1 ? 1 : 2;
            for (int y = FLOOR + 1; y <= FLOOR + height; y++) {
                canvas.set(px, y, pz, (i + y) % 3 == 0 ? Material.BLACK_CONCRETE : Material.LIGHT_GRAY_CONCRETE);
            }
            canvas.set(px, FLOOR + height + 1, pz, Material.SMOOTH_STONE_SLAB);
        }
    }

    private void girder(int x1, int y1, int z1, int x2, int y2, int z2) {
        canvas.line(x1, y1, z1, x2, y2, z2, Material.POLISHED_DEEPSLATE);
        canvas.line(x1, y1 + 1, z1, x2, y2 + 1, z2, Material.IRON_BARS);
    }
}
