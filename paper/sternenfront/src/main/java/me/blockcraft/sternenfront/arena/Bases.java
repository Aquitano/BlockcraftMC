package me.blockcraft.sternenfront.arena;

import org.bukkit.Material;

import java.util.Random;

/**
 * The two headquarters at either end of the basin, built on the same plan: a ship standing on legs over the spawn
 * spots, a ramp from the map side up into its hold and a ladder through its roof, a line of cover in front with
 * three ways out (towards A, B and C) and stores along the canyon walls. The Kolonien in the west camp round a
 * patched freighter between containers and tents; the Hegemonie in the east stand on a lit white platform with a
 * dropship and a control tower. {@code e} below counts from the centre line towards the base's own end.
 */
final class Bases {

    private static final int FLOOR = TessarMap.FLOOR;
    private static final int SHIP = TessarMap.HEADQUARTERS;
    private static final int BELLY = FLOOR + 4;
    private static final int COVER = 66;

    private final Canvas canvas;
    private final Terrain terrain;
    private final Random random;

    Bases(Canvas canvas, Terrain terrain, Random random) {
        this.canvas = canvas;
        this.terrain = terrain;
        this.random = random;
    }

    void build() {
        kolonien();
        hegemonie();
    }

    private static int x(int side, double e) {
        return side * (int) Math.round(e);
    }

    private static String inward(int side) {
        return side < 0 ? "west" : "east";
    }

    private static String outward(int side) {
        return side < 0 ? "east" : "west";
    }

    private void kolonien() {
        int side = -1;
        camp(side);
        freighter(side);
        ramp(side, Material.SPRUCE_PLANKS, "spruce_stairs");
        for (int sign = -1; sign <= 1; sign += 2) {
            container(side, COVER, sign * 10, sign, Material.ORANGE_TERRACOTTA, 7);
            container(side, COVER + 1, sign * 5, sign, Material.CYAN_TERRACOTTA, 3);
            container(side, COVER + 1, sign * 13, sign, Material.RED_TERRACOTTA, 3, FLOOR + 4);
            tent(side, 72, sign * 23, sign < 0 ? Material.ORANGE_WOOL : Material.BROWN_WOOL);
            tent(side, 80, sign * 25, Material.LIGHT_GRAY_WOOL);
            fuelTank(side, 90, sign * 22);
        }
        lookout(side, 71, -14);
        lookout(side, 71, 14);
    }

    private void hegemonie() {
        int side = 1;
        platform(side);
        dropship(side);
        ramp(side, Material.SMOOTH_QUARTZ, "quartz_stairs");
        for (int sign = -1; sign <= 1; sign += 2) {
            blastWall(side, sign * 4, sign * 16);
            pod(side, 72, sign * 23);
            pod(side, 79, sign * 25);
            pylon(side, 90, sign * 22);
        }
        tower(side, 88, -27);
        tower(side, 88, 27);
    }

    /** Trampled ground, plank walkways and lanterns on posts round the Kolonien freighter. */
    private void camp(int side) {
        for (int e = 62; e <= 93; e++) {
            for (int z = -32; z <= 32; z++) {
                int x = x(side, e);
                if (terrain.cliff(x, z)) {
                    continue;
                }
                int y = terrain.height(x, z);
                double n = terrain.noise(x * 0.06 + 40, z * 0.06);
                if (n > 0.45) {
                    canvas.set(x, y, z, Material.COARSE_DIRT);
                } else if (n < -0.55) {
                    canvas.set(x, y, z, Material.PACKED_MUD);
                }
            }
        }
        for (int e = 68; e <= 76; e++) {
            for (int z = -1; z <= 1; z++) {
                canvas.set(x(side, e), FLOOR, z, Material.SPRUCE_PLANKS);
            }
        }
        for (int z = -14; z <= 14; z++) {
            canvas.set(x(side, 74), FLOOR, z, Material.STRIPPED_SPRUCE_LOG);
        }
        for (int[] post : new int[][]{{70, -4}, {70, 4}, {86, -17}, {86, 17}}) {
            int x = x(side, post[0]);
            canvas.fill(x, FLOOR + 1, post[1], x, FLOOR + 2, post[1], Material.SPRUCE_FENCE);
            canvas.set(x, FLOOR + 3, post[1], "minecraft:lantern[hanging=false]");
        }
    }

