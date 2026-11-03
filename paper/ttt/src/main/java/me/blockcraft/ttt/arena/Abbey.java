package me.blockcraft.ttt.arena;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Slab;

import java.util.List;

import static me.blockcraft.ttt.arena.Site.TERRACE;

/**
 * The abbey on the terrace: the church with its bell tower and organ gallery, the crypt below it, the cloister with
 * a dry well that drops into the crypt, the east range with refectory, library and the abbot's room, the graveyard
 * with a mausoleum whose iron door hides a passage to the crypt, the herb garden and the forecourt above the stairs.
 */
final class Abbey {

    private static final int FLOOR = TERRACE;
    private static final int CRYPT = TERRACE - 8;
    private static final List<Material> GLASS = List.of(Material.YELLOW_STAINED_GLASS_PANE, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE);

    private final Site site;
    private Block herbBasket;
    private Block abbotsDesk;
    private Block brokenWall;

    Abbey(Site site) {
        this.site = site;
    }

    /** Where the herbs for the inn are picked. */
    Block herbBasket() {
        return herbBasket;
    }

    /** Where the town hall's charter goes. */
    Block abbotsDesk() {
        return abbotsDesk;
    }

    /** Where the quarry's stone goes. */
    Block brokenWall() {
        return brokenWall;
    }

    void build() {
        crypt();
        church();
        tower();
        cryptStairs();
        graveyard();
        mausoleum();
        passage();
        herbGarden();
        cloister();
        dryWell();
        eastRange();
        forecourt();
    }

    /**
     * The crypt under the nave: vaulted on six pillars, bones in the wall niches, three tombs and the founder's
     * grave, where the vigil's brazier waits cold. The round's ender chest stands in a niche of the north wall.
     */
    private void crypt() {
        site.vault(-22, CRYPT, -44, -8, FLOOR - 1, -26, Material.STONE_BRICKS);
        for (int x = -21; x <= -9; x++) {
            for (int z = -43; z <= -27; z++) {
                site.set(x, CRYPT, z, switch (site.random.nextInt(4)) {
                    case 0 -> Material.COBBLED_DEEPSLATE;
                    case 1 -> Material.CRACKED_DEEPSLATE_TILES;
                    default -> Material.DEEPSLATE_TILES;
                });
            }
        }
        int top = FLOOR - 2;
        for (int x : new int[]{-18, -12}) {
            for (int z : new int[]{-40, -35, -30}) {
                site.fill(x, CRYPT + 1, z, x, top, z, Material.POLISHED_DEEPSLATE);
                for (var side : Site.SIDES) {
                    site.set(x + side.getModX(), top, z + side.getModZ(),
                            Site.stairs(Material.DEEPSLATE_BRICK_STAIRS, side.getOppositeFace(), Bisected.Half.TOP));
                }
            }
        }
        for (int z : new int[]{-42, -33, -30, -28}) {
            niche(-22, z, BlockFace.EAST);
        }
        for (int z : new int[]{-32, -30, -28}) {
            niche(-8, z, BlockFace.WEST);
        }
        tomb(-20, -41);
        tomb(-20, -32);
        tomb(-13, -32);
        site.fill(-16, CRYPT + 1, -37, -14, CRYPT + 1, -36, Material.POLISHED_DEEPSLATE);
        site.fill(-16, CRYPT + 2, -37, -14, CRYPT + 2, -36, Site.slab(Material.POLISHED_DEEPSLATE_SLAB, Slab.Type.BOTTOM));
        site.set(-15, CRYPT + 2, -37, Site.rotated(Material.SKELETON_SKULL, BlockFace.SOUTH));
        var brazier = site.unlit(-15, CRYPT + 1, -34, Material.CAMPFIRE, BlockFace.SOUTH);
        site.chore(new Chore.Hold("vigil", brazier, true));
        site.chair(-16, CRYPT + 1, -33, BlockFace.NORTH, Material.DEEPSLATE_BRICK_STAIRS);
        site.chair(-14, CRYPT + 1, -33, BlockFace.NORTH, Material.DEEPSLATE_BRICK_STAIRS);
        site.candles(-17, CRYPT + 1, -36, 3);
        site.candles(-13, CRYPT + 1, -37, 2);
        site.air(-15, CRYPT + 2, -44, -15, CRYPT + 2, -44);
        site.set(-15, CRYPT + 1, -44, Site.directional(Material.ENDER_CHEST, BlockFace.SOUTH));
        site.candles(-16, CRYPT + 1, -43, 4);
        site.candles(-14, CRYPT + 1, -43, 1);
        site.chest(-21, CRYPT + 1, -27, BlockFace.EAST);
        for (int[] spot : new int[][]{{-15, -41}, {-15, -30}, {-20, -36}}) {
            site.lantern(spot[0], top, spot[1], true);
        }
        for (int[] web : new int[][]{{-21, top, -43}, {-21, top, -27}, {-9, top, -27}, {-21, CRYPT + 1, -29}}) {
            site.set(web[0], web[1], web[2], Material.COBWEB);
        }
        for (int[] roots : new int[][]{{-17, -38}, {-13, -28}, {-19, -34}, {-11, -42}}) {
            site.set(roots[0], top, roots[1], Material.HANGING_ROOTS);
        }
    }

