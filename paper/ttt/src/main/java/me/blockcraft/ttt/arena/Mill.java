package me.blockcraft.ttt.arena;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Slab;

import static me.blockcraft.ttt.arena.Site.CELLAR;
import static me.blockcraft.ttt.arena.Site.GROUND;
import static me.blockcraft.ttt.arena.Terrain.BROOK_NORTH;
import static me.blockcraft.ttt.arena.Terrain.BROOK_SOUTH;

/**
 * The water mill on the brook east of the bridge: three floors of stone and timber, the wheel turning in a deepened
 * stretch of the brook under a flume from the mill pond, and the gear room below with the generator that the
 * village's lamps hang on. The smugglers' tunnel from the inn's cellar ends at the gear room's iron door. The brook
 * is crossed by the stone bridge, a footbridge in the west and a ford of stepping stones.
 */
final class Mill {

    private static final int X1 = 26;
    private static final int X2 = 36;
    private static final int Z1 = 11;
    private static final int Z2 = 19;
    private static final int AXLE_X = 31;
    private static final int AXLE_Y = CELLAR + 3;

    private final Site site;
    private Block grainBin;
    private Block generator;

    Mill(Site site) {
        this.site = site;
    }

    /** Where the farm's grain goes. */
    Block grainBin() {
        return grainBin;
    }

    Block generator() {
        return generator;
    }

    void build() {
        gearRoom();
        mill();
        wheel();
        flume();
        smugglersTunnel();
        bridges();
    }