    /** A patched freighter: a tapered nose under a glass cockpit, engine nacelles on its flanks, a tail fin and three engines. */
    private void freighter(int side) {
        for (int z = -18; z <= 13; z++) {
            for (int e = SHIP - 10; e <= SHIP + 10; e++) {
                double w = e - SHIP;
                for (int y = BELLY; y <= BELLY + 11; y++) {
                    var block = freighterBlock(w, y - BELLY, z);
                    if (block != null) {
                        canvas.set(x(side, e), y, z, block);
                    }
                }
            }
        }
        for (double centre : new double[]{-3.5, 0, 3.5}) {
            engine(side, centre, BELLY + 3.5, 14, 1.9, Material.POLISHED_BLACKSTONE, Material.SHROOMLIGHT, Material.ORANGE_STAINED_GLASS);
        }
        for (double centre : new double[]{-7.6, 7.6}) {
            engine(side, centre, BELLY + 3.5, 14, 1.5, Material.GRAY_CONCRETE, Material.SHROOMLIGHT, Material.ORANGE_STAINED_GLASS);
        }
        legs(side, Material.POLISHED_BLACKSTONE_WALL, Material.IRON_BLOCK);
        hold(side);
        for (int z = -6; z <= 6; z += 4) {
            canvas.set(x(side, SHIP - 1), BELLY - 1, z, "minecraft:lantern[hanging=true]");
            canvas.set(x(side, SHIP + 2), BELLY - 1, z + 2, "minecraft:lantern[hanging=true]");
        }
        int mast = x(side, SHIP + 3);
        for (int y = BELLY + 8; y <= BELLY + 11; y++) {
            canvas.set(mast, y, -4, Material.IRON_BARS);
        }
        canvas.set(mast, BELLY + 12, -4, "minecraft:end_rod[facing=up]");
        canvas.set(x(side, SHIP - 3), BELLY + 8, 2, Material.DAYLIGHT_DETECTOR);
        canvas.set(x(side, SHIP - 3), BELLY + 8, 3, Material.BARREL);
        canvas.set(x(side, SHIP - 2), BELLY + 8, 3, Material.BARREL);
    }

    private Material freighterBlock(double w, double h, int z) {
        double taper = z < -12 ? (z + 18) / 6.0 : 1;
        double width = 6.4 * (0.35 + 0.65 * taper);
        double height = 4.0 * (0.45 + 0.55 * taper);
        boolean body = Math.pow(w / width, 2) + Math.pow((h - 3.2) / height, 2) <= 1 || (h == 0 && Math.abs(w) <= 5.2 * taper);
        double bubble = Math.pow(w / 2.6, 2) + Math.pow((z + 11) / 3.5, 2) + Math.pow((h - 7) / 2.2, 2);
        boolean nacelle = z >= 3 && Math.pow((Math.abs(w) - 7.6) / 1.9, 2) + Math.pow((h - 3.5) / 1.9, 2) <= 1;
        boolean pylon = z >= 5 && z <= 11 && Math.abs(h - 3.5) < 0.6 && Math.abs(w) > 5 && Math.abs(w) < 7;
        boolean fin = Math.abs(w) < 0.5 && z >= 6 && h >= 7 && h <= 7 + (z - 6) * 0.5;
        if (bubble <= 1 && h >= 7) {
            return bubble > 0.45 && h > 7 ? Material.GRAY_STAINED_GLASS : Material.POLISHED_ANDESITE;
        }
        if (nacelle || pylon) {
            return Math.floorMod(z, 4) == 0 ? Material.ORANGE_TERRACOTTA : Material.LIGHT_GRAY_CONCRETE;
        }
        if (fin) {
            return h > 6 + (z - 6) * 0.5 ? Material.ORANGE_TERRACOTTA : Material.GRAY_CONCRETE;
        }
        if (!body) {
            return null;
        }
        double hold = Math.pow(w / 5.0, 2) + Math.pow((h - 3.3) / 3.0, 2);
        if (h >= 1 && hold < 1 && z > -9 && z < 10) {
            return Material.AIR;
        }
        if (h == 0) {
            return (z & 3) == 0 ? Material.IRON_BLOCK : Material.POLISHED_ANDESITE;
        }
        if (Math.abs(h - 2) < 0.5 && Math.abs(w) > 4) {
            return Material.ORANGE_TERRACOTTA;
        }
        if (Math.abs(h - 4) < 0.5 && Math.abs(w) > 5.4 && Math.floorMod(z, 3) == 0 && z > -9 && z < 10) {
            return Material.BLACK_STAINED_GLASS;
        }
        if (z > 10 && h > 3 && random.nextInt(3) == 0) {
            return Material.BLACK_CONCRETE;
        }
        if (terrain.noise(w * 0.35 + z * 0.21, h * 0.4 + 90) > 0.62) {
            return Material.BROWN_TERRACOTTA;
        }
        if (Math.abs(w) < 1.5 && h > 6) {
            return Material.POLISHED_ANDESITE;
        }
        if (Math.floorMod(z, 5) == 0) {
            return Material.GRAY_CONCRETE;
        }
        return random.nextInt(14) == 0 ? Material.WHITE_TERRACOTTA : Material.LIGHT_GRAY_CONCRETE;
    }

