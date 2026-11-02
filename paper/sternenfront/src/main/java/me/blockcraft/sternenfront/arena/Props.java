package me.blockcraft.sternenfront.arena;

import org.bukkit.Material;

import java.util.Random;

/**
 * What stands in the open basin between the bases and the posts: mesas and an arch that break the long views,
 * boulders, two crashed fighters, the bones of a great beast, moisture vaporators, crates and an old pipeline. Each
 * piece has a twin on the other faction's side, mirrored or turned half round, so neither side has more cover.
 */
final class Props {

    private static final int FLOOR = TessarMap.FLOOR;

    private final Canvas canvas;
    private final Terrain terrain;
    private final Random random;

    Props(Canvas canvas, Terrain terrain, Random random) {
        this.canvas = canvas;
        this.terrain = terrain;
        this.random = random;
    }

    void build() {
        for (int side = -1; side <= 1; side += 2) {
            mesa(side * 42, -26, 6.2, 15);
            mesa(side * 44, 24, 5.4, 13);
            arch(side * 55, -11, side * 55, 9);
            for (int[] boulder : new int[][]{{28, -14, 25}, {24, 13, 20}, {62, -28, 25}, {64, 32, 28}, {18, -27, 17},
                    {16, 27, 17}, {36, 40, 22}, {34, -38, 20}, {70, 40, 18}, {50, -50, 22}}) {
                boulder(side * boulder[0], boulder[1], boulder[2] / 10.0);
            }
            vaporator(side * 48, -1);
            vaporator(side * 71, 30);
            vaporator(side * 72, -31);
            vaporator(side * 23, 35);
            vaporator(side * 21, -31);
            crates(side * 30, 30);
            crates(side * 48, -17);
            crates(side * 50, 18);
            crates(side * 26, -48);
            pipeline(side);
        }
        fighter(-34, 7, 70);
        fighter(34, -7, 250);
        beast(-58, 41, 25);
        beast(58, -41, 205);
    }

