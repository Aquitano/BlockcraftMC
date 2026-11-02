package me.blockcraft.sternenfront.arena;

import org.bukkit.Material;

import java.util.Random;

/**
 * Post A, the Raffinerie under the northern cliff: a paved yard round the post between two copper storage tanks
 * whose rims carry a catwalk high over the yard, two pressure tanks lying on saddles to the south, control houses
 * on either flank, pipes on racks and two smoking stacks against the rock. Mirrored about the centre line.
 */
final class Refinery {

    private static final int FLOOR = TessarMap.FLOOR;
    private static final int Z = TessarMap.POST_A;
    private static final int CATWALK = FLOOR + 9;

    private final Canvas canvas;
    private final Random random;

    Refinery(Canvas canvas, Random random) {
        this.canvas = canvas;
        this.random = random;
    }

    void build() {
        yard();
        for (int side = -1; side <= 1; side += 2) {
            storageTank(side * 15, Z - 3, side);
            pressureTank(side * 11, Z + 8);
            controlHouse(side * 25, Z + 4, side);
            stack(side * 27, Z - 9);
            pipes(side);
            clutter(side);
        }
        catwalk();
    }

    private void yard() {
        for (int x = -13; x <= 13; x++) {
            for (int z = Z - 10; z <= Z + 11; z++) {
                double d = Math.hypot(x, (z - Z) * 1.1);
                if (d > 12.5) {
                    continue;
                }
                Material paving;
                if (d > 11.5) {
                    paving = (x + z) % 2 == 0 ? Material.YELLOW_TERRACOTTA : Material.BLACK_CONCRETE;
                } else if (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) {
                    paving = Material.POLISHED_ANDESITE;
                } else {
                    paving = random.nextInt(7) == 0 ? Material.STONE : Material.SMOOTH_STONE;
                }
                canvas.set(x, FLOOR, z, paving);
                canvas.fill(x, FLOOR + 1, z, x, FLOOR + 3, z, Material.AIR);
            }
        }
    }