    /** A round engine pointing south out of a ship's stern: its rim, its glow and the light in its mouth. */
    private void engine(int side, double w, double centreY, int z, double radius, Material rim, Material glow, Material mouth) {
        int r = (int) Math.ceil(radius);
        long middle = Math.round(SHIP + w);
        for (long e = middle - r - 1; e <= middle + r + 1; e++) {
            for (int y = (int) Math.floor(centreY - radius); y <= (int) Math.ceil(centreY + radius); y++) {
                double d = Math.hypot(e - (SHIP + w), y + 0.5 - centreY);
                if (d > radius) {
                    continue;
                }
                int x = x(side, e);
                canvas.set(x, y, z, d > radius - 0.9 ? rim : glow);
                canvas.set(x, y, z + 1, d > radius - 0.9 ? rim : mouth);
            }
        }
    }

    /** An angular dropship in white and steel, a light blue canopy, stub wings and two glowing engines. */
    private void dropship(int side) {
        for (int z = -17; z <= 15; z++) {
            double scale = z < -9 ? Math.max(0.15, 1 - (-9 - z) / 8.5) : 1;
            for (double e = SHIP - 9; e <= SHIP + 9; e++) {
                double w = e + 0.5 - (SHIP + 0.5);
                for (int y = BELLY; y <= BELLY + 8; y++) {
                    double h = y - BELLY;
                    double reach = (6.6 - h * 0.55) * scale;
                    boolean wing = h >= 2 && h <= 3 && z >= 3 && z <= 9 && Math.abs(w) <= 9 - (z - 3) * 0.4;
                    boolean hull = Math.abs(w) <= reach && h <= 7 * Math.min(1, scale + 0.2);
                    if (!hull && !wing) {
                        continue;
                    }
                    int x = x(side, e);
                    double hold = Math.abs(w) <= reach - 1.5 ? 1 : 0;
                    if (hull && hold > 0 && h >= 1 && h <= 5 && z > -9 && z < 10) {
                        canvas.clear(x, y, z);
                    } else if (hull && z < -8 && h >= 3 && Math.abs(w) > reach - 1.6) {
                        canvas.set(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS);
                    } else if (!hull) {
                        canvas.set(x, y, z, Math.abs(w) > 8 ? Material.LIGHT_BLUE_CONCRETE : h == 2 ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE);
                    } else {
                        canvas.set(x, y, z, dropshipPlate(w, h, z, reach));
                    }
                }
            }
        }
        for (int z = -8; z <= 9; z++) {
            canvas.set(x(side, SHIP + 6), BELLY + 2, z, z % 3 == 0 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_CONCRETE);
        }
        for (double centre : new double[]{-2.5, 2.5}) {
            engine(side, centre, BELLY + 3, 16, 2.2, Material.LIGHT_GRAY_CONCRETE, Material.SEA_LANTERN, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        legs(side, Material.LIGHT_GRAY_CONCRETE, Material.SMOOTH_QUARTZ);
        hold(side);
        for (int z = -6; z <= 6; z += 4) {
            canvas.set(x(side, SHIP - 1), BELLY, z, Material.SEA_LANTERN);
            canvas.set(x(side, SHIP + 2), BELLY, z + 2, Material.SEA_LANTERN);
        }
        int mast = x(side, SHIP + 1);
        for (int y = BELLY + 8; y <= BELLY + 10; y++) {
            canvas.set(mast, y, 2, Material.IRON_BARS);
        }
        canvas.set(mast, BELLY + 11, 2, "minecraft:end_rod[facing=up]");
    }

    private Material dropshipPlate(double w, double h, int z, double reach) {
        if (h == 0) {
            return (z & 3) == 0 ? Material.LIGHT_GRAY_CONCRETE : Material.SMOOTH_STONE;
        }
        if (Math.abs(w) > reach - 1 && h == 4 && Math.floorMod(z, 3) == 0) {
            return Material.BLACK_STAINED_GLASS;
        }
        if (h == 1) {
            return Material.LIGHT_GRAY_CONCRETE;
        }
        if (Math.abs(w) < 1.2 && h >= 6) {
            return Material.LIGHT_BLUE_CONCRETE;
        }
        return Math.floorMod(z, 5) == 0 ? Material.SMOOTH_QUARTZ : Material.WHITE_CONCRETE;
    }

    /** Four legs from the ground to the belly, at the corners of the spawn area under the ship. */
    private void legs(int side, Material leg, Material foot) {
        for (int e : new int[]{SHIP - 5, SHIP + 5}) {
            for (int z : new int[]{-11, 11}) {
                int x = x(side, e);
                canvas.set(x, FLOOR + 1, z, foot);
                canvas.fill(x, FLOOR + 2, z, x, FLOOR + 3, z, leg);
            }
        }
    }

    /** The hold: crates, a bulkhead to the engine room and the ladder through the roof. */
    private void hold(int side) {
        for (double e = SHIP - 4; e <= SHIP + 4; e++) {
            for (int y = BELLY + 1; y <= BELLY + 6; y++) {
                canvas.set(x(side, e), y, 10, Material.GRAY_CONCRETE);
            }
        }
        int lx = x(side, SHIP + 1);
        for (int y = BELLY + 1; y <= BELLY + 8; y++) {
            canvas.set(lx, y, 9, "minecraft:ladder[facing=north]");
            canvas.set(lx, y, 10, Material.GRAY_CONCRETE);
        }
        canvas.set(x(side, SHIP + 3), BELLY + 1, 7, Material.BARREL);
        canvas.set(x(side, SHIP + 3), BELLY + 1, 6, Material.BARREL);
        canvas.set(x(side, SHIP + 3), BELLY + 2, 7, Material.BARREL);
        canvas.set(x(side, SHIP - 3), BELLY + 1, -7, Material.STRIPPED_SPRUCE_LOG);
        canvas.set(x(side, SHIP + 3), BELLY + 1, -5, Material.CHEST);
        canvas.set(x(side, SHIP), BELLY + 5, 0, Material.SEA_LANTERN);
        canvas.set(x(side, SHIP), BELLY + 5, -6, Material.SEA_LANTERN);
    }

    /** The ramp from the map side up to the hold's floor, through a door in the flank. */
    private void ramp(int side, Material solid, String stairs) {
        for (int z = -2; z <= 2; z++) {
            for (int step = 0; step < 4; step++) {
                int x = x(side, SHIP - 10 + step);
                canvas.fill(x, FLOOR + 1, z, x, FLOOR + step, z, solid);
                canvas.set(x, FLOOR + 1 + step, z, "minecraft:" + stairs + "[facing=" + inward(side) + "]");
            }
            for (double e = SHIP - 6; e <= SHIP - 5; e++) {
                canvas.set(x(side, e), BELLY, z, solid);
                canvas.fill(x(side, e), BELLY + 1, z, x(side, e), BELLY + 3, z, Material.AIR);
            }
        }
    }

    /** A shipping container; {@code floor} lets one stand on another. */
    private void container(int side, int e, int z, int sign, Material colour, int length) {
        container(side, e, z, sign, colour, length, FLOOR + 1);
    }

    private void container(int side, int e, int z, int sign, Material colour, int length, int floor) {
        for (int i = 0; i < length; i++) {
            int cz = z + sign * i;
            for (int de = 0; de < 3; de++) {
                int x = x(side, e + de);
                for (int y = floor; y < floor + 3; y++) {
                    boolean end = i == 0 || i == length - 1;
                    canvas.set(x, y, cz, end ? (de == 1 && y < floor + 2 ? Material.IRON_BARS : Material.IRON_BLOCK)
                            : (cz & 1) == 0 ? colour : Material.LIGHT_GRAY_TERRACOTTA);
                }
            }
        }
    }

    /** An A-frame tent of wool on poles, ridge running towards the base. */
    private void tent(int side, int e, int z, Material cloth) {
        for (int de = 0; de < 5; de++) {
            int x = x(side, e + de);
            canvas.set(x, FLOOR + 1, z - 2, cloth);
            canvas.set(x, FLOOR + 1, z + 2, cloth);
            canvas.set(x, FLOOR + 2, z - 1, cloth);
            canvas.set(x, FLOOR + 2, z + 1, cloth);
            canvas.set(x, FLOOR + 3, z, cloth);
            if (de > 0 && de < 4) {
                canvas.set(x, FLOOR + 1, z, Material.BROWN_CARPET);
            }
        }
        canvas.set(x(side, e - 1), FLOOR + 1, z, Material.SPRUCE_FENCE);
        canvas.set(x(side, e - 1), FLOOR + 2, z, "minecraft:lantern[hanging=false]");
    }

    private void fuelTank(int side, int e, int z) {
        canvas.cylinder(x(side, e), z, FLOOR + 1, FLOOR + 6, 2.4, Material.WAXED_COPPER_BLOCK, false);
        canvas.cylinder(x(side, e), z, FLOOR + 3, FLOOR + 3, 2.4, Material.IRON_BLOCK, false);
        canvas.cylinder(x(side, e), z, FLOOR + 7, FLOOR + 7, 1.6, Material.WAXED_CUT_COPPER, false);
    }

    /** A scaffolding lookout: climbable, with a plank platform and a rail. */
    private void lookout(int side, int e, int z) {
        int x = x(side, e);
        for (int y = FLOOR + 1; y <= FLOOR + 7; y++) {
            canvas.set(x, y, z, Material.SCAFFOLDING);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    canvas.set(x + dx, FLOOR + 7, z + dz, "minecraft:spruce_slab[type=top]");
                    canvas.set(x + dx, FLOOR + 8, z + dz, Material.SPRUCE_FENCE);
                }
            }
        }
        canvas.clear(x(side, e - 1), FLOOR + 8, z);
    }