    private void mesa(int cx, int cz, double radius, int height) {
        int r = (int) Math.ceil(radius) + 3;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                double angle = Math.atan2(dz, dx);
                double edge = radius + 1.3 * terrain.noise(Math.cos(angle) * 2 + cx, Math.sin(angle) * 2 + cz);
                double d = Math.hypot(dx, dz);
                int ground = terrain.height(x, z);
                int top;
                if (d <= edge) {
                    top = FLOOR + height - (d > edge - 1.5 && terrain.noise(x * 0.4, z * 0.4) > 0.3 ? 2 : 0);
                } else if (d <= edge + 2.2) {
                    top = Math.max(ground, FLOOR + (int) Math.round(2.4 - (d - edge)));
                } else {
                    continue;
                }
                for (int y = FLOOR - 1; y <= top; y++) {
                    canvas.set(x, y, z, y == top && d <= edge ? (terrain.noise(x * 0.3, z * 0.3) > 0.2 ? Material.RED_SAND : Material.SANDSTONE)
                            : terrain.rock(x, y, z));
                }
            }
        }
    }

    /** A natural arch over a lane: two legs and a span that rises between them. */
    private void arch(int x1, int z1, int x2, int z2) {
        int legTop = FLOOR + 8;
        for (var leg : new int[][]{{x1, z1}, {x2, z2}}) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.hypot(dx, dz) > 2.4) {
                        continue;
                    }
                    for (int y = FLOOR - 1; y <= legTop; y++) {
                        canvas.set(leg[0] + dx, y, leg[1] + dz, terrain.rock(leg[0] + dx, y, leg[1] + dz));
                    }
                }
            }
        }
        int steps = 60;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x1 + (x2 - x1) * t;
            double z = z1 + (z2 - z1) * t;
            double y = legTop + Math.sin(Math.PI * t) * 3.2;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int bx = (int) Math.floor(x) + dx;
                    int by = (int) Math.round(y) + dy;
                    int bz = (int) Math.floor(z);
                    if (Math.abs(dx) + Math.abs(dy) < 2 || i % 7 != 0) {
                        canvas.set(bx, by, bz, terrain.rock(bx, by, bz));
                    }
                }
            }
        }
    }

    private void boulder(int cx, int cz, double radius) {
        int r = (int) Math.ceil(radius) + 1;
        int ground = terrain.height(cx, cz);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= r; dy++) {
                    double wobble = 0.5 * terrain.noise((cx + dx) * 0.5, (cz + dz) * 0.5 + dy);
                    double d = Math.sqrt(dx * dx + dz * dz + dy * dy * 1.7);
                    if (d <= radius + wobble) {
                        int x = cx + dx;
                        int z = cz + dz;
                        int y = ground + dy;
                        canvas.set(x, y, z, random.nextInt(4) == 0 ? Material.SMOOTH_SANDSTONE : terrain.rock(x, y, z));
                    }
                }
            }
        }
    }

    /** A moisture vaporator: a thin mast with fins and a light. */
    private void vaporator(int x, int z) {
        int y = terrain.height(x, z);
        canvas.set(x, y + 1, z, Material.POLISHED_ANDESITE);
        canvas.set(x, y + 2, z, Material.ANDESITE_WALL);
        for (int i = 3; i <= 6; i++) {
            canvas.set(x, y + i, z, i % 2 == 0 ? Material.WAXED_COPPER_GRATE : Material.IRON_BARS);
        }
        canvas.set(x, y + 7, z, Material.WAXED_LIGHTNING_ROD);
        canvas.set(x, y + 8, z, "minecraft:end_rod[facing=up]");
        canvas.set(x + 1, y + 1, z, "minecraft:polished_andesite_stairs[facing=west]");
        canvas.set(x - 1, y + 1, z, "minecraft:polished_andesite_stairs[facing=east]");
        canvas.set(x, y + 1, z + 1, "minecraft:polished_andesite_stairs[facing=north]");
        canvas.set(x, y + 1, z - 1, "minecraft:polished_andesite_stairs[facing=south]");
    }

    /** A heap of cargo: barrels, crates of logs and a tarp. */
    private void crates(int x, int z) {
        int y = terrain.height(x, z);
        canvas.fill(x, y + 1, z, x + 1, y + 2, z + 1, Material.STRIPPED_SPRUCE_LOG);
        canvas.set(x, y + 3, z, Material.BROWN_CARPET);
        canvas.set(x + 1, y + 3, z, Material.BROWN_CARPET);
        canvas.set(x + 2, y + 1, z, Material.BARREL);
        canvas.set(x + 2, y + 1, z + 1, Material.BARREL);
        canvas.set(x - 1, y + 1, z + 1, Material.BARREL);
        canvas.set(x + 2, y + 2, z, Material.BARREL);
        canvas.set(x, y + 1, z + 2, random.nextBoolean() ? Material.SCAFFOLDING : Material.HAY_BLOCK);
    }

    /** The old pipeline from the Raffinerie towards the base, on trestles, with a burst section. */
    private void pipeline(int side) {
        int[][] path = {{30, -46}, {44, -46}, {52, -40}};
        for (int leg = 0; leg + 1 < path.length; leg++) {
            int x1 = side * path[leg][0];
            int z1 = path[leg][1];
            int x2 = side * path[leg + 1][0];
            int z2 = path[leg + 1][1];
            int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
            for (int i = 0; i <= steps; i++) {
                int x = x1 + (x2 - x1) * i / steps;
                int z = z1 + (z2 - z1) * i / steps;
                int y = FLOOR + 2;
                if (leg == 0 && i >= 6 && i <= 7) {
                    canvas.set(x, FLOOR + 1, z, Material.WAXED_EXPOSED_CUT_COPPER);
                    continue;
                }
                canvas.set(x, y, z, i % 5 == 0 ? Material.IRON_BLOCK : Material.WAXED_COPPER_BLOCK);
                if (i % 5 == 0) {
                    canvas.set(x, FLOOR + 1, z, Material.POLISHED_DEEPSLATE_WALL);
                }
            }
        }
    }

    /** A crashed fighter lying on its belly at {@code heading} degrees, its furrow behind it. */
    private void fighter(int cx, int cz, double heading) {
        double ax = Math.sin(Math.toRadians(heading));
        double az = -Math.cos(Math.toRadians(heading));
        for (double u = -14; u <= 0; u += 0.5) {
            int x = (int) Math.floor(cx + ax * (u - 5));
            int z = (int) Math.floor(cz + az * (u - 5));
            int y = terrain.height(x, z);
            if (random.nextInt(3) > 0) {
                canvas.set(x, y, z, random.nextBoolean() ? Material.BLACK_TERRACOTTA : Material.COARSE_DIRT);
            }
        }
        for (double u = -4; u <= 5; u += 0.4) {
            for (double v = -5.5; v <= 5.5; v += 0.4) {
                int x = (int) Math.floor(cx + ax * u - az * v);
                int z = (int) Math.floor(cz + az * u + ax * v);
                double body = Math.abs(v) <= 1.6 - Math.max(0, u - 2.5) * 0.45 ? 1 : 0;
                boolean wing = Math.abs(v) <= 5.5 && u >= -2.5 && u <= 0.5 && Math.abs(v) > 1.4;
                if (body > 0) {
                    canvas.set(x, FLOOR + 1, z, Material.GRAY_CONCRETE);
                    canvas.set(x, FLOOR + 2, z, u > 1 && u < 3.5 ? Material.BLACK_STAINED_GLASS
                            : u < -3 ? Material.POLISHED_BLACKSTONE : Material.LIGHT_GRAY_CONCRETE);
                    if (Math.abs(v) < 0.8 && u < 0.5 && u > -3) {
                        canvas.set(x, FLOOR + 3, z, Material.LIGHT_GRAY_CONCRETE);
                    }
                } else if (wing) {
                    boolean tip = Math.abs(v) > 4.8;
                    canvas.set(x, FLOOR + 1, z, tip ? "minecraft:red_terracotta" : "minecraft:smooth_stone_slab[type=top]");
                    if (tip && u > -1) {
                        canvas.set(x, FLOOR + 2, z, Material.IRON_BARS);
                    }
                }
            }
        }
        int ex = (int) Math.floor(cx - ax * 4.4);
        int ez = (int) Math.floor(cz - az * 4.4);
        canvas.set(ex, FLOOR + 2, ez, Material.SHROOMLIGHT);
    }

    /** The bones of a great beast: a spine arching out of the sand, ribs on both sides and a skull. */
    private void beast(int cx, int cz, double heading) {
        double ax = Math.sin(Math.toRadians(heading));
        double az = -Math.cos(Math.toRadians(heading));
        for (double u = -9; u <= 9; u += 0.5) {
            double rise = 4.5 * Math.cos(u / 9 * Math.PI / 2);
            int x = (int) Math.floor(cx + ax * u);
            int z = (int) Math.floor(cz + az * u);
            int y = FLOOR + (int) Math.round(rise);
            canvas.set(x, y, z, Material.BONE_BLOCK);
            if (Math.abs(u % 3) < 0.25 && Math.abs(u) < 7.5) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    for (double t = 0; t <= 1; t += 0.08) {
                        double v = sign * (0.5 + 3.6 * Math.sin(t * Math.PI * 0.55));
                        double h = rise - t * (rise + 0.5);
                        int rx = (int) Math.floor(cx + ax * u - az * v);
                        int rz = (int) Math.floor(cz + az * u + ax * v);
                        canvas.set(rx, FLOOR + (int) Math.round(h), rz, Material.BONE_BLOCK);
                    }
                }
            }
        }
        int sx = (int) Math.floor(cx + ax * 11);
        int sz = (int) Math.floor(cz + az * 11);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= 2; dy++) {
                    canvas.set(sx + dx, FLOOR + dy, sz + dz, Material.BONE_BLOCK);
                }
            }
        }
        canvas.set(sx + (int) Math.round(ax * 2), FLOOR + 1, sz + (int) Math.round(az * 2), Material.BONE_BLOCK);
        canvas.set(sx - (int) Math.round(az), FLOOR + 2, sz + (int) Math.round(ax), Material.BLACK_CONCRETE);
        canvas.set(sx + (int) Math.round(az), FLOOR + 2, sz - (int) Math.round(ax), Material.BLACK_CONCRETE);
    }
}
