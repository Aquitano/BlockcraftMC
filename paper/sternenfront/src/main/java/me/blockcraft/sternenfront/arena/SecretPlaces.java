package me.blockcraft.sternenfront.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Where Tessar hides its secrets. Five kyber crystals: in the sand that fills the nose of the cruiser's bow, in a slot
 * behind the temple hall, on the sand drift in a corner of the cantina, under the eastern pressure tank and on a ledge
 * in the north-eastern cliff that only a jump pack reaches. The stall south-east of the Basar that the droid Brummi circles, the dune south-east of it with
 * a rib in the sand, and the cantina's music box. And the Kessel-Lauf: a slot cut into the southern cliff behind the
 * cantina, entered from the basin at its east end, with stepping stones over a pit, one gap only a jump pack crosses
 * and lit arches for start, checkpoints and finish. Barriers roof the slot well below the cliff tops, so it shows the
 * posts nothing and leads nowhere.
 */
final class SecretPlaces {

    private static final int FLOOR = TessarMap.FLOOR;
    private static final int KESSEL_NORTH = 61;
    private static final int KESSEL_SOUTH = 65;
    private static final int KESSEL_EAST = -22;
    private static final int KESSEL_WEST = -62;
    private static final int DECK_WEST = -26;
    private static final int ENTRANCE_X = -24;
    private static final int CEILING = FLOOR + 12;
    private static final int WALL_TOP = FLOOR + 17;
    private static final int SKY = FLOOR + 45;
    private static final double KESSEL_MIDDLE = (KESSEL_NORTH + KESSEL_SOUTH + 1) / 2.0;

    private enum Arch {
        START(Material.VERDANT_FROGLIGHT), CHECKPOINT(Material.OCHRE_FROGLIGHT), FINISH(Material.PEARLESCENT_FROGLIGHT);

        private final Material light;

        Arch(Material light) {
            this.light = light;
        }
    }

    private final Canvas canvas;
    private final Terrain terrain;

    SecretPlaces(Canvas canvas, Terrain terrain) {
        this.canvas = canvas;
        this.terrain = terrain;
    }

    SecretSpots build() {
        var crystals = List.of(at(4.5, FLOOR + 1.4, -24.5), templeSlot(), at(-24.5, FLOOR + 2.3, 48.5),
                at(11.5, FLOOR + 1.35, -33.5), cliffLedge());
        var droidPath = List.of(at(2.5, FLOOR + 1, 47.5), at(8.5, FLOOR + 1, 47.5), at(8.5, FLOOR + 1, 53.5),
                at(2.5, FLOOR + 1, 53.5));
        return new SecretSpots(crystals, droidPath, dune(), musicBox(), kessel());
    }

    private Location at(double x, double y, double z) {
        return new Location(canvas.world(), x, y, z);
    }

    /** A dark slot in the back wall of the temple hall, west of the altar. */
    private Location templeSlot() {
        int terrace = FLOOR + 3;
        canvas.fill(-5, terrace + 1, -69, -5, terrace + 2, -69, Material.AIR);
        canvas.set(-5, terrace + 3, -69, Material.CHISELED_RED_SANDSTONE);
        return at(-4.5, terrace + 1.4, -68.5);
    }