    /** The white landing platform with its grid of lights and a light blue circle under the ship. */
    private void platform(int side) {
        for (int e = 62; e <= 93; e++) {
            for (int z = -32; z <= 32; z++) {
                int x = x(side, e);
                if (terrain.cliff(x, z) || terrain.cliff(x + 1, z) || terrain.cliff(x - 1, z) || terrain.cliff(x, z + 1) || terrain.cliff(x, z - 1)) {
                    continue;
                }
                double ring = Math.hypot(e - SHIP, z);
                Material floor;
                if (e == 62 || Math.abs(z) >= 31) {
                    floor = Material.SMOOTH_QUARTZ;
                } else if (Math.abs(ring - 11) < 0.6) {
                    floor = Material.LIGHT_BLUE_CONCRETE;
                } else if (e % 6 == 0 && z % 6 == 0) {
                    floor = Material.SEA_LANTERN;
                } else if (e % 6 == 0 || z % 6 == 0) {
                    floor = Material.LIGHT_GRAY_CONCRETE;
                } else {
                    floor = random.nextInt(12) == 0 ? Material.SMOOTH_STONE : Material.WHITE_CONCRETE;
                }
                canvas.set(x, FLOOR, z, floor);
                canvas.fill(x, FLOOR + 1, z, x, FLOOR + 6, z, Material.AIR);
            }
        }
        for (int z = -29; z <= 29; z += 4) {
            canvas.set(x(side, 62), FLOOR + 1, z, Material.QUARTZ_PILLAR);
            canvas.set(x(side, 62), FLOOR + 2, z, "minecraft:end_rod[facing=up]");
        }
    }

