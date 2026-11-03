package me.blockcraft.ttt.arena;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Slab;

import java.util.List;

import static me.blockcraft.ttt.arena.Site.GROUND;

/**
 * What lies around the village: the farm under the terrace's east wall with its red barn, fields and pigsty, the
 * quarry's crane, hut and stonecutter, the orchard south of the brook with the wayside chapel at the end of the main
 * street, the charcoal burner's clearing and a hunting stand by the hedge.
 */
final class Outskirts {

    private final Site site;
    private Block stableGate;
    private Block stonecutter;
    private Block grainSacks;

    Outskirts(Site site) {
        this.site = site;
    }

    /** Where the smithy's horseshoes go. */
    Block stableGate() {
        return stableGate;
    }

    /** Where the grain for the mill is collected. */
    Block grainSacks() {
        return grainSacks;
    }

    /** Where the stone for the abbey is cut. */
    Block stonecutter() {
        return stonecutter;
    }

    void build() {
        site.pave(29, -31, 35, -16);
        site.pave(21, -31, 35, -28);
        site.pave(-1, 31, 1, 37);
        site.pave(12, 8, 13, 18);
        barn();
        fields();
        pigsty();
        quarry();
        orchard();
        chapel();
        charcoalBurner();
        huntingStand();
        hedgerows();
    }

