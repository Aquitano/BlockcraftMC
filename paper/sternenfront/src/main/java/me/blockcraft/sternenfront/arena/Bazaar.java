package me.blockcraft.sternenfront.arena;

import org.bukkit.Material;

import java.util.Random;

/**
 * Post C, the Basar under the southern cliff: a paved market square round the post, stalls under striped awnings at
 * its edge, the cantina to the west and a warehouse to the east under big sandstone domes, small dome houses on
 * the square's corners and walled alleys between them. The big domes have doors on three sides, so the fight can
 * run through them.
 */
final class Bazaar {

    private static final int FLOOR = TessarMap.FLOOR;
    private static final int Z = TessarMap.POST_C;
    private static final Material[][] AWNINGS = {
            {Material.RED_WOOL, Material.YELLOW_WOOL}, {Material.GREEN_WOOL, Material.LIME_WOOL},
            {Material.PURPLE_WOOL, Material.MAGENTA_WOOL}, {Material.BLUE_WOOL, Material.CYAN_WOOL}};

    private final Canvas canvas;
    private final Random random;

    Bazaar(Canvas canvas, Random random) {
        this.canvas = canvas;
        this.random = random;
    }

    void build() {
        square();
        dome(-20, Z + 1, 7, new int[]{0, 180, 270});
        dome(20, Z + 1, 7, new int[]{180, 0, 270});
        cantina(-20, Z + 1);
        warehouse(20, Z + 1);
        for (int side = -1; side <= 1; side += 2) {
            dome(side * 12, Z - 8, 3.5, new int[]{side < 0 ? 0 : 180});
            dome(side * 12, Z + 8, 3.5, new int[]{270 - side * 45});
            room(side * 12, Z - 8);
            room(side * 12, Z + 8);
            alley(side);
        }
        int stall = 0;
        for (int side = -1; side <= 1; side += 2) {
            for (int dz : new int[]{-6, 6}) {
                stall(side * 6, Z + dz, side, AWNINGS[stall++]);
            }
        }
        gate();
        drifts();
    }

    private void square() {
        for (int x = -12; x <= 12; x++) {
            for (int z = Z - 12; z <= Z + 12; z++) {
                double d = Math.hypot(x, z - Z);
                if (d > 11.5) {
                    continue;
                }
                Material paving;
                if (d > 10.5) {
                    paving = Material.CUT_SANDSTONE;
                } else if (Math.abs(d - 4) < 0.5 || Math.abs(d - 9) < 0.5) {
                    paving = Material.BROWN_TERRACOTTA;
                } else if ((Math.floorMod(x, 3) == 0) != (Math.floorMod(z, 3) == 0)) {
                    paving = Material.SMOOTH_SANDSTONE;
                } else {
                    paving = random.nextInt(5) == 0 ? Material.YELLOW_TERRACOTTA : Material.SANDSTONE;
                }
                canvas.set(x, FLOOR, z, paving);
                canvas.fill(x, FLOOR + 1, z, x, FLOOR + 3, z, Material.AIR);
            }
        }
    }