    /** A copper tank on a concrete footing, banded with iron, a dome on top and a railed rim to walk round. */
    private void storageTank(int cx, int cz, int side) {
        double radius = 4.5;
        canvas.cylinder(cx, cz, FLOOR, FLOOR, radius + 1, Material.POLISHED_ANDESITE, false);
        for (int y = FLOOR + 1; y <= CATWALK; y++) {
            Material band = y == FLOOR + 3 || y == FLOOR + 7 ? Material.IRON_BLOCK
                    : y == CATWALK ? Material.WAXED_CUT_COPPER
                    : (y % 3 == 0 ? Material.WAXED_COPPER_BLOCK : Material.WAXED_EXPOSED_COPPER);
            canvas.cylinder(cx, cz, y, y, radius, band, false);
        }
        canvas.dome(cx, CATWALK + 1, cz, 3.2, Material.WAXED_EXPOSED_CUT_COPPER, false);
        canvas.set(cx, CATWALK + 5, cz, Material.WAXED_LIGHTNING_ROD);
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= radius && d > radius - 1.1 && !(dz < 0 && Math.abs(dx) < 2)) {
                    canvas.set(cx + dx, CATWALK + 1, cz + dz, Material.IRON_BARS);
                }
            }
        }
        int ladderX = cx - side * 5;
        for (int y = FLOOR + 1; y <= CATWALK; y++) {
            canvas.set(ladderX, y, cz, "minecraft:ladder[facing=" + (side > 0 ? "west" : "east") + "]");
        }
        canvas.clear(ladderX + side, CATWALK + 1, cz);
        canvas.set(cx - side * 3, FLOOR + 5, cz + 4, Material.WAXED_COPPER_BULB);
    }

    /** A pressure tank lying east-west on two saddles, low cover at the south of the yard. */
    private void pressureTank(int cx, int cz) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            boolean end = Math.abs(x - cx) == 3;
            for (int y = FLOOR + 1; y <= FLOOR + 5; y++) {
                for (int z = cz - 2; z <= cz + 2; z++) {
                    double d = Math.hypot(y - (FLOOR + 3), z - cz);
                    if (d <= (end ? 1.6 : 2.4)) {
                        canvas.set(x, y, z, end ? Material.IRON_BLOCK : (x == cx ? Material.WHITE_TERRACOTTA : Material.LIGHT_GRAY_TERRACOTTA));
                    }
                }
            }
        }
        for (int x : new int[]{cx - 2, cx + 2}) {
            canvas.set(x, FLOOR + 1, cz - 2, "minecraft:polished_deepslate_stairs[facing=south]");
            canvas.set(x, FLOOR + 1, cz + 2, "minecraft:polished_deepslate_stairs[facing=north]");
        }
        canvas.set(cx, FLOOR + 6, cz, Material.WAXED_LIGHTNING_ROD);
    }

    /** The control house: a flat concrete block with windows on the yard, consoles inside and a door each way. */
    private void controlHouse(int cx, int cz, int side) {
        int x1 = cx - 3;
        int x2 = cx + 3;
        int z1 = cz - 4;
        int z2 = cz + 3;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                canvas.set(x, FLOOR, z, Material.POLISHED_ANDESITE);
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                    if (!wall) {
                        canvas.clear(x, y, z);
                    } else if (y == FLOOR + 3 && (x + z) % 2 != 0 && (x == cx - side * 3)) {
                        canvas.set(x, y, z, Material.GLASS_PANE);
                    } else {
                        boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                        canvas.set(x, y, z, corner ? Material.POLISHED_DEEPSLATE : y == FLOOR + 1 ? Material.GRAY_CONCRETE : Material.LIGHT_GRAY_CONCRETE);
                    }
                }
                canvas.set(x, FLOOR + 5, z, wall ? Material.SMOOTH_STONE_SLAB : Material.SMOOTH_STONE);
            }
        }
        int door = cx - side * 3;
        canvas.fill(door, FLOOR + 1, cz - 1, door, FLOOR + 2, cz, Material.AIR);
        canvas.fill(cx, FLOOR + 1, z2, cx + side, FLOOR + 2, z2, Material.AIR);
        canvas.set(cx, FLOOR + 4, cz, Material.SEA_LANTERN);
        canvas.set(cx + side * 2, FLOOR + 1, cz - 3, "minecraft:observer[facing=up]");
        canvas.set(cx + side, FLOOR + 1, cz - 3, Material.SMITHING_TABLE);
        canvas.set(cx, FLOOR + 1, cz - 3, "minecraft:observer[facing=up]");
        canvas.set(cx + side * 2, FLOOR + 1, cz + 1, Material.BARREL);
        canvas.set(cx + side * 2, FLOOR + 2, cz + 1, Material.BARREL);
        canvas.set(cx + side, FLOOR + 6, cz - 2, Material.IRON_BARS);
        canvas.set(cx + side, FLOOR + 7, cz - 2, Material.DAYLIGHT_DETECTOR);
        canvas.set(cx - side, FLOOR + 6, cz + 1, Material.WAXED_LIGHTNING_ROD);
    }

    /** A brick stack against the cliff, banded red and white, with a signal fire at the top for the smoke. */
    private void stack(int cx, int cz) {
        int top = FLOOR + 27;
        for (int y = FLOOR + 1; y <= top; y++) {
            Material band = y > top - 3 ? Material.IRON_BLOCK : (y / 3) % 2 == 0 ? Material.BRICKS : Material.WHITE_TERRACOTTA;
            canvas.cylinder(cx, cz, y, y, 2.3, y < FLOOR + 4 ? Material.POLISHED_DEEPSLATE : band, false);
        }
        canvas.set(cx, top - 1, cz, Material.HAY_BLOCK);
        canvas.set(cx, top, cz, "minecraft:campfire[lit=true,signal_fire=true]");
        canvas.cylinder(cx, cz, FLOOR + 1, FLOOR + 3, 3.3, Material.POLISHED_DEEPSLATE, true);
    }

    /** Pipes on racks between each storage tank, its pressure tank and its control house. */
    private void pipes(int side) {
        int rack = FLOOR + 5;
        for (int x = side * 11; side > 0 ? x <= 22 : x >= -22; x += side) {
            if (Math.abs(x) % 4 == 2) {
                for (int y = FLOOR + 1; y < rack; y++) {
                    canvas.set(x, y, Z + 3, Material.POLISHED_DEEPSLATE_WALL);
                }
            }
            canvas.set(x, rack, Z + 3, Math.abs(x) % 4 == 0 ? Material.IRON_BLOCK : Material.WAXED_CUT_COPPER);
            canvas.set(x, rack + 1, Z + 3, Math.abs(x) % 2 == 0 ? Material.WAXED_COPPER_GRATE : Material.AIR);
        }
        canvas.line(side * 14, FLOOR + 6, Z + 1, side * 14, FLOOR + 6, Z + 5, Material.WAXED_COPPER_BLOCK);
        canvas.line(side * 13, FLOOR + 6, Z + 5, side * 13, FLOOR + 6, Z + 6, Material.WAXED_COPPER_BLOCK);
        for (int z = Z - 1; z <= Z + 5; z++) {
            canvas.set(side * 21, FLOOR + 2, z, Material.WAXED_EXPOSED_CUT_COPPER);
        }
        canvas.set(side * 21, FLOOR + 1, Z - 1, Material.POLISHED_DEEPSLATE_WALL);
        canvas.set(side * 21, FLOOR + 1, Z + 5, Material.POLISHED_DEEPSLATE_WALL);
    }

    private void clutter(int side) {
        int[][] barrels = {{side * 7, Z - 9}, {side * 8, Z - 9}, {side * 7, Z - 8}, {side * 19, Z + 9}, {side * 20, Z + 9}, {side * 19, Z + 10}};
        for (var barrel : barrels) {
            canvas.set(barrel[0], FLOOR + 1, barrel[1], Material.BARREL);
        }
        canvas.set(side * 7, FLOOR + 2, Z - 9, Material.BARREL);
        canvas.fill(side * 17, FLOOR + 1, Z + 13, side * 18, FLOOR + 1, Z + 14, Material.STRIPPED_SPRUCE_LOG);
        canvas.set(side * 17, FLOOR + 2, Z + 13, Material.SCAFFOLDING);
        canvas.set(side * 4, FLOOR + 1, Z + 11, Material.YELLOW_TERRACOTTA);
        canvas.set(side * 5, FLOOR + 1, Z + 11, Material.BLACK_CONCRETE);
    }

    /** The walkway from rim to rim along the north of the yard, on legs, with rails. */
    private void catwalk() {
        int z1 = Z - 8;
        int z2 = Z - 7;
        for (int x = -13; x <= 13; x++) {
            for (int z = z1; z <= z2; z++) {
                canvas.set(x, CATWALK, z, "minecraft:iron_trapdoor[half=top]");
            }
            if (Math.abs(x) <= 10) {
                canvas.set(x, CATWALK + 1, z1 - 1, Material.IRON_BARS);
                canvas.set(x, CATWALK + 1, z2 + 1, Material.IRON_BARS);
            }
            if (Math.abs(x) % 6 == 4) {
                canvas.set(x, CATWALK - 1, z1, Material.POLISHED_DEEPSLATE);
                for (int y = FLOOR + 1; y < CATWALK - 1; y++) {
                    canvas.set(x, y, z1, Material.POLISHED_DEEPSLATE_WALL);
                }
            }
            if (Math.abs(x) % 8 == 3) {
                canvas.set(x, CATWALK - 1, z2, "minecraft:lantern[hanging=true]");
            }
        }
    }
}