    /** Front cover of the Hegemonie: white blast walls with a light blue window band and lamps on top. */
    private void blastWall(int side, int from, int to) {
        int step = Integer.signum(to - from);
        for (int z = from; z != to + step; z += step) {
            boolean post = Math.floorMod(z, 4) == 0;
            for (int de = 0; de < 2; de++) {
                int x = x(side, COVER + de);
                for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                    canvas.set(x, y, z, post ? Material.QUARTZ_PILLAR
                            : y == FLOOR + 2 && de == 0 && Math.floorMod(z, 2) == 0 ? Material.LIGHT_BLUE_STAINED_GLASS
                            : de == 1 ? Material.LIGHT_GRAY_CONCRETE : Material.WHITE_CONCRETE);
                }
            }
            if (post) {
                canvas.set(x(side, COVER), FLOOR + 4, z, "minecraft:end_rod[facing=up]");
            }
        }
    }

    /** A rounded cargo pod in white with a light blue stripe. */
    private void pod(int side, int e, int z) {
        for (int de = 0; de < 5; de++) {
            int x = x(side, e + de);
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                    boolean corner = Math.abs(dz) == 1 && y == FLOOR + 3;
                    canvas.set(x, y, z + dz, corner ? Material.SMOOTH_QUARTZ_SLAB
                            : y == FLOOR + 2 ? Material.LIGHT_BLUE_CONCRETE : Material.WHITE_CONCRETE);
                }
            }
        }
    }

    /** A light pylon: quartz with a lantern head. */
    private void pylon(int side, int e, int z) {
        int x = x(side, e);
        canvas.fill(x, FLOOR + 1, z, x, FLOOR + 6, z, Material.QUARTZ_PILLAR);
        canvas.set(x, FLOOR + 7, z, Material.SEA_LANTERN);
        canvas.set(x, FLOOR + 8, z, "minecraft:end_rod[facing=up]");
    }

    /** The control tower at the platform's corner, glass on top, out of reach. */
    private void tower(int side, int e, int z) {
        int cx = x(side, e);
        int dz = Integer.signum(z);
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                int x = cx + ox;
                int tz = z - dz * 2 + oz;
                if (terrain.cliff(x, tz)) {
                    continue;
                }
                boolean edge = Math.abs(ox) == 2 || Math.abs(oz) == 2;
                for (int y = FLOOR + 1; y <= FLOOR + 15; y++) {
                    Material block;
                    if (y >= FLOOR + 12 && y <= FLOOR + 13) {
                        block = edge ? Material.LIGHT_BLUE_STAINED_GLASS : Material.AIR;
                    } else if (y == FLOOR + 14 || y == FLOOR + 15) {
                        block = y == FLOOR + 15 && edge ? Material.SMOOTH_QUARTZ_SLAB : Material.SMOOTH_QUARTZ;
                    } else {
                        block = Math.abs(ox) == 2 && Math.abs(oz) == 2 ? Material.QUARTZ_PILLAR : Material.WHITE_CONCRETE;
                    }
                    canvas.set(x, y, tz, block);
                }
            }
        }
        canvas.set(cx, FLOOR + 16, z - dz * 2, Material.IRON_BARS);
        canvas.set(cx, FLOOR + 17, z - dz * 2, Material.IRON_BARS);
        canvas.set(cx, FLOOR + 18, z - dz * 2, "minecraft:end_rod[facing=up]");
        canvas.set(cx, FLOOR + 12, z - dz * 2, Material.SEA_LANTERN);
    }
}