    /** A ledge cut into the north-eastern cliff three blocks above the scree, too high to jump to on foot. */
    private Location cliffLedge() {
        double cx = 66.5;
        double cz = -53.5;
        for (int x = 64; x <= 68; x++) {
            for (int z = -56; z <= -51; z++) {
                if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= 1.5 && terrain.cliff(x, z)) {
                    canvas.set(x, FLOOR + 4, z, Material.SMOOTH_SANDSTONE);
                    canvas.fill(x, FLOOR + 5, z, x, FLOOR + 7, z, Material.AIR);
                }
            }
        }
        canvas.set(66, FLOOR + 5, -55, Material.SHORT_DRY_GRASS);
        return at(66.5, FLOOR + 5.4, -53.5);
    }

    /** The flat top of the dune south-east of the Basar, with the tip of a rib breaking its sand. */
    private SecretSpots.Dune dune() {
        int top = FLOOR + 3;
        canvas.set(55, top, 49, Material.BONE_BLOCK);
        canvas.set(55, top + 1, 49, Material.BONE_BLOCK);
        canvas.set(55, top + 2, 50, Material.BONE_BLOCK);
        canvas.set(55, top + 1, 50, Material.AIR);
        return new SecretSpots.Dune(at(53.5, top + 1, 47.5), 3.5);
    }

    /** A jukebox against the cantina's south wall between two speakers. */
    private org.bukkit.block.Block musicBox() {
        int z = 51;
        canvas.set(-20, FLOOR + 1, z, Material.JUKEBOX);
        canvas.set(-20, FLOOR + 2, z, "minecraft:lantern[hanging=false]");
        for (int x : new int[]{-21, -19}) {
            canvas.set(x, FLOOR + 1, z, Material.NOTE_BLOCK);
            canvas.set(x, FLOOR + 2, z, Material.NOTE_BLOCK);
        }
        return canvas.world().getBlockAt(-20, FLOOR + 1, z);
    }

    private SecretSpots.Course kessel() {
        walls();
        carve();
        entrance();
        stones();
        lights();
        vents();
        var gates = new ArrayList<Double>();
        gates.add(arch(DECK_WEST, FLOOR, Arch.START));
        gates.add(arch(-35, FLOOR + 1, Arch.CHECKPOINT));
        gates.add(arch(-55, FLOOR + 3, Arch.CHECKPOINT));
        gates.add(arch(-61, FLOOR + 3, Arch.FINISH));
        canvas.fill(KESSEL_WEST, CEILING, KESSEL_NORTH, KESSEL_EAST, CEILING, KESSEL_SOUTH, Material.BARRIER);
        var lane = new BoundingBox(KESSEL_WEST, FLOOR - 1, KESSEL_NORTH, KESSEL_EAST + 1, CEILING, KESSEL_SOUTH + 1);
        var board = new Location(canvas.world(), KESSEL_EAST + 0.6, FLOOR + 3.2, KESSEL_MIDDLE, 90, 0);
        return new SecretSpots.Course(lane, FLOOR + 0.5, List.copyOf(gates), board);
    }

    /** Solid rock round the slot at least up to the cliff's lowest top, so the barrier roof is all that is open. */
    private void walls() {
        for (int x = KESSEL_WEST - 2; x <= KESSEL_EAST + 2; x++) {
            for (int z = KESSEL_NORTH - 2; z <= KESSEL_SOUTH + 2; z++) {
                boolean inside = x >= KESSEL_WEST && x <= KESSEL_EAST && z >= KESSEL_NORTH && z <= KESSEL_SOUTH;
                if (inside) {
                    continue;
                }
                for (int y = FLOOR - 3; y <= WALL_TOP; y++) {
                    if (!canvas.type(x, y, z).isSolid()) {
                        canvas.set(x, y, z, terrain.rock(x, y, z));
                    }
                }
            }
        }
    }

    /**
     * The slot: straight and five wide up to the barrier roof, with the start deck at the east end at basin level and
     * the pit a block lower; above the roof it wanders and narrows into a crack open to the sky.
     */
    private void carve() {
        for (int x = KESSEL_WEST; x <= KESSEL_EAST; x++) {
            for (int z = KESSEL_NORTH; z <= KESSEL_SOUTH; z++) {
                boolean deck = x >= DECK_WEST;
                int ground = deck ? FLOOR : FLOOR - 1;
                for (int y = FLOOR - 3; y < ground; y++) {
                    canvas.set(x, y, z, terrain.rock(x, y, z));
                }
                canvas.set(x, ground, z, deck ? Material.POLISHED_BLACKSTONE_BRICKS
                        : (x + z) % 3 == 0 ? Material.RED_SANDSTONE : Material.SMOOTH_RED_SANDSTONE);
                canvas.fill(x, ground + 1, z, x, CEILING, z, Material.AIR);
            }
            for (int y = CEILING + 1; y <= SKY; y++) {
                double half = 2.1 + 0.9 * terrain.noise(x * 0.13, y * 0.11) - Math.max(0, y - CEILING - 6) * 0.06;
                double middle = KESSEL_MIDDLE + 0.8 * terrain.noise(x * 0.07 + 40, y * 0.05);
                for (int z = KESSEL_NORTH - 1; z <= KESSEL_SOUTH + 1; z++) {
                    if (Math.abs(z + 0.5 - middle) < Math.max(0.6, half)) {
                        canvas.clear(x, y, z);
                    }
                }
            }
        }
        for (int z = KESSEL_NORTH; z <= KESSEL_SOUTH; z++) {
            canvas.set(DECK_WEST, FLOOR, z, z % 2 == 0 ? Material.YELLOW_TERRACOTTA : Material.BLACK_CONCRETE);
        }
    }

    /**
     * A doorway three wide in a closed stretch of the cliff face, framed and lit, and a passage through the northern
     * wall onto the start deck; the path to it is cut through the scree.
     */
    private void entrance() {
        int face = KESSEL_NORTH - 3;
        for (int x = ENTRANCE_X - 2; x <= ENTRANCE_X + 2; x++) {
            for (int y = FLOOR + 1; y <= WALL_TOP; y++) {
                if (!canvas.type(x, y, face).isSolid()) {
                    canvas.set(x, y, face, terrain.rock(x, y, face));
                }
            }
        }
        for (int x = ENTRANCE_X - 1; x <= ENTRANCE_X + 1; x++) {
            for (int z = face - 2; z < KESSEL_NORTH; z++) {
                canvas.set(x, FLOOR, z, z < face ? Material.CUT_SANDSTONE : Material.POLISHED_BLACKSTONE_BRICKS);
                canvas.fill(x, FLOOR + 1, z, x, FLOOR + 3, z, Material.AIR);
            }
            canvas.set(x, FLOOR + 4, face, x == ENTRANCE_X ? Material.VERDANT_FROGLIGHT : Material.POLISHED_BLACKSTONE);
        }
        for (int x : new int[]{ENTRANCE_X - 2, ENTRANCE_X + 2}) {
            canvas.fill(x, FLOOR + 1, face, x, FLOOR + 4, face, Material.POLISHED_BLACKSTONE);
            canvas.set(x, FLOOR + 4, face - 1, "minecraft:end_rod[facing=north]");
        }
    }

    /** Stepping stones over the pit, the long gap, the shelf beyond it and the last stone before the finish. */
    private void stones() {
        pillar(-30, -29, KESSEL_NORTH + 1, KESSEL_NORTH + 2, FLOOR);
        pillar(-34, -33, KESSEL_NORTH + 2, KESSEL_NORTH + 3, FLOOR + 1);
        pillar(-38, -37, KESSEL_NORTH, KESSEL_NORTH + 1, FLOOR + 1);
        pillar(-55, -46, KESSEL_NORTH, KESSEL_SOUTH, FLOOR + 2);
        pillar(-59, -58, KESSEL_NORTH + 2, KESSEL_NORTH + 3, FLOOR + 2);
        pillar(KESSEL_WEST, -61, KESSEL_NORTH, KESSEL_SOUTH, FLOOR + 2);
    }

    private void pillar(int x1, int x2, int z1, int z2, int top) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = FLOOR; y < top; y++) {
                    canvas.set(x, y, z, Material.POLISHED_DEEPSLATE);
                }
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                canvas.set(x, top, z, edge && (x - z) % 2 == 0 ? Material.YELLOW_TERRACOTTA : Material.WAXED_CUT_COPPER);
            }
        }
    }

    /** Lamps set into both walls every five blocks, so the slot reads as a lit course from the deck. */
    private void lights() {
        for (int x = KESSEL_EAST - 2; x >= KESSEL_WEST; x -= 5) {
            canvas.set(x, FLOOR + 6, KESSEL_NORTH - 1, Material.SEA_LANTERN);
            canvas.set(x, FLOOR + 6, KESSEL_SOUTH + 1, Material.SEA_LANTERN);
        }
    }

    /** Campfires under grates in the pit floor, whose smoke gives the Kessel its name. */
    private void vents() {
        for (int[] vent : new int[][]{{-32, KESSEL_SOUTH - 1}, {-42, KESSEL_NORTH + 1}, {-44, KESSEL_SOUTH}}) {
            canvas.set(vent[0], FLOOR - 2, vent[1], "minecraft:campfire[lit=true]");
            canvas.set(vent[0], FLOOR - 1, vent[1], Material.WAXED_COPPER_GRATE);
        }
    }

    /**
     * An arch across the slot at {@code x} over ground whose top block is {@code base}: posts against both walls down
     * into the pit and a vault of frame and light over the middle. Returns the plane a runner crosses, the arch's middle.
     */
    private double arch(int x, int base, Arch kind) {
        int foot = base + 1;
        double centreY = foot + 2.5;
        for (int z = KESSEL_NORTH; z <= KESSEL_SOUTH; z++) {
            boolean post = z == KESSEL_NORTH || z == KESSEL_SOUTH;
            if (post) {
                canvas.fill(x, FLOOR - 1, z, x, foot + 1, z, Material.POLISHED_BLACKSTONE);
            }
            for (int y = foot; y <= foot + 6; y++) {
                double d = Math.hypot(z + 0.5 - KESSEL_MIDDLE, y + 0.5 - centreY);
                if (y + 0.5 >= centreY && d >= 2 && d <= 3) {
                    boolean light = z == KESSEL_NORTH + 1 || z == KESSEL_SOUTH - 1 || (!post && d > 2.6);
                    canvas.set(x, y, z, light ? kind.light : Material.POLISHED_BLACKSTONE_BRICKS);
                }
            }
        }
        canvas.set(x, foot + 6, (int) KESSEL_MIDDLE, "minecraft:end_rod[facing=up]");
        return x + 0.5;
    }
}
