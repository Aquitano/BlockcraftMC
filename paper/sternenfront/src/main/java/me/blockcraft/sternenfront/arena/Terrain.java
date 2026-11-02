package me.blockcraft.sternenfront.arena;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.util.noise.SimplexNoiseGenerator;

import java.util.List;
import java.util.Random;

/**
 * The ground of Tessar: a sandy basin with low dunes, closed in by canyon walls of banded rock that narrow round
 * either headquarters. Wherever a building stands, the dunes flatten out first. The cliffs rise sheer for
 * {@link #FACE} blocks, and an invisible wall stands in front of every face from below the lowest ledge up, so no
 * leap from the highest roof lands on top of them.
 */
final class Terrain {

    static final int MIN_X = -96;
    static final int MAX_X = 96;
    static final int MIN_Z = -70;
    static final int MAX_Z = 70;
    static final int BASE = TessarMap.FLOOR - 6;
    static final int FACE = 17;
    private static final int RIM_FROM = 11;
    private static final int RIM_TO = 46;

    private static final Material[] STRATA = {
            Material.SANDSTONE, Material.SANDSTONE, Material.SMOOTH_SANDSTONE, Material.ORANGE_TERRACOTTA,
            Material.ORANGE_TERRACOTTA, Material.TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.WHITE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.RED_SANDSTONE, Material.SMOOTH_RED_SANDSTONE,
            Material.ORANGE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.SANDSTONE, Material.CUT_SANDSTONE, Material.BROWN_TERRACOTTA, Material.RED_TERRACOTTA};

    /** A circle of ground the dunes leave flat: full inside {@code radius}, fading out over {@code fade}. */
    record Flat(double x, double z, double radius, double fade) {
    }

    private final Canvas canvas;
    private final SimplexNoiseGenerator noise;
    private final Random random;
    private final List<Flat> flats;
    private final int[][] heights = new int[MAX_X - MIN_X + 1][MAX_Z - MIN_Z + 1];