    /** A round house under a dome; {@code doors} are compass angles (0 east, 90 south, 180 west, 270 north). */
    private void dome(int cx, int cz, double radius, int[] doors) {
        int r = (int) Math.ceil(radius);
        int wallTop = FLOOR + 4;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > radius + 0.3) {
                    continue;
                }
                canvas.set(cx + dx, FLOOR, cz + dz, d < radius - 1 ? Material.SMOOTH_SANDSTONE : Material.CUT_SANDSTONE);
                for (int y = FLOOR + 1; y <= wallTop + r + 1; y++) {
                    double shell = y <= wallTop ? d : Math.sqrt(d * d + (y - wallTop) * (y - wallTop));
                    if (shell > radius + 0.3) {
                        continue;
                    }
                    boolean skin = shell > radius - 0.9;
                    if (!skin) {
                        canvas.clear(cx + dx, y, cz + dz);
                    } else if (y == FLOOR + 1) {
                        canvas.set(cx + dx, y, cz + dz, Material.CUT_SANDSTONE);
                    } else if (y <= wallTop) {
                        canvas.set(cx + dx, y, cz + dz, y == wallTop ? Material.CHISELED_SANDSTONE : Material.SMOOTH_SANDSTONE);
                    } else {
                        canvas.set(cx + dx, y, cz + dz, y >= wallTop + r - 1 ? Material.CUT_SANDSTONE
                                : random.nextInt(9) == 0 ? Material.SANDSTONE : Material.SMOOTH_SANDSTONE);
                    }
                }
            }
        }
        for (int angle = 0; angle < 360; angle += 45) {
            boolean door = false;
            for (int open : doors) {
                door |= open == angle;
            }
            double rad = Math.toRadians(angle);
            int wx = cx + (int) Math.round(Math.cos(rad) * (radius - 0.4));
            int wz = cz + (int) Math.round(Math.sin(rad) * (radius - 0.4));
            if (door) {
                boolean alongX = Math.abs(Math.cos(rad)) < 0.5;
                for (int i = -1; i <= 1; i++) {
                    int x = wx + (alongX ? i : 0);
                    int z = wz + (alongX ? 0 : i);
                    for (int t = 0; t <= 1; t++) {
                        int px = x + (alongX ? 0 : (int) -Math.signum(Math.cos(rad)) * t);
                        int pz = z + (alongX ? (int) -Math.signum(Math.sin(rad)) * t : 0);
                        canvas.fill(px, FLOOR + 1, pz, px, FLOOR + 3, pz, Material.AIR);
                    }
                }
                int lx = wx + (int) Math.round(Math.cos(rad));
                int lz = wz + (int) Math.round(Math.sin(rad));
                canvas.set(lx, FLOOR + 4, lz, Material.CUT_SANDSTONE_SLAB);
            } else if (radius > 5 || angle % 90 != 0) {
                canvas.set(wx, FLOOR + 3, wz, Material.IRON_BARS);
            }
        }
        canvas.set(cx, wallTop + r - 1, cz, Material.CHISELED_SANDSTONE);
        canvas.set(cx, wallTop + r - 2, cz, Material.IRON_CHAIN);
        canvas.set(cx, wallTop + r - 3, cz, "minecraft:lantern[hanging=true]");
        if (radius > 5) {
            canvas.set(cx, wallTop + r + 1, cz, Material.WAXED_LIGHTNING_ROD);
            canvas.set(cx + 1, wallTop + r + 1, cz, Material.CUT_SANDSTONE_SLAB);
        }
    }

    private void cantina(int cx, int cz) {
        for (int angle = 100; angle <= 260; angle += 8) {
            double rad = Math.toRadians(angle);
            int x = cx + 1 + (int) Math.round(Math.cos(rad) * 3);
            int z = cz + (int) Math.round(Math.sin(rad) * 3);
            canvas.set(x, FLOOR + 1, z, Material.STRIPPED_SPRUCE_LOG);
            canvas.set(x, FLOOR + 2, z, "minecraft:spruce_slab[type=bottom]");
        }
        canvas.set(cx - 1, FLOOR + 1, cz, Material.BARREL);
        canvas.set(cx - 1, FLOOR + 2, cz, Material.BARREL);
        canvas.set(cx - 1, FLOOR + 1, cz + 1, Material.BARREL);
        canvas.set(cx - 1, FLOOR + 1, cz - 1, Material.DECORATED_POT);
        canvas.set(cx - 2, FLOOR + 1, cz, Material.BREWING_STAND);
        for (int[] stool : new int[][]{{cx + 4, cz - 2}, {cx + 4, cz + 2}, {cx + 5, cz}}) {
            canvas.set(stool[0], FLOOR + 1, stool[1], Material.POLISHED_BLACKSTONE_WALL);
        }
        for (int[] table : new int[][]{{cx - 3, cz + 4}, {cx + 2, cz + 5}, {cx - 3, cz - 4}}) {
            canvas.set(table[0], FLOOR + 1, table[1], Material.SPRUCE_FENCE);
            canvas.set(table[0], FLOOR + 2, table[1], Material.SPRUCE_PRESSURE_PLATE);
            canvas.set(table[0] + 1, FLOOR + 1, table[1], "minecraft:spruce_stairs[facing=east]");
        }
        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                if (canvas.isAir(x, FLOOR + 1, z) && Math.abs(x - cx - 3) <= 1) {
                    canvas.set(x, FLOOR + 1, z, Material.RED_CARPET);
                }
            }
        }
        canvas.set(cx + 1, FLOOR + 6, cz - 3, "minecraft:lantern[hanging=true]");
        canvas.set(cx + 1, FLOOR + 6, cz + 3, "minecraft:lantern[hanging=true]");
        canvas.set(cx + 1, FLOOR + 7, cz - 3, Material.IRON_CHAIN);
        canvas.set(cx + 1, FLOOR + 7, cz + 3, Material.IRON_CHAIN);
    }

    private void warehouse(int cx, int cz) {
        for (int[] stack : new int[][]{{cx + 3, cz - 3, 3}, {cx + 4, cz - 2, 2}, {cx + 3, cz + 3, 2}, {cx + 4, cz + 2, 3}, {cx - 2, cz + 4, 1}}) {
            for (int y = 1; y <= stack[2]; y++) {
                canvas.set(stack[0], FLOOR + y, stack[1], y % 2 == 0 ? Material.STRIPPED_OAK_LOG : Material.BARREL);
            }
        }
        canvas.fill(cx - 4, FLOOR + 1, cz - 3, cx - 4, FLOOR + 3, cz - 2, Material.SCAFFOLDING);
        canvas.set(cx, FLOOR + 1, cz - 4, Material.CHEST);
        canvas.set(cx + 1, FLOOR + 1, cz - 4, Material.HAY_BLOCK);
        canvas.set(cx, FLOOR + 8, cz, Material.IRON_CHAIN);
        canvas.set(cx, FLOOR + 7, cz, Material.IRON_CHAIN);
        canvas.set(cx, FLOOR + 6, cz, Material.HEAVY_CORE);
    }

    private void room(int cx, int cz) {
        canvas.set(cx, FLOOR + 1, cz, Material.BROWN_CARPET);
        canvas.set(cx + 1, FLOOR + 1, cz + 1, Material.BARREL);
        canvas.set(cx - 1, FLOOR + 1, cz + 1, Material.FLOWER_POT);
        canvas.set(cx + 1, FLOOR + 1, cz - 1, Material.DECORATED_POT);
    }

    /** Lamps along the square and crates in the gaps between the houses, cover for whoever comes in from the side. */
    private void alley(int side) {
        lampPost(side * 12, Z);
        lampPost(side * 6, Z + 11);
        for (int[] crate : new int[][]{{side * 16, Z - 6}, {side * 16, Z - 5}, {side * 15, Z - 6}, {side * 16, Z + 9}, {side * 17, Z + 9}}) {
            canvas.set(crate[0], FLOOR + 1, crate[1], crate[1] == Z - 5 ? Material.STRIPPED_SPRUCE_LOG : Material.BARREL);
        }
        canvas.set(side * 16, FLOOR + 2, Z - 6, Material.BARREL);
    }

    private void lampPost(int x, int z) {
        canvas.set(x, FLOOR + 1, z, Material.CHISELED_SANDSTONE);
        canvas.set(x, FLOOR + 2, z, Material.SANDSTONE_WALL);
        canvas.set(x, FLOOR + 3, z, Material.SANDSTONE_WALL);
        canvas.set(x, FLOOR + 4, z, "minecraft:lantern[hanging=false]");
    }

    /** A market stall facing the square: counter, posts and an awning in two striped colours. */
    private void stall(int cx, int cz, int side, Material[] stripes) {
        int front = cx - side;
        int back = cx + side;
        for (int z = cz - 1; z <= cz + 1; z++) {
            canvas.set(front, FLOOR + 1, z, "minecraft:spruce_slab[type=top]");
            canvas.set(cx, FLOOR + 1, z, z == cz ? Material.BARREL : Material.AIR);
            canvas.set(back, FLOOR + 1, z, Material.STRIPPED_SPRUCE_LOG);
            canvas.set(front, FLOOR + 4, z, stripes[Math.floorMod(z, 2)]);
            canvas.set(cx, FLOOR + 4, z, stripes[Math.floorMod(z, 2)]);
            canvas.set(back, FLOOR + 5, z, stripes[Math.floorMod(z, 2)]);
        }
        for (int z : new int[]{cz - 1, cz + 1}) {
            canvas.fill(front, FLOOR + 2, z, front, FLOOR + 3, z, Material.SPRUCE_FENCE);
            canvas.fill(back, FLOOR + 2, z, back, FLOOR + 4, z, Material.SPRUCE_FENCE);
        }
        canvas.set(front, FLOOR + 2, cz, random.nextBoolean() ? Material.DECORATED_POT : Material.FLOWER_POT);
        canvas.set(back, FLOOR + 2, cz, Material.BARREL);
    }

    /** The arch over the way in from the wreck, hung with lanterns. */
    private void gate() {
        for (int side = -1; side <= 1; side += 2) {
            canvas.fill(side * 6, FLOOR + 1, Z - 12, side * 6, FLOOR + 5, Z - 12, Material.CUT_SANDSTONE);
            canvas.set(side * 6, FLOOR + 1, Z - 12, Material.CHISELED_SANDSTONE);
        }
        for (int x = -6; x <= 6; x++) {
            int lift = Math.abs(x) <= 2 ? 1 : 0;
            canvas.set(x, FLOOR + 6 + lift, Z - 12, Math.abs(x) == 0 ? Material.CHISELED_RED_SANDSTONE : Material.SMOOTH_RED_SANDSTONE);
            if (lift == 0 && Math.abs(x) < 6) {
                canvas.set(x, FLOOR + 7, Z - 12, "minecraft:smooth_red_sandstone_slab[type=bottom]");
            }
        }
        canvas.set(-3, FLOOR + 5, Z - 12, "minecraft:lantern[hanging=true]");
        canvas.set(3, FLOOR + 5, Z - 12, "minecraft:lantern[hanging=true]");
    }

    /** Sand blown into the corners against the house walls. */
    private void drifts() {
        for (int x = -26; x <= 26; x++) {
            for (int z = Z - 14; z <= Z + 13; z++) {
                if (random.nextInt(6) != 0 || !canvas.isAir(x, FLOOR + 1, z) || !canvas.type(x, FLOOR, z).isOccluding()) {
                    continue;
                }
                int walls = 0;
                for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    walls += canvas.type(x + step[0], FLOOR + 1, z + step[1]).isOccluding() ? 1 : 0;
                }
                if (walls >= 2 && Math.hypot(x, z - Z) > 11) {
                    canvas.set(x, FLOOR + 1, z, Material.SAND);
                }
            }
        }
    }
}