    /**
     * Under the mill: the axle comes through the south wall to a gear, a shaft rises to the millstone above, and the
     * generator hums in the corner under a lamp. A ladder leads up into the mill.
     */
    private void gearRoom() {
        site.vault(X1, CELLAR, Z1, X2, GROUND - 1, Z2, Material.STONE_BRICKS);
        for (int x = X1 + 1; x < X2; x++) {
            for (int z = Z1 + 1; z < Z2; z++) {
                site.set(x, CELLAR, z, site.random.nextInt(3) == 0 ? Material.COBBLESTONE : Material.STONE_BRICKS);
            }
        }
        site.fill(AXLE_X, AXLE_Y, 15, AXLE_X, AXLE_Y, Z2, Site.log(Material.STRIPPED_SPRUCE_LOG, Axis.Z));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    site.set(AXLE_X + dx, AXLE_Y + dy, 15, Material.SPRUCE_PLANKS);
                }
            }
        }
        site.set(AXLE_X - 2, AXLE_Y, 15, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
        site.set(AXLE_X + 2, AXLE_Y, 15, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
        site.set(AXLE_X, AXLE_Y - 2, 15, Material.SPRUCE_FENCE);
        site.fill(AXLE_X, CELLAR + 1, 13, AXLE_X, GROUND, 13, Material.STRIPPED_SPRUCE_LOG);
        for (var side : Site.SIDES) {
            site.set(AXLE_X + side.getModX(), AXLE_Y + 1, 13 + side.getModZ(), Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
        }
        site.set(AXLE_X, AXLE_Y + 1, 14, Material.SPRUCE_PLANKS);

        site.set(34, CELLAR + 1, 16, Material.POLISHED_ANDESITE);
        generator = site.set(34, CELLAR + 2, 16, Site.directional(Material.OBSERVER, BlockFace.WEST));
        site.lamp(34, CELLAR + 3, 16);
        site.set(35, CELLAR + 1, 16, Material.IRON_BARS);
        site.set(35, CELLAR + 1, 15, Material.COPPER_BLOCK);
        site.lantern(28, GROUND - 2, 16, true);
        site.lantern(33, GROUND - 2, 12, true);
        site.ladder(35, CELLAR + 1, GROUND - 1, 18, BlockFace.NORTH);
        site.hatch(35, GROUND, 18, BlockFace.NORTH, Material.SPRUCE_TRAPDOOR);
        site.set(27, CELLAR + 1, 18, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(28, CELLAR + 1, 18, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(27, CELLAR + 2, 18, Material.COBWEB);
    }

    /**
     * The mill: a stone ground floor with the millstone and a door on either side, the sack floor above with a
     * loading hatch over the yard, and the grain loft under the roof.
     */
    private void mill() {
        int top = site.shell(X1, Z1, X2, Z2, GROUND, 3, Material.SPRUCE_PLANKS, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_PLANKS);
        for (int x = X1; x <= X2; x++) {
            for (int z = Z1; z <= Z2; z++) {
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    if ((x == X1 || x == X2 || z == Z1 || z == Z2) && site.block(x, y, z).getType() == Material.SPRUCE_PLANKS) {
                        site.set(x, y, z, site.worn(Material.COBBLESTONE));
                    }
                }
            }
        }
        site.roof(X1, Z1, X2, Z2, top + 1, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.SPRUCE_PLANKS);
        site.door(AXLE_X, GROUND + 1, Z1, BlockFace.NORTH, Material.SPRUCE_DOOR);
        site.door(X1, GROUND + 1, 16, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.set(AXLE_X, GROUND, Z1 - 1, Material.COBBLESTONE);
        site.lantern(AXLE_X + 1, GROUND + 3, Z1 - 1, false);
        site.set(AXLE_X + 1, GROUND + 2, Z1 - 1, Material.SPRUCE_FENCE);
        site.set(AXLE_X + 1, GROUND + 1, Z1 - 1, Material.SPRUCE_FENCE);
        site.hatch(35, GROUND, 18, BlockFace.NORTH, Material.SPRUCE_TRAPDOOR);

        var millstone = site.set(AXLE_X, GROUND + 1, 13, Material.GRINDSTONE.createBlockData(data -> {
            ((FaceAttachable) data).setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) data).setFacing(BlockFace.NORTH);
        }));
        site.chore(new Chore.Puzzle("millwork", millstone, false));
        site.set(AXLE_X, GROUND + 3, 13, Material.HOPPER);
        site.fill(28, GROUND + 1, 17, 29, GROUND + 1, 18, Material.WHITE_WOOL);
        site.set(28, GROUND + 2, 18, Material.WHITE_WOOL);
        site.set(33, GROUND + 1, 18, Material.HAY_BLOCK);
        site.set(32, GROUND + 1, 18, Site.directional(Material.BARREL, BlockFace.UP));
        site.lantern(29, GROUND + 3, 14, true);

        int sacks = GROUND + 4;
        for (int step = 0; step < 4; step++) {
            int z = 12 + step;
            if (step > 0) {
                site.fill(35, GROUND + 1, z, 35, GROUND + step, z, Material.SPRUCE_PLANKS);
            }
            site.set(35, GROUND + 1 + step, z, Site.stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH, Bisected.Half.BOTTOM));
            if (step < 3) {
                site.air(35, sacks, z, 35, sacks, z);
                site.fence(34, sacks + 1, z, Material.SPRUCE_FENCE);
            }
        }
        grainBin = site.set(28, sacks + 1, 17, Site.directional(Material.BARREL, BlockFace.UP));
        site.fill(29, sacks + 1, 18, 31, sacks + 1, 18, Material.WHITE_WOOL);
        site.set(30, sacks + 2, 18, Material.WHITE_WOOL);
        site.chest(27, sacks + 1, 12, BlockFace.EAST);
        site.air(29, sacks + 1, Z1, 29, sacks + 2, Z1);
        site.set(29, sacks + 4, Z1 - 1, Site.log(Material.SPRUCE_LOG, Axis.Z));
        site.set(29, sacks + 3, Z1 - 1, Material.IRON_CHAIN);
        site.lantern(32, sacks + 3, 15, true);

        int loft = GROUND + 8;
        site.ladder(27, sacks + 1, loft, 18, BlockFace.EAST);
        site.fill(32, loft + 1, 17, 35, loft + 1, 18, Material.HAY_BLOCK);
        site.set(34, loft + 2, 18, Material.HAY_BLOCK);
        site.set(29, loft + 1, 12, Material.COMPOSTER);
        site.set(30, loft + 1, 12, Material.COMPOSTER);
        site.set(33, loft + 1, 12, Material.COBWEB);
        site.lantern(31, loft + 3, 15, true);

        site.fill(27, GROUND + 1, 7, 28, GROUND + 1, 7, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
        site.fences(26, GROUND + 1, 7, 26, GROUND + 1, 7, Material.SPRUCE_FENCE);
        site.set(27, GROUND + 2, 7, Material.WHITE_WOOL);
        site.set(28, GROUND + 2, 7, Material.HAY_BLOCK);
        site.set(34, GROUND + 1, 8, Material.HAY_BLOCK);
        site.set(34, GROUND + 2, 8, Material.HAY_BLOCK);
        site.set(35, GROUND + 1, 8, Material.HAY_BLOCK);
    }

    /** The undershot wheel in a pit of deeper water south of the mill, turning on the axle through the wall. */
    private void wheel() {
        for (int x = X1; x <= X2; x++) {
            for (int z = BROOK_NORTH; z <= BROOK_SOUTH; z++) {
                site.set(x, AXLE_Y - 6, z, Material.STONE);
                site.fill(x, AXLE_Y - 5, z, x, GROUND - 1, z, Material.WATER);
                site.air(x, GROUND, z, x, GROUND + 2, z);
            }
        }
        for (int z = BROOK_NORTH; z <= BROOK_NORTH + 1; z++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    double distance = Math.sqrt(dx * dx + dy * dy);
                    boolean rim = distance >= 3.5 && distance < 4.5;
                    boolean spoke = distance < 3.5 && (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy));
                    if (rim) {
                        site.set(AXLE_X + dx, AXLE_Y + dy, z, Material.SPRUCE_PLANKS);
                    } else if (spoke) {
                        site.fence(AXLE_X + dx, AXLE_Y + dy, z, Material.SPRUCE_FENCE);
                    }
                }
            }
            site.set(AXLE_X, AXLE_Y, z, Site.log(Material.STRIPPED_SPRUCE_LOG, Axis.Z));
        }
    }

    /** A wooden flume on posts carries water from the mill pond to the top of the wheel. */
    private void flume() {
        int y = AXLE_Y + 5;
        for (int x = 33; x <= 38; x++) {
            if (x >= 34) {
                site.fill(x, y - 1, BROOK_NORTH, x, y - 1, BROOK_NORTH + 1, Material.SPRUCE_PLANKS);
            }
            site.fill(x, y, BROOK_NORTH, x, y, BROOK_NORTH + 1, Material.WATER);
            if (x > X2) {
                site.set(x, y, BROOK_NORTH - 1, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
            }
            site.set(x, y, BROOK_NORTH + 2, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
        }
        for (int z : new int[]{BROOK_NORTH - 1, BROOK_NORTH + 2}) {
            site.fences(37, GROUND - 2, z, 37, y - 1, z, Material.SPRUCE_FENCE);
        }
    }

    /**
     * The smugglers' tunnel from the inn's cellar to the gear room, under the lane between the bakery and the mill.
     * Both ends are iron doors; a hatch among the crates in the lane lets anyone else down.
     */
    private void smugglersTunnel() {
        site.rough(23, CELLAR, 0, 25, CELLAR + 4, 14, Material.COBBLESTONE);
        site.air(24, CELLAR + 1, 0, 25, CELLAR + 3, 13);
        site.traitorDoor(X1, CELLAR + 1, 13, BlockFace.WEST);
        site.torch(25, CELLAR + 2, 4, BlockFace.WEST);
        site.torch(24, CELLAR + 2, 12, BlockFace.EAST);
        site.ladder(24, CELLAR + 1, GROUND - 1, 9, BlockFace.EAST);
        site.hatch(24, GROUND, 9, BlockFace.EAST, Material.SPRUCE_TRAPDOOR);
        site.set(23, GROUND + 1, 8, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(23, GROUND + 1, 10, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(23, GROUND + 2, 10, Site.directional(Material.BARREL, BlockFace.EAST));
        site.set(25, GROUND + 1, 10, Material.HAY_BLOCK);
    }

    /** The stone bridge of the main street, a plank footbridge in the west and stepping stones east of the bridge. */
    private void bridges() {
        site.air(-3, GROUND + 1, BROOK_NORTH - 3, 3, GROUND + 3, BROOK_SOUTH + 3);
        for (int x = -3; x <= 3; x++) {
            boolean parapet = Math.abs(x) == 3;
            for (int z = BROOK_NORTH - 1; z <= BROOK_SOUTH + 1; z++) {
                boolean abutment = z == BROOK_NORTH - 1 || z == BROOK_SOUTH + 1;
                boolean springer = z == BROOK_NORTH || z == BROOK_SOUTH;
                if (abutment) {
                    site.rough(x, GROUND - 3, z, x, GROUND, z, Material.STONE_BRICKS);
                } else if (springer) {
                    site.set(x, GROUND, z, Site.stairs(Material.STONE_BRICK_STAIRS, z == BROOK_NORTH ? BlockFace.NORTH : BlockFace.SOUTH, Bisected.Half.TOP));
                } else {
                    site.air(x, GROUND, z, x, GROUND, z);
                }
                site.set(x, GROUND + 1, z, site.worn(Material.STONE_BRICKS));
                if (parapet) {
                    site.wall(x, GROUND + 2, z, Material.STONE_BRICK_WALL);
                }
            }
            if (!parapet) {
                site.set(x, GROUND + 1, BROOK_NORTH - 2, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.SOUTH, Bisected.Half.BOTTOM));
                site.set(x, GROUND + 1, BROOK_SOUTH + 2, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
            }
        }
        for (int x : new int[]{-3, 3}) {
            for (int z : new int[]{BROOK_NORTH - 1, BROOK_SOUTH + 1}) {
                site.set(x, GROUND + 2, z, Material.STONE_BRICKS);
                site.lantern(x, GROUND + 3, z, false);
            }
        }

        site.air(-31, GROUND + 1, BROOK_NORTH - 2, -29, GROUND + 2, BROOK_SOUTH + 2);
        for (int z = BROOK_NORTH - 2; z <= BROOK_SOUTH + 2; z++) {
            site.set(-30, GROUND, z, Material.SPRUCE_PLANKS);
            if (z >= BROOK_NORTH - 1 && z <= BROOK_SOUTH + 1) {
                site.set(-31, GROUND, z, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
                site.set(-29, GROUND, z, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
                site.fence(-31, GROUND + 1, z, Material.SPRUCE_FENCE);
                site.fence(-29, GROUND + 1, z, Material.SPRUCE_FENCE);
            }
        }
        site.fences(-31, GROUND - 2, BROOK_NORTH + 1, -31, GROUND - 1, BROOK_NORTH + 1, Material.SPRUCE_FENCE);
        site.fences(-29, GROUND - 2, BROOK_SOUTH - 1, -29, GROUND - 1, BROOK_SOUTH - 1, Material.SPRUCE_FENCE);
        site.fences(-31, GROUND + 2, BROOK_NORTH - 1, -31, GROUND + 2, BROOK_NORTH - 1, Material.SPRUCE_FENCE);
        site.lantern(-31, GROUND + 3, BROOK_NORTH - 1, false);

        int[][] stones = {{12, BROOK_NORTH}, {13, BROOK_NORTH + 1}, {12, BROOK_NORTH + 2}, {13, BROOK_SOUTH}};
        for (int[] stone : stones) {
            site.set(stone[0], GROUND - 1, stone[1], site.random.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
            site.air(stone[0], GROUND, stone[1], stone[0], GROUND + 1, stone[1]);
        }
    }
}