    Terrain(Canvas canvas, long seed, List<Flat> flats) {
        this.canvas = canvas;
        this.noise = new SimplexNoiseGenerator(seed);
        this.random = new Random(seed);
        this.flats = flats;
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                heights[x - MIN_X][z - MIN_Z] = computeHeight(x, z);
            }
        }
    }

    int height(int x, int z) {
        if (x < MIN_X || x > MAX_X || z < MIN_Z || z > MAX_Z) {
            return TessarMap.FLOOR + FACE + 10;
        }
        return heights[x - MIN_X][z - MIN_Z];
    }

    boolean cliff(int x, int z) {
        return height(x, z) >= TessarMap.FLOOR + FACE;
    }

    double noise(double x, double z) {
        return noise.noise(x, z);
    }

    /** How far the column lies inside the canyon wall; zero or less on the basin floor. */
    double depth(double x, double z) {
        double ax = Math.abs(x);
        double narrow = 26 * smooth((ax - 56) / 34);
        double ragged = 2.6 * noise(x * 0.06, 11.5) + 1.4 * noise(x * 0.17, 31.7);
        double straight = smooth((ax - 9) / 7);
        double north = -58 + narrow + ragged * straight;
        double south = 58 - narrow - (2.6 * noise(x * 0.06, 57.3) + 1.4 * noise(x * 0.17, 77.1));
        double west = -93 + 1.6 * noise(z * 0.09, 91.9);
        double east = 93 + 1.6 * noise(z * 0.09, 121.3);
        return Math.max(Math.max(north - z, z - south), Math.max(west - x, x - east));
    }

    private int computeHeight(int x, int z) {
        double depth = depth(x + 0.5, z + 0.5);
        int floor = TessarMap.FLOOR;
        if (depth > 0) {
            double terrace = depth * 1.5 + 5 * noise(x * 0.07, z * 0.07);
            return floor + FACE + (int) Math.min(17, Math.max(0, Math.floor(terrace / 3) * 3));
        }
        double scree = Math.max(0, 3 + depth) * 0.9;
        double dunes = Math.max(0, noise(x * 0.028, z * 0.045)) * 3.4 + (noise(x * 0.11, z * 0.11) + 1) * 0.35;
        return floor + (int) Math.round(scree * open(x, z) + dunes * open(x, z));
    }

    /** One where the dunes may rise, falling to zero towards a flat. */
    double open(double x, double z) {
        double open = 1;
        for (var flat : flats) {
            double distance = Math.hypot(x - flat.x(), z - flat.z());
            open = Math.min(open, smooth((distance - flat.radius()) / flat.fade()));
        }
        return open;
    }

    void build() {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                int top = height(x, z);
                boolean cliff = cliff(x, z);
                double wobble = 2.2 * noise(x * 0.045, z * 0.045);
                for (int y = BASE; y <= top; y++) {
                    canvas.set(x, y, z, cliff ? stratum(y, wobble, top) : ground(x, y, z, top, wobble));
                }
            }
        }
    }

    /** The banded rock of the canyon at a spot, for anything that stands out of the ground like the cliffs. */
    Material rock(int x, int y, int z) {
        return stratum(y, 2.2 * noise(x * 0.045, z * 0.045), Integer.MAX_VALUE);
    }

    private Material stratum(int y, double wobble, int top) {
        if (y == top) {
            return random.nextInt(5) == 0 ? Material.SMOOTH_SANDSTONE : Material.SANDSTONE;
        }
        int band = Math.floorDiv(y + (int) Math.round(wobble), 2);
        return STRATA[Math.floorMod(band, STRATA.length)];
    }

    private Material ground(int x, int y, int z, int top, double wobble) {
        if (y < top - 2) {
            return stratum(y, wobble, Integer.MAX_VALUE);
        }
        if (y < top) {
            return Material.SANDSTONE;
        }
        double patch = noise(x * 0.07 + 300, z * 0.07);
        if (patch > 0.62) {
            return random.nextBoolean() ? Material.SMOOTH_SANDSTONE : Material.SANDSTONE;
        }
        double rust = noise(x * 0.05 + 500, z * 0.05) + Math.max(0, depth(x + 0.5, z + 0.5) + 8) * 0.12;
        return rust > 0.55 ? Material.RED_SAND : Material.SAND;
    }

    /** Dry grass and dead bushes on open sand, away from where anyone builds. */
    void plant() {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                if (cliff(x, z) || open(x, z) < 0.6 || random.nextInt(100) >= 4) {
                    continue;
                }
                int top = height(x, z);
                var ground = canvas.type(x, top, z);
                if ((ground == Material.SAND || ground == Material.RED_SAND) && canvas.isAir(x, top + 1, z)) {
                    int pick = random.nextInt(10);
                    if (pick < 4) {
                        canvas.set(x, top + 1, z, Material.SHORT_DRY_GRASS);
                    } else if (pick < 6 && canvas.isAir(x, top + 2, z)) {
                        canvas.set(x, top + 1, z, Material.TALL_DRY_GRASS);
                    } else {
                        canvas.set(x, top + 1, z, Material.DEAD_BUSH);
                    }
                }
            }
        }
    }

    /** The invisible wall before every cliff face. */
    void rim() {
        int floor = TessarMap.FLOOR;
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                if (cliff(x, z) || !nextToCliff(x, z)) {
                    continue;
                }
                for (int y = floor + RIM_FROM; y <= floor + RIM_TO; y++) {
                    canvas.setIfAir(x, y, z, Material.BARRIER);
                }
            }
        }
    }

    private boolean nextToCliff(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && cliff(x + dx, z + dz)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Sand left without a full block under it, where a building cut into a dune, turns to sandstone and stays put. */
    void settle() {
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                for (int y = TessarMap.FLOOR - 1; y <= TessarMap.FLOOR + 10; y++) {
                    var type = canvas.type(x, y, z);
                    if (type.hasGravity() && !canvas.type(x, y - 1, z).isSolid()) {
                        canvas.set(x, y, z, type == Material.RED_SAND ? Material.RED_SANDSTONE : Material.SANDSTONE);
                    }
                }
            }
        }
    }

    /** Every column of the map in the desert biome, for its sky and its colours. */
    void desert() {
        var world = canvas.world();
        for (int x = MIN_X; x <= MAX_X; x += 4) {
            for (int z = MIN_Z; z <= MAX_Z; z += 4) {
                for (int y = BASE; y <= TessarMap.FLOOR + 64; y += 4) {
                    world.setBiome(x, y, z, Biome.DESERT);
                }
            }
        }
    }

    static double smooth(double t) {
        double clamped = Math.clamp(t, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }
}