    /**
     * The red barn: a horse box whose gate the horseshoes are brought to, cattle stalls, a grain cart, and a hay loft
     * over the north half reached by a ladder, with a hatch in the gable to jump from.
     */
    private void barn() {
        int x1 = 28;
        int x2 = 40;
        int z1 = -44;
        int z2 = -32;
        int top = site.shell(x1, z1, x2, z2, GROUND, 2, Material.RED_TERRACOTTA, Material.DARK_OAK_LOG, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.DARK_OAK_STAIRS, Material.DARK_OAK_SLAB, Material.RED_TERRACOTTA);
        int loft = GROUND + 4;
        for (int x = x1 + 1; x < x2; x++) {
            for (int z = z1 + 1; z < z2; z++) {
                site.set(x, GROUND, z, site.random.nextInt(3) == 0 ? Material.PACKED_MUD : Material.COARSE_DIRT);
                if (z >= -37) {
                    site.air(x, loft, z, x, loft, z);
                }
            }
        }
        site.air(33, GROUND + 1, z2, 35, GROUND + 3, z2);
        site.door(x1, GROUND + 1, -38, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.ladder(39, GROUND + 1, loft, -40, BlockFace.WEST);
        for (int x = x1 + 1; x < x2; x++) {
            site.fence(x, loft + 1, -38, Material.SPRUCE_FENCE);
        }

        for (int z = -43; z <= -40; z++) {
            if (z != -41) {
                site.fence(32, GROUND + 1, z, Material.SPRUCE_FENCE);
            }
        }
        site.fences(29, GROUND + 1, -39, 32, GROUND + 1, -39, Material.SPRUCE_FENCE);
        stableGate = site.set(32, GROUND + 1, -41, Site.directional(Material.SPRUCE_FENCE_GATE, BlockFace.EAST));
        site.set(29, GROUND + 1, -43, Material.HAY_BLOCK);
        site.set(29, GROUND + 1, -40, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(2)));
        site.lantern(30, loft - 1, -42, true);
        for (int z = -43; z <= -40; z++) {
            site.fence(36, GROUND + 1, z, Material.SPRUCE_FENCE);
        }
        site.set(38, GROUND + 1, -43, Material.COMPOSTER);
        site.set(38, GROUND + 1, -41, Material.HAY_BLOCK);
        site.chest(29, GROUND + 1, -33, BlockFace.EAST);
        grainSacks = site.set(39, GROUND + 1, -33, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(39, GROUND + 1, -34, Material.WHITE_WOOL);
        site.set(38, GROUND + 1, -33, Material.WHITE_WOOL);
        site.fill(37, GROUND + 1, -35, 38, GROUND + 1, -35, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
        site.set(36, GROUND + 1, -35, Material.SPRUCE_FENCE);
        site.set(37, GROUND + 2, -35, Material.HAY_BLOCK);
        site.set(38, GROUND + 2, -35, Material.HAY_BLOCK);
        site.chandelier(34, GROUND + 5, -35, 2);

        site.fill(29, loft + 1, -43, 33, loft + 2, -41, Material.HAY_BLOCK);
        site.fill(29, loft + 3, -43, 31, loft + 3, -42, Material.HAY_BLOCK);
        var bale = site.set(36, loft + 1, -42, Material.HAY_BLOCK);
        site.chore(new Chore.Hold("hay", bale, false));
        site.chest(39, loft + 1, -43, BlockFace.WEST);
        site.air(34, loft + 1, z1, 34, loft + 2, z1);
        site.lantern(34, loft + 3, -40, true);
        site.set(37, loft + 1, -39, Material.COBWEB);
        site.set(34, GROUND, z2 + 1, Material.COARSE_DIRT);
        site.torch(32, GROUND + 3, z2 + 1, BlockFace.SOUTH);
        site.torch(37, GROUND + 3, z2 + 1, BlockFace.SOUTH);
    }

    /** Fenced fields of wheat, carrots and potatoes with water channels and a scarecrow, below the east stairs. */
    private void fields() {
        int x1 = 18;
        int x2 = 27;
        int z1 = -26;
        int z2 = -17;
        var crops = List.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.WHEAT);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (edge) {
                    site.fence(x, GROUND + 1, z, Material.OAK_FENCE);
                } else if ((z - z1) % 4 == 0) {
                    site.set(x, GROUND, z, Material.WATER);
                } else {
                    site.set(x, GROUND, z, Material.FARMLAND);
                    var crop = crops.get(Math.floorMod(z - z1, 4));
                    site.set(x, GROUND + 1, z, crop.createBlockData(data -> ((Ageable) data).setAge(((Ageable) data).getMaximumAge())));
                }
            }
        }
        site.set(x2, GROUND + 1, -21, Site.directional(Material.OAK_FENCE_GATE, BlockFace.EAST));
        site.set(22, GROUND, -21, Material.DIRT);
        site.fence(22, GROUND + 1, -21, Material.OAK_FENCE);
        site.set(22, GROUND + 2, -21, Material.HAY_BLOCK);
        site.set(22, GROUND + 3, -21, Site.directional(Material.CARVED_PUMPKIN, BlockFace.EAST));
        site.fill(23, GROUND + 2, -21, 23, GROUND + 2, -21, Material.OAK_FENCE);
        site.fill(21, GROUND + 2, -21, 21, GROUND + 2, -21, Material.OAK_FENCE);
        site.fill(24, GROUND + 1, -36, 25, GROUND + 1, -35, Material.HAY_BLOCK);
        site.set(24, GROUND + 2, -36, Material.HAY_BLOCK);
        site.fill(19, GROUND + 1, -40, 20, GROUND + 1, -40, Site.log(Material.OAK_LOG, Axis.X));
        site.set(19, GROUND + 2, -40, Site.log(Material.OAK_LOG, Axis.X));
    }

    /** A muddy pigsty with a trough and a lean-to. */
    private void pigsty() {
        int x1 = 34;
        int x2 = 43;
        int z1 = -27;
        int z2 = -19;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                site.set(x, GROUND, z, site.random.nextInt(3) == 0 ? Material.COARSE_DIRT : Material.MUD);
                if (edge && !(x == x1 && z == -23)) {
                    site.fence(x, GROUND + 1, z, Material.SPRUCE_FENCE);
                }
            }
        }
        site.set(x1, GROUND + 1, -23, Site.directional(Material.SPRUCE_FENCE_GATE, BlockFace.EAST));
        site.fill(38, GROUND + 1, -20, 40, GROUND + 1, -20, Material.COMPOSTER);
        for (int x : new int[]{41, 42}) {
            for (int z : new int[]{-26, -24}) {
                site.fences(x, GROUND + 1, z, x, GROUND + 2, z, Material.SPRUCE_FENCE);
            }
        }
        site.fill(40, GROUND + 3, -27, 43, GROUND + 3, -23, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
        site.set(42, GROUND + 1, -25, Material.HAY_BLOCK);
    }

    /**
     * The quarry's dressing: a crane over the pit with a block on its chain, a hut against the east wall of the
     * ledge, the stonecutter on the floor among cut blocks, and lamps at the rim.
     */
    private void quarry() {
        int ledge = GROUND - 3;
        int floor = GROUND - 7;
        site.fill(-26, GROUND + 1, 36, -26, GROUND + 8, 36, Material.SPRUCE_LOG);
        site.fill(-31, GROUND + 8, 36, -27, GROUND + 8, 36, Site.log(Material.SPRUCE_LOG, Axis.X));
        site.fill(-31, GROUND + 3, 36, -31, GROUND + 7, 36, Material.IRON_CHAIN);
        site.set(-31, GROUND + 2, 36, Material.STONE_BRICKS);
        site.set(-27, GROUND + 7, 36, Site.stairs(Material.SPRUCE_STAIRS, BlockFace.EAST, Bisected.Half.TOP));

        for (int x = -30; x <= -28; x++) {
            for (int z = 39; z <= 42; z++) {
                site.set(x, ledge, z, Material.SPRUCE_PLANKS);
                site.set(x, GROUND + 1, z, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
            }
        }
        site.fences(-30, ledge + 1, 39, -30, GROUND, 39, Material.SPRUCE_FENCE);
        site.fences(-30, ledge + 1, 42, -30, GROUND, 42, Material.SPRUCE_FENCE);
        site.chest(-28, ledge + 1, 40, BlockFace.WEST);
        site.set(-28, ledge + 1, 41, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-28, ledge + 2, 41, Site.directional(Material.BARREL, BlockFace.WEST));
        site.lantern(-29, GROUND, 41, true);

        stonecutter = site.set(-34, floor + 1, 36, Site.directional(Material.STONECUTTER, BlockFace.NORTH));
        site.set(-33, floor + 1, 37, Material.STONE);
        site.set(-32, floor + 1, 37, Material.STONE_BRICKS);
        site.set(-32, floor + 2, 37, Site.slab(Material.STONE_BRICK_SLAB, Slab.Type.BOTTOM));
        site.set(-37, floor + 1, 39, Material.COBBLESTONE);
        site.set(-38, floor + 1, 39, Site.slab(Material.COBBLESTONE_SLAB, Slab.Type.BOTTOM));
        site.set(-37, floor + 2, 39, Site.slab(Material.COBBLESTONE_SLAB, Slab.Type.BOTTOM));
        site.lantern(-35, floor + 1, 38, false);
        site.set(-39, floor + 1, 34, Site.directional(Material.BARREL, BlockFace.UP));
        for (int[] post : new int[][]{{-38, 27}, {-25, 30}}) {
            site.fences(post[0], GROUND + 1, post[1], post[0], GROUND + 2, post[1], Material.SPRUCE_FENCE);
            site.lantern(post[0], GROUND + 3, post[1], false);
        }
    }

    /** Fruit trees in rows, beehives and a bench between the brook and the chapel. */
    private void orchard() {
        for (int x : new int[]{-20, -14, -8}) {
            for (int z : new int[]{30, 36, 42}) {
                site.fruitTree(x + site.random.nextInt(2), GROUND, z + site.random.nextInt(2));
            }
        }
        site.fruitTree(6, GROUND, 32);
        site.fruitTree(7, GROUND, 39);
        site.set(-11, GROUND + 1, 33, Site.directional(Material.BEEHIVE, BlockFace.SOUTH));
        site.set(-17, GROUND + 1, 39, Site.directional(Material.BEEHIVE, BlockFace.EAST));
        site.chair(-5, GROUND + 1, 34, BlockFace.EAST, Material.SPRUCE_STAIRS);
        site.chair(-5, GROUND + 1, 35, BlockFace.EAST, Material.SPRUCE_STAIRS);
        site.set(-11, GROUND + 1, 40, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-11, GROUND + 1, 41, Material.COMPOSTER);
    }

    /** The wayside chapel at the end of the main street: an open arch, candles before a small altar, a chest behind it. */
    private void chapel() {
        int x1 = -2;
        int x2 = 2;
        int z1 = 38;
        int z2 = 43;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                site.set(x, GROUND, z, Material.STONE_BRICKS);
                if (edge) {
                    site.rough(x, GROUND + 1, z, x, GROUND + 5, z, Material.STONE_BRICKS);
                } else {
                    site.air(x, GROUND + 1, z, x, GROUND + 5, z);
                }
            }
        }
        site.roof(x1, z1, x2, z2, GROUND + 5, Material.MOSSY_STONE_BRICK_STAIRS, Material.MOSSY_STONE_BRICK_SLAB, Material.STONE_BRICKS);
        site.air(0, GROUND + 1, z1, 0, GROUND + 3, z1);
        site.set(-1, GROUND + 3, z1, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.WEST, Bisected.Half.TOP));
        site.set(1, GROUND + 3, z1, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.EAST, Bisected.Half.TOP));
        site.set(0, GROUND + 1, 42, Material.CHISELED_STONE_BRICKS);
        site.candles(-1, GROUND + 1, 42, 3);
        site.candles(1, GROUND + 1, 42, 2);
        site.set(0, GROUND + 2, 42, Material.POTTED_LILY_OF_THE_VALLEY);
        site.chest(1, GROUND + 1, 40, BlockFace.WEST);
        site.chair(-1, GROUND + 1, 40, BlockFace.SOUTH, Material.SPRUCE_STAIRS);
        site.pane(-2, GROUND + 3, 41, Material.YELLOW_STAINED_GLASS_PANE);
        site.pane(2, GROUND + 3, 41, Material.YELLOW_STAINED_GLASS_PANE);
        site.set(0, GROUND + 5, z1, Material.CHISELED_STONE_BRICKS);
    }

    /** The charcoal burner's clearing: a smoking kiln of earth, stacked logs and the burner's hut. */
    private void charcoalBurner() {
        int cx = 26;
        int cz = 37;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 3.4) {
                    continue;
                }
                site.set(cx + dx, GROUND, cz + dz, Material.COARSE_DIRT);
                int height = distance < 1.5 ? 3 : distance < 2.6 ? 2 : 1;
                for (int y = 1; y <= height; y++) {
                    site.set(cx + dx, GROUND + y, cz + dz, site.random.nextInt(4) == 0 ? Material.COAL_BLOCK : Material.PACKED_MUD);
                }
            }
        }
        site.fire(cx, GROUND + 4, cz);
        site.fill(19, GROUND + 1, 31, 22, GROUND + 2, 31, Site.log(Material.OAK_LOG, Axis.X));
        site.fill(19, GROUND + 1, 32, 21, GROUND + 1, 32, Site.log(Material.OAK_LOG, Axis.X));
        site.fill(31, GROUND + 1, 33, 31, GROUND + 2, 35, Site.log(Material.SPRUCE_LOG, Axis.Z));

        int x1 = 30;
        int x2 = 35;
        int z1 = 40;
        int z2 = 44;
        int top = site.shell(x1, z1, x2, z2, GROUND, 1, Material.SPRUCE_PLANKS, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.SPRUCE_PLANKS);
        site.door(x1, GROUND + 1, 42, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.bed(34, GROUND + 1, 42, BlockFace.SOUTH, Material.BLACK_BED);
        site.chest(31, GROUND + 1, 43, BlockFace.NORTH);
        site.set(31, GROUND + 1, 41, Site.directional(Material.BARREL, BlockFace.UP));
        site.lantern(32, GROUND + 3, 42, true);
        site.set(29, GROUND + 1, 41, Site.directional(Material.BARREL, BlockFace.UP));
    }

    /**
     * Hedgerows, boulders and a few old trees across the meadow south of the brook, so that nobody sees from the
     * bridge to the hedge.
     */
    private void hedgerows() {
        hedgerow(-24, 27, -5, 27, -14, -13);
        hedgerow(4, 27, 11, 27, 7, 7);
        hedgerow(11, 28, 11, 37, 33, 33);
        hedgerow(17, 29, 33, 29, 24, 25);
        hedgerow(-12, 44, -4, 44, -8, -8);
        for (int[] boulder : new int[][]{{8, 30}, {-10, 38}, {15, 41}, {37, 33}, {-6, 29}}) {
            site.set(boulder[0], GROUND + 1, boulder[1], Material.MOSSY_COBBLESTONE);
            site.set(boulder[0] + 1, GROUND + 1, boulder[1], Material.ANDESITE);
            site.set(boulder[0], GROUND + 1, boulder[1] + 1, Site.slab(Material.COBBLESTONE_SLAB, Slab.Type.BOTTOM));
            site.set(boulder[0], GROUND + 2, boulder[1], Site.slab(Material.MOSSY_COBBLESTONE_SLAB, Slab.Type.BOTTOM));
        }
        site.tree(15, GROUND, 35, Material.OAK_LOG);
        site.tree(9, GROUND, 43, Material.BIRCH_LOG);
        site.tree(38, GROUND, 30, Material.SPRUCE_LOG);
        site.tree(-18, GROUND, 26, Material.DARK_OAK_LOG);
    }

    /** A line of hedge two leaves high from one end to the other, open between {@code gapFrom} and {@code gapTo}. */
    private void hedgerow(int x1, int z1, int x2, int z2, int gapFrom, int gapTo) {
        boolean alongX = z1 == z2;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int along = alongX ? x : z;
                if (along >= gapFrom && along <= gapTo || !site.block(x, GROUND + 1, z).isEmpty() && !site.block(x, GROUND + 1, z).getType().name().endsWith("GRASS")) {
                    continue;
                }
                var leaves = site.random.nextInt(3) == 0 ? Material.AZALEA_LEAVES : Material.OAK_LEAVES;
                site.set(x, GROUND + 1, z, Site.leaves(leaves));
                if (site.random.nextInt(4) != 0) {
                    site.set(x, GROUND + 2, z, Site.leaves(leaves));
                }
            }
        }
    }

    /** A hunting stand on posts by the eastern hedge, a lookout over the farmland south of the brook. */
    private void huntingStand() {
        int x1 = 40;
        int x2 = 42;
        int z1 = 39;
        int z2 = 41;
        int deck = GROUND + 6;
        for (int x : new int[]{x1, x2}) {
            for (int z : new int[]{z1, z2}) {
                site.fences(x, GROUND + 1, z, x, deck + 3, z, Material.SPRUCE_FENCE);
            }
        }
        site.fill(x1, GROUND + 1, 40, x1, deck - 1, 40, Material.STRIPPED_SPRUCE_LOG);
        site.fill(x1, deck, z1, x2, deck, z2, Material.SPRUCE_PLANKS);
        site.ladder(x1 - 1, GROUND + 1, deck, 40, BlockFace.WEST);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (edge && !(x == x1 && z == 40)) {
                    site.fence(x, deck + 1, z, Material.SPRUCE_FENCE);
                }
            }
        }
        site.fill(x1, deck + 4, z1, x2, deck + 4, z2, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
    }
}