    private void niche(int x, int z, BlockFace facing) {
        site.set(x, CRYPT + 2, z, Site.log(Material.BONE_BLOCK, Axis.X));
        site.set(x, CRYPT + 3, z, Site.rotated(Material.SKELETON_SKULL, facing));
        site.set(x - facing.getModX(), CRYPT + 3, z, Material.BONE_BLOCK);
    }

    private void tomb(int x, int z) {
        site.fill(x, CRYPT + 1, z, x + 1, CRYPT + 1, z + 1, Material.POLISHED_DEEPSLATE);
        site.fill(x, CRYPT + 2, z, x + 1, CRYPT + 2, z + 1, Site.slab(Material.DEEPSLATE_TILE_SLAB, Slab.Type.BOTTOM));
    }

    /**
     * The church: a nave of weathered stone between two arcades, pews before a raised altar, tall coloured windows
     * and a rose window, an organ gallery over the doorway reached by stairs along the east wall, a door into the
     * cloister and the stairs down into the crypt in the north-east corner.
     */
    private void church() {
        int x1 = -22;
        int x2 = -8;
        int z1 = -44;
        int z2 = -26;
        int wallTop = FLOOR + 11;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                if (!edge) {
                    site.set(x, FLOOR, z, (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                    site.air(x, FLOOR + 1, z, x, wallTop, z);
                    continue;
                }
                site.set(x, FLOOR, z, Material.STONE_BRICKS);
                site.set(x, FLOOR + 1, z, Material.POLISHED_ANDESITE);
                site.rough(x, FLOOR + 2, z, x, wallTop, z, Material.STONE_BRICKS);
            }
        }
        for (int z : new int[]{-41, -37, -33, -29}) {
            window(x2, z);
            if (z != -29) {
                window(x1, z);
            }
        }
        for (int[] cross : new int[][]{{-15, 3}, {-15, 4}, {-15, 5}, {-15, 6}, {-16, 5}, {-14, 5}}) {
            site.set(cross[0], FLOOR + cross[1], z1, Site.log(Material.STRIPPED_DARK_OAK_LOG, cross[1] == 5 && cross[0] != -15 ? Axis.X : Axis.Y));
        }
        for (int[] rose : new int[][]{{-15, 9}, {-16, 9}, {-14, 9}, {-15, 8}, {-15, 10}}) {
            site.pane(rose[0], FLOOR + rose[1], z1, GLASS.get(Math.floorMod(rose[0] + rose[1], GLASS.size())));
        }
        site.roof(x1, z1, x2, z2, wallTop, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB, Material.STONE_BRICKS);
        site.door(-15, FLOOR + 1, z2, BlockFace.SOUTH, Material.DARK_OAK_DOOR);
        site.fill(-16, FLOOR + 1, z2, -16, FLOOR + 3, z2, Material.CHISELED_STONE_BRICKS);
        site.fill(-14, FLOOR + 1, z2, -14, FLOOR + 3, z2, Material.CHISELED_STONE_BRICKS);
        site.set(-15, FLOOR + 3, z2, Material.CHISELED_STONE_BRICKS);
        site.pane(-15, FLOOR + 9, z2, Material.GLASS_PANE);
        site.torch(-17, FLOOR + 3, z2 + 1, BlockFace.SOUTH);
        site.torch(-13, FLOOR + 3, z2 + 1, BlockFace.SOUTH);
        site.door(x2, FLOOR + 1, -34, BlockFace.EAST, Material.SPRUCE_DOOR);

        for (int x : new int[]{-19, -11}) {
            for (int z = -41; z <= -29; z++) {
                site.set(x, wallTop - 1, z, Material.STONE_BRICKS);
            }
            for (int z : new int[]{-41, -37, -33, -29}) {
                site.set(x, FLOOR + 1, z, Material.CHISELED_STONE_BRICKS);
                site.rough(x, FLOOR + 2, z, x, wallTop - 1, z, Material.STONE_BRICKS);
                for (int dz : new int[]{-1, 1}) {
                    if (z + dz >= -42 && z + dz <= -28) {
                        site.set(x, wallTop - 2, z + dz, Site.stairs(Material.STONE_BRICK_STAIRS, dz < 0 ? BlockFace.SOUTH : BlockFace.NORTH, Bisected.Half.TOP));
                    }
                }
            }
        }
        site.torch(-18, FLOOR + 3, -41, BlockFace.EAST);
        site.torch(-18, FLOOR + 3, -33, BlockFace.EAST);
        site.torch(-12, FLOOR + 3, -41, BlockFace.WEST);
        site.torch(-12, FLOOR + 3, -33, BlockFace.WEST);
        site.chandelier(-15, FLOOR + 7, -39);
        site.chandelier(-15, FLOOR + 7, -33);

        for (int z : new int[]{-38, -36, -34, -32}) {
            for (int x : new int[]{-20, -19, -18, -17, -13, -12}) {
                site.chair(x, FLOOR + 1, z, BlockFace.NORTH, Material.SPRUCE_STAIRS);
            }
        }
        site.fill(-15, FLOOR + 1, -40, -15, FLOOR + 1, -27, Material.RED_CARPET);
        site.fill(-18, FLOOR + 1, -43, -12, FLOOR + 1, -41, Site.slab(Material.POLISHED_ANDESITE_SLAB, Slab.Type.BOTTOM));
        site.fill(-16, FLOOR + 1, -42, -14, FLOOR + 1, -42, Material.POLISHED_DIORITE);
        site.candles(-16, FLOOR + 2, -42, 3);
        site.candles(-14, FLOOR + 2, -42, 3);
        site.set(-15, FLOOR + 2, -42, Material.GOLD_BLOCK);
        site.set(-19, FLOOR + 1, -28, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(3)));

        int gallery = FLOOR + 4;
        site.fill(-21, gallery, -29, -9, gallery, -27, Material.SPRUCE_PLANKS);
        site.fences(-21, gallery + 1, -30, -10, gallery + 1, -30, Material.SPRUCE_FENCE);
        for (int step = 0; step < 4; step++) {
            int z = -33 + step;
            if (step > 0) {
                site.fill(-9, FLOOR + 1, z, -9, FLOOR + step, z, Material.STONE_BRICKS);
            }
            site.set(-9, FLOOR + 1 + step, z, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.SOUTH, Bisected.Half.BOTTOM));
        }
        for (int x = -18; x <= -12; x++) {
            int pipe = 3 - Math.abs(x + 15);
            site.fill(x, gallery + 1, -27, x, gallery + 2 + pipe, -27, Site.directional(Material.LIGHTNING_ROD, BlockFace.UP));
        }
        var organ = site.set(-15, gallery + 1, -28, Material.NOTE_BLOCK);
        site.chore(new Chore.Puzzle("organ", organ, false));
        site.chest(-21, gallery + 1, -27, BlockFace.EAST);
        site.torch(-20, gallery + 3, -27, BlockFace.NORTH);
        site.torch(-10, gallery + 3, -27, BlockFace.NORTH);
    }

    private void window(int x, int z) {
        var glass = GLASS.get(site.random.nextInt(GLASS.size()));
        for (int y = FLOOR + 6; y <= FLOOR + 9; y++) {
            site.pane(x, y, z, y == FLOOR + 9 ? Material.WHITE_STAINED_GLASS_PANE : glass);
        }
    }

    /**
     * The bell tower at the church's south-west corner: a ladder climbs past a landing at the gallery's height and a
     * storeroom to the belfry, whose open arches look over the whole map. The bell hangs there.
     */
    private void tower() {
        int x1 = -27;
        int x2 = -23;
        int z1 = -30;
        int z2 = -26;
        int belfry = FLOOR + 14;
        int ceiling = belfry + 5;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                site.set(x, FLOOR, z, Material.STONE_BRICKS);
                if (edge) {
                    site.fill(x, FLOOR + 1, z, x, ceiling, z, corner ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                    site.rough(x, FLOOR + 2, z, x, ceiling - 1, z, corner ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                } else {
                    site.air(x, FLOOR + 1, z, x, ceiling - 1, z);
                    for (int level : new int[]{FLOOR + 4, FLOOR + 9, belfry}) {
                        site.set(x, level, z, Material.SPRUCE_PLANKS);
                    }
                    site.set(x, ceiling, z, Material.STONE_BRICKS);
                }
            }
        }
        for (int a = -1; a <= 1; a++) {
            for (int y = belfry + 1; y <= belfry + 3; y++) {
                site.set(-25 + a, y, z1, Material.AIR);
                site.set(-25 + a, y, z2, Material.AIR);
                site.set(x1, y, -28 + a, Material.AIR);
                site.set(x2, y, -28 + a, Material.AIR);
            }
            site.fence(-25 + a, belfry + 1, z1, Material.SPRUCE_FENCE);
            site.fence(-25 + a, belfry + 1, z2, Material.SPRUCE_FENCE);
            site.fence(x1, belfry + 1, -28 + a, Material.SPRUCE_FENCE);
            site.fence(x2, belfry + 1, -28 + a, Material.SPRUCE_FENCE);
        }
        site.ladder(-26, FLOOR + 1, belfry, -28, BlockFace.EAST);
        site.door(-25, FLOOR + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.fill(-23, FLOOR, -28, -22, FLOOR, -28, Material.STONE_BRICKS);
        site.air(-23, FLOOR + 1, -28, -22, FLOOR + 2, -28);
        site.air(-23, FLOOR + 5, -28, -22, FLOOR + 6, -28);
        site.air(-25, FLOOR + 11, z1, -25, FLOOR + 11, z1);
        site.air(-25, FLOOR + 11, z2, -25, FLOOR + 11, z2);
        site.chest(-24, FLOOR + 10, -29, BlockFace.WEST);
        site.lantern(-24, FLOOR + 3, -27, true);
        site.lantern(-24, FLOOR + 8, -29, true);
        site.lantern(-26, ceiling - 1, -29, true);
        var bell = site.set(-25, ceiling - 1, -28, Material.BELL.createBlockData(data -> {
            var hanging = (Bell) data;
            hanging.setAttachment(Bell.Attachment.CEILING);
            hanging.setFacing(BlockFace.SOUTH);
        }));
        site.chore(new Chore.Hold("bell", bell, true));
        site.roof(x1, z1, x2, z2, ceiling, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB, Material.STONE_BRICKS);
        site.set(-25, ceiling + 4, -28, Site.directional(Material.LIGHTNING_ROD, BlockFace.UP));
    }

    /** Stone stairs from the nave's north-east corner down into the crypt, behind a railing. */
    private void cryptStairs() {
        for (int step = 0; step < 8; step++) {
            int z = -43 + step;
            int y = FLOOR - step;
            for (int x = -10; x <= -9; x++) {
                if (y - 1 > CRYPT) {
                    site.fill(x, CRYPT + 1, z, x, y - 1, z, Material.STONE_BRICKS);
                }
                site.set(x, y, z, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
                if (step > 0) {
                    site.air(x, y + 1, z, x, FLOOR, z);
                }
            }
            if (step > 0 && site.block(-11, FLOOR + 1, z).isEmpty()) {
                site.fence(-11, FLOOR + 1, z, Material.SPRUCE_FENCE);
            }
        }
        site.fences(-10, FLOOR + 1, -35, -9, FLOOR + 1, -35, Material.SPRUCE_FENCE);
    }

    /**
     * The traitors' passage from the mausoleum to the crypt: a ladder down the mausoleum's shaft, a low tunnel west
     * of the church and an iron door into the crypt at each end. Halfway, a ladder climbs to a hatch at the bottom
     * of the open grave, which anyone may find; a ladder in the grave climbs back out.
     */
    private void passage() {
        site.rough(-41, CRYPT, -43, -23, CRYPT + 4, -37, Material.COBBLESTONE);
        site.air(-40, CRYPT + 1, -38, -23, CRYPT + 3, -38);
        site.air(-40, CRYPT + 1, -42, -40, CRYPT + 3, -38);
        site.rough(-22, CRYPT, -39, -22, CRYPT + 4, -37, Material.STONE_BRICKS);
        site.traitorDoor(-22, CRYPT + 1, -38, BlockFace.WEST);
        site.torch(-35, CRYPT + 2, -38, BlockFace.SOUTH);
        site.torch(-27, CRYPT + 2, -38, BlockFace.NORTH);
        site.rough(-41, FLOOR - 4, -43, -39, FLOOR - 1, -41, Material.COBBLESTONE);
        site.ladder(-40, CRYPT + 1, FLOOR, -42, BlockFace.SOUTH);

        site.rough(-33, CRYPT + 1, -36, -31, FLOOR - 4, -36, Material.COBBLESTONE);
        site.ladder(-32, CRYPT + 1, FLOOR - 4, -37, BlockFace.NORTH);
        site.hatch(-32, FLOOR - 3, -37, BlockFace.NORTH, Material.SPRUCE_TRAPDOOR);
        site.set(-32, FLOOR - 3, -36, Material.COARSE_DIRT);
        site.air(-32, FLOOR - 2, -37, -32, FLOOR, -36);
        for (int y = FLOOR - 2; y <= FLOOR; y++) {
            if (!site.block(-32, y, -35).isSolid()) {
                site.set(-32, y, -35, Material.COARSE_DIRT);
            }
        }
        site.ladder(-32, FLOOR - 2, FLOOR, -36, BlockFace.NORTH);
    }

    /** A stone mausoleum in the graveyard's north-west corner; its iron door opens on the shaft down to the passage. */
    private void mausoleum() {
        int x1 = -42;
        int x2 = -38;
        int z1 = -44;
        int z2 = -39;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                if (!(x == -40 && z == -42)) {
                    site.set(x, FLOOR, z, Material.POLISHED_ANDESITE);
                }
                if (edge) {
                    site.rough(x, FLOOR + 1, z, x, FLOOR + 5, z, corner ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
                } else {
                    site.air(x, FLOOR + 1, z, x, FLOOR + 5, z);
                }
            }
        }
        site.roof(x1, z1, x2, z2, FLOOR + 5, Material.STONE_BRICK_STAIRS, Material.STONE_BRICK_SLAB, Material.STONE_BRICKS);
        site.traitorDoor(-40, FLOOR + 1, z2, BlockFace.SOUTH);
        site.set(-40, FLOOR + 3, z2, Material.CHISELED_STONE_BRICKS);
        site.set(-40, FLOOR, z2 + 1, Material.POLISHED_ANDESITE);
        site.fill(-41, FLOOR + 1, -43, -41, FLOOR + 1, -42, Material.POLISHED_DEEPSLATE);
        site.fill(-41, FLOOR + 2, -43, -41, FLOOR + 2, -42, Site.slab(Material.POLISHED_DEEPSLATE_SLAB, Slab.Type.BOTTOM));
        site.candles(-39, FLOOR + 1, -43, 2);
        site.chest(-41, FLOOR + 1, -40, BlockFace.EAST);
    }

    /**
     * The graveyard west of the church: crooked headstones over mounds, candles on a few graves, a yew, a fence with
     * a lantern at the gate, and the open grave whose bottom is a hatch.
     */
    private void graveyard() {
        for (int x = -45; x <= -28; x++) {
            for (int z = -45; z <= -30; z++) {
                if (x >= -42 && x <= -38 && z >= -44 && z <= -38) {
                    continue;
                }
                site.set(x, FLOOR, z, switch (site.random.nextInt(4)) {
                    case 0 -> Material.PODZOL;
                    case 1 -> Material.COARSE_DIRT;
                    default -> Material.GRASS_BLOCK;
                });
            }
        }
        for (int x = -45; x <= -28; x++) {
            if (x != -36 && x != -35) {
                site.fence(x, FLOOR + 1, -29, Material.DARK_OAK_FENCE);
            }
        }
        for (int z = -44; z <= -30; z++) {
            if (z != -35 && z != -34) {
                site.fence(-28, FLOOR + 1, z, Material.DARK_OAK_FENCE);
            }
        }
        site.fence(-37, FLOOR + 2, -29, Material.DARK_OAK_FENCE);
        site.lantern(-37, FLOOR + 3, -29, false);
        int[][] graves = {{-44, -34}, {-41, -34}, {-38, -34}, {-35, -34}, {-30, -34}, {-44, -31}, {-41, -31}, {-38, -31},
                {-35, -31}, {-32, -31}, {-30, -31}, {-35, -43}, {-32, -43}, {-30, -43}, {-35, -40}, {-30, -40}, {-44, -37}};
        var stones = List.of(Material.MOSSY_COBBLESTONE_WALL, Material.COBBLESTONE_WALL, Material.STONE_BRICK_WALL, Material.MOSSY_STONE_BRICK_WALL);
        for (int[] grave : graves) {
            site.set(grave[0], FLOOR + 1, grave[1], stones.get(site.random.nextInt(stones.size())));
            site.set(grave[0], FLOOR, grave[1] + 1, site.random.nextBoolean() ? Material.COARSE_DIRT : Material.ROOTED_DIRT);
            switch (site.random.nextInt(5)) {
                case 0 -> site.candles(grave[0], FLOOR + 1, grave[1] + 1, 1 + site.random.nextInt(3));
                case 1 -> site.set(grave[0], FLOOR + 1, grave[1] + 1, Material.DEAD_BUSH);
                case 2 -> site.set(grave[0], FLOOR + 1, grave[1] + 1, Material.LILY_OF_THE_VALLEY);
                default -> {
                }
            }
        }
        site.set(-32, FLOOR + 1, -38, Material.MOSSY_STONE_BRICK_WALL);
        site.set(-31, FLOOR + 1, -37, Material.COARSE_DIRT);
        site.set(-31, FLOOR + 2, -37, Site.slab(Material.MUD_BRICK_SLAB, Slab.Type.BOTTOM));
        site.chest(-45, FLOOR + 1, -44, BlockFace.EAST);
        site.tree(-44, FLOOR, -40, Material.DARK_OAK_LOG);
    }

    /**
     * Raised beds of herbs and vegetables below the graveyard, beehives along the wall, a rain barrel and a potting
     * shed; the herbs for the inn are picked from the basket by the beds.
     */
    private void herbGarden() {
        var herbs = List.of(Material.FERN, Material.ALLIUM, Material.LILY_OF_THE_VALLEY, Material.AZURE_BLUET,
                Material.CORNFLOWER, Material.SHORT_GRASS, Material.OXEYE_DAISY);
        for (int z0 : new int[]{-27, -23, -19}) {
            for (int x0 : new int[]{-44, -40, -36}) {
                for (int x = x0; x <= x0 + 2; x++) {
                    for (int z = z0; z <= z0 + 1; z++) {
                        site.set(x, FLOOR, z, site.random.nextBoolean() ? Material.PODZOL : Material.ROOTED_DIRT);
                        site.set(x, FLOOR + 1, z, herbs.get(site.random.nextInt(herbs.size())));
                    }
                }
                for (int x = x0 - 1; x <= x0 + 3; x++) {
                    for (int z : new int[]{z0 - 1, z0 + 2}) {
                        site.set(x, FLOOR, z, Material.DIRT_PATH);
                    }
                }
            }
        }
        site.set(-45, FLOOR + 1, -27, Site.directional(Material.BEEHIVE, BlockFace.EAST));
        site.set(-45, FLOOR + 1, -23, Site.directional(Material.BEEHIVE, BlockFace.EAST));
        site.set(-45, FLOOR + 1, -20, Site.directional(Material.BEE_NEST, BlockFace.EAST));
        herbBasket = site.set(-31, FLOOR + 1, -21, Material.COMPOSTER);
        site.set(-33, FLOOR + 1, -25, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(3)));
        site.set(-30, FLOOR + 1, -19, Material.ROSE_BUSH.createBlockData(data -> ((Bisected) data).setHalf(Bisected.Half.BOTTOM)));
        site.set(-30, FLOOR + 2, -19, Material.ROSE_BUSH.createBlockData(data -> ((Bisected) data).setHalf(Bisected.Half.TOP)));

        int x1 = -32;
        int x2 = -29;
        int z1 = -28;
        int z2 = -25;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                site.set(x, FLOOR, z, Material.SPRUCE_PLANKS);
                boolean wall = z == z1 || x == x1 || x == x2;
                boolean post = (x == x1 || x == x2) && (z == z1 || z == z2);
                site.fill(x, FLOOR + 1, z, x, FLOOR + 3, z, post ? Material.SPRUCE_LOG : wall ? Material.SPRUCE_PLANKS : Material.AIR);
                site.set(x, FLOOR + 4, z, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
            }
        }
        site.air(x1, FLOOR + 1, -26, x1, FLOOR + 2, -26);
        site.chest(-30, FLOOR + 1, -27, BlockFace.SOUTH);
        site.set(-31, FLOOR + 1, -27, Site.directional(Material.BARREL, BlockFace.UP));
        site.pot(-31, FLOOR + 2, -27);
        site.set(-30, FLOOR + 3, -27, Site.directional(Material.BARREL, BlockFace.SOUTH));
        site.lantern(-30, FLOOR + 3, -26, true);
    }

    /**
     * The cloister east of the church: arcaded walks under a lean-to roof round a garth with a quince tree, benches
     * and the dry well. The roof itself can be walked from the library. The south wall is being mended, and the
     * scaffold in front of it is where the quarry's stone goes.
     */
    private void cloister() {
        for (int x = -7; x <= 7; x++) {
            for (int z = -43; z <= -28; z++) {
                boolean garth = x >= -4 && x <= 4 && z >= -40 && z <= -31;
                if (garth) {
                    site.set(x, FLOOR, z, x == 0 || z == -35 ? Material.DIRT_PATH : Material.GRASS_BLOCK);
                    site.air(x, FLOOR + 1, z, x, FLOOR + 6, z);
                    continue;
                }
                site.set(x, FLOOR, z, site.random.nextInt(3) == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                boolean inner = x == -5 || x == 5 || z == -41 || z == -30;
                boolean innerRing = inner && x >= -5 && x <= 5 && z >= -41 && z <= -30;
                if (innerRing) {
                    boolean pillar = Math.floorMod(x + z, 2) == 0 || (Math.abs(x) == 5 && (z == -41 || z == -30));
                    if (pillar) {
                        site.rough(x, FLOOR + 1, z, x, FLOOR + 3, z, Material.STONE_BRICKS);
                    } else {
                        site.air(x, FLOOR + 1, z, x, FLOOR + 2, z);
                        site.set(x, FLOOR + 3, z, Site.slab(Material.STONE_BRICK_SLAB, Slab.Type.TOP));
                    }
                    site.set(x, FLOOR + 4, z, Material.STONE_BRICKS);
                } else {
                    site.air(x, FLOOR + 1, z, x, FLOOR + 4, z);
                }
                site.set(x, FLOOR + 5, z, Site.slab(Material.DEEPSLATE_TILE_SLAB, Slab.Type.BOTTOM));
            }
        }
        for (int x = -7; x <= 7; x++) {
            for (int z : new int[]{-44, -27}) {
                site.rough(x, FLOOR, z, x, FLOOR + 5, z, Material.STONE_BRICKS);
            }
        }
        site.air(-1, FLOOR + 1, -27, 1, FLOOR + 2, -27);
        site.set(-1, FLOOR + 3, -27, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.WEST, Bisected.Half.TOP));
        site.set(1, FLOOR + 3, -27, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.EAST, Bisected.Half.TOP));
        site.set(0, FLOOR + 3, -27, Site.slab(Material.STONE_BRICK_SLAB, Slab.Type.TOP));
        for (int[] spot : new int[][]{{-6, -36}, {6, -36}, {0, -42}, {0, -29}, {-6, -42}, {6, -29}}) {
            site.lantern(spot[0], FLOOR + 4, spot[1], true);
        }
        site.fruitTree(3, FLOOR, -39);
        site.chair(-3, FLOOR + 1, -32, BlockFace.EAST, Material.SPRUCE_STAIRS);
        site.chair(-3, FLOOR + 1, -38, BlockFace.EAST, Material.SPRUCE_STAIRS);
        site.chair(3, FLOOR + 1, -32, BlockFace.WEST, Material.SPRUCE_STAIRS);
        for (int[] bush : new int[][]{{-4, -40}, {4, -31}, {-4, -31}}) {
            site.set(bush[0], FLOOR + 1, bush[1], Site.half(Material.PEONY, Bisected.Half.BOTTOM));
            site.set(bush[0], FLOOR + 2, bush[1], Site.half(Material.PEONY, Bisected.Half.TOP));
        }

        site.set(3, FLOOR + 2, -27, Material.CRACKED_STONE_BRICKS);
        site.set(5, FLOOR + 3, -27, Material.CRACKED_STONE_BRICKS);
        site.air(4, FLOOR + 3, -27, 4, FLOOR + 3, -27);
        brokenWall = site.set(4, FLOOR + 2, -27, Material.CRACKED_STONE_BRICKS);
        for (int x : new int[]{3, 5}) {
            site.fences(x, FLOOR + 1, -26, x, FLOOR + 3, -26, Material.SPRUCE_FENCE);
        }
        site.fill(3, FLOOR + 4, -26, 5, FLOOR + 4, -26, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
        site.set(6, FLOOR + 1, -26, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(2, FLOOR + 1, -26, Material.COBBLESTONE);
        site.set(2, FLOOR + 2, -26, Site.slab(Material.COBBLESTONE_SLAB, Slab.Type.BOTTOM));
        site.set(1, FLOOR + 1, -25, Site.slab(Material.STONE_SLAB, Slab.Type.BOTTOM));
    }

    /**
     * The dry well in the garth: a ladder runs from its rim down a shaft and a short tunnel into the crypt, the one
     * way down there besides the church's stairs.
     */
    private void dryWell() {
        site.rough(-7, CRYPT, -36, 1, CRYPT + 4, -34, Material.STONE_BRICKS);
        site.air(-8, CRYPT + 1, -35, 0, CRYPT + 3, -35);
        site.torch(-4, CRYPT + 2, -35, BlockFace.NORTH);
        site.rough(-1, CRYPT + 4, -36, 1, FLOOR + 1, -34, Material.STONE_BRICKS);
        site.air(0, CRYPT + 4, -35, 0, FLOOR + 1, -35);
        site.ladder(0, CRYPT + 1, FLOOR + 1, -35, BlockFace.WEST);
        site.fences(-1, FLOOR + 2, -35, -1, FLOOR + 3, -35, Material.SPRUCE_FENCE);
        site.fences(1, FLOOR + 2, -35, 1, FLOOR + 3, -35, Material.SPRUCE_FENCE);
        site.fill(-1, FLOOR + 4, -36, 1, FLOOR + 4, -34, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
    }

    /**
     * The east range, two storeys of stone on the terrace's edge: the refectory and kitchen below, the library and
     * the abbot's room above, stairs between them, and a door from the library out onto the cloister roof.
     */
    private void eastRange() {
        int x1 = 8;
        int x2 = 15;
        int z1 = -44;
        int z2 = -27;
        int top = site.shell(x1, z1, x2, z2, FLOOR, 2, Material.STONE_BRICKS, Material.DEEPSLATE, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB, Material.STONE_BRICKS);
        int upper = FLOOR + 4;
        for (int x = x1 + 1; x < x2; x++) {
            site.rough(x, FLOOR + 1, -36, x, FLOOR + 3, -36, Material.STONE_BRICKS);
            site.rough(x, upper + 1, -36, x, upper + 3, -36, Material.STONE_BRICKS);
        }
        site.door(11, FLOOR + 1, -36, BlockFace.NORTH, Material.SPRUCE_DOOR);
        site.door(11, upper + 1, -36, BlockFace.NORTH, Material.SPRUCE_DOOR);
        site.door(x1, FLOOR + 1, -40, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.door(x1, FLOOR + 1, -31, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.door(x2, FLOOR + 1, -29, BlockFace.EAST, Material.SPRUCE_DOOR);
        site.door(x1, upper + 1, -38, BlockFace.WEST, Material.SPRUCE_DOOR);

        for (int step = 0; step < 4; step++) {
            int z = -29 - step;
            if (step > 0) {
                site.fill(14, FLOOR + 1, z, 14, FLOOR + step, z, Material.SPRUCE_PLANKS);
            }
            site.set(14, FLOOR + 1 + step, z, Site.stairs(Material.SPRUCE_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
            if (step < 3) {
                site.air(14, upper, z, 14, upper, z);
                site.fence(13, upper + 1, z, Material.SPRUCE_FENCE);
            }
        }
        site.fence(14, upper + 1, -28, Material.SPRUCE_FENCE);

        for (int z = -42; z <= -38; z++) {
            site.table(11, FLOOR + 1, z);
            site.table(12, FLOOR + 1, z);
            site.chair(10, FLOOR + 1, z, BlockFace.EAST, Material.SPRUCE_STAIRS);
            site.chair(13, FLOOR + 1, z, BlockFace.WEST, Material.SPRUCE_STAIRS);
        }
        site.lantern(11, upper - 1, -41, true);
        site.lantern(12, upper - 1, -38, true);
        site.chest(9, FLOOR + 1, -43, BlockFace.EAST);
        site.set(14, FLOOR + 1, -43, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(14, FLOOR + 1, -42, Site.directional(Material.BARREL, BlockFace.UP));

        site.fire(11, FLOOR + 1, -28);
        site.fill(10, FLOOR + 1, -28, 10, FLOOR + 3, -28, Material.BRICKS);
        site.fill(12, FLOOR + 1, -28, 12, FLOOR + 3, -28, Material.BRICKS);
        site.set(11, FLOOR + 3, -28, Material.BRICKS);
        site.set(9, FLOOR + 1, -35, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(9, FLOOR + 1, -34, Material.CAULDRON);
        site.table(10, FLOOR + 1, -32);
        site.lantern(11, upper - 1, -32, true);

        for (int z = -43; z <= -38; z++) {
            if (z <= -40) {
                site.fill(9, upper + 1, z, 9, upper + 3, z, Material.BOOKSHELF);
            }
            site.fill(14, upper + 1, z, 14, upper + 3, z, Material.BOOKSHELF);
        }
        site.fill(10, upper + 1, -43, 13, upper + 3, -43, Material.BOOKSHELF);
        var shelf = site.set(12, upper + 1, -43, Site.directional(Material.CHISELED_BOOKSHELF, BlockFace.SOUTH));
        site.chore(new Chore.Puzzle("chronicle", shelf, false));
        site.fill(11, upper + 1, -40, 12, upper + 1, -40, Site.slab(Material.DARK_OAK_SLAB, Slab.Type.TOP));
        site.candles(11, upper + 2, -40, 2);
        site.chair(11, upper + 1, -39, BlockFace.NORTH, Material.DARK_OAK_STAIRS);
        site.chair(12, upper + 1, -39, BlockFace.NORTH, Material.DARK_OAK_STAIRS);
        site.chest(13, upper + 1, -37, BlockFace.WEST);
        site.lantern(11, upper + 3, -38, true);

        abbotsDesk = site.set(9, upper + 1, -30, Site.directional(Material.LECTERN, BlockFace.EAST));
        site.candles(9, upper + 1, -29, 3);
        site.bed(12, upper + 1, -34, BlockFace.NORTH, Material.PURPLE_BED);
        site.chest(9, upper + 1, -34, BlockFace.EAST);
        site.fill(10, upper + 1, -32, 12, upper + 1, -30, Material.PURPLE_CARPET);
        site.lantern(11, upper + 3, -31, true);
    }

    /** The square before the church and the cloister gate: paving, a lime tree, benches over the parapet and lamps. */
    private void forecourt() {
        for (int x = -27; x <= 15; x++) {
            for (int z = -25; z <= -17; z++) {
                boolean path = (z >= -25 && z <= -23 && x >= -26 && x <= 3) || (x >= -2 && x <= 2) || (z <= -24 && x >= 2 && x <= 15);
                if (path) {
                    site.set(x, FLOOR, z, switch (site.random.nextInt(6)) {
                        case 0 -> Material.MOSSY_COBBLESTONE;
                        case 1 -> Material.GRAVEL;
                        case 2 -> Material.COARSE_DIRT;
                        default -> Material.COBBLESTONE;
                    });
                }
            }
        }
        site.tree(-8, FLOOR, -20, Material.OAK_LOG);
        site.tree(9, FLOOR, -20, Material.BIRCH_LOG);
        for (int x : new int[]{-6, -5, 5, 6}) {
            site.chair(x, FLOOR + 1, -17, BlockFace.SOUTH, Material.SPRUCE_STAIRS);
        }
        for (int[] post : new int[][]{{-12, -22}, {12, -22}, {-24, -22}}) {
            site.fences(post[0], FLOOR + 1, post[1], post[0], FLOOR + 3, post[1], Material.DARK_OAK_FENCE);
            site.lantern(post[0], FLOOR + 4, post[1], false);
        }
        site.set(-20, FLOOR + 1, -18, Material.STONE_BRICK_WALL);
        site.set(-20, FLOOR + 2, -18, Material.STONE_BRICK_WALL);
        site.set(-20, FLOOR + 3, -18, Material.CHISELED_STONE_BRICKS);
        site.set(-21, FLOOR + 2, -18, Site.directional(Material.STONE_BRICK_STAIRS, BlockFace.EAST));
        site.set(-19, FLOOR + 2, -18, Site.directional(Material.STONE_BRICK_STAIRS, BlockFace.WEST));
    }
}
