package me.blockcraft.ttt.arena;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Slab;

import java.util.List;

import static me.blockcraft.ttt.arena.Site.GROUND;
import static me.blockcraft.ttt.arena.Site.TERRACE;

/**
 * The land Schattenbach stands on: meadow over stone, the abbey's terrace in the north-west behind a retaining wall,
 * the brook that crosses the south from the mill pond in the east, the quarry pit beyond it, a hedge at the edge
 * and a forest outside the border so the void is never the first thing anyone sees.
 */
final class Terrain {

    static final int EDGE = 46;
    /** The terrace covers everything north of this row and west of {@link #TERRACE_EAST}; the row itself is its wall. */
    static final int TERRACE_SOUTH = -16;
    static final int TERRACE_EAST = 16;
    static final int BROOK_NORTH = 20;
    static final int BROOK_SOUTH = 23;

    private static final int CLEARED = 56;
    private static final int BEDROCK = 53;
    private static final List<Material> FLOWERS = List.of(Material.DANDELION, Material.POPPY, Material.AZURE_BLUET,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.RED_TULIP, Material.WHITE_TULIP, Material.ALLIUM);

    private final Site site;

    Terrain(Site site) {
        this.site = site;
    }

    static boolean onTerrace(int x, int z) {
        return x <= TERRACE_EAST && z <= TERRACE_SOUTH;
    }

    static int surface(int x, int z) {
        return onTerrace(x, z) ? TERRACE : GROUND;
    }

    void build() {
        ground();
        terraceWall();
        brook();
        millPond();
        quarry();
        hedge();
    }

    private void ground() {
        for (int x = -CLEARED; x <= CLEARED; x++) {
            for (int z = -CLEARED; z <= CLEARED; z++) {
                int top = surface(x, z);
                site.set(x, BEDROCK, z, Material.BEDROCK);
                site.fill(x, BEDROCK + 1, z, x, GROUND - 4, z, Material.STONE);
                site.fill(x, GROUND - 3, z, x, top - 1, z, Material.DIRT);
                site.set(x, top, z, patch(x, z));
            }
        }
    }

    private Material patch(int x, int z) {
        double noise = Math.sin(x * 0.37 + z * 0.11) + Math.cos(z * 0.29 - x * 0.17) + site.random.nextDouble() * 0.6;
        if (noise > 1.85) {
            return Material.COARSE_DIRT;
        }
        if (noise < -1.75) {
            return Material.MOSS_BLOCK;
        }
        return Material.GRASS_BLOCK;
    }

    /**
     * The terrace's retaining wall of old stone with a parapet, buttresses every few blocks, ivy, and three ways up:
     * the abbey stairs from the market, the narrow west stairs and the stairs down to the farm in the east.
     */
    private void terraceWall() {
        for (int x = -CLEARED; x <= TERRACE_EAST; x++) {
            site.rough(x, GROUND - 3, TERRACE_SOUTH, x, TERRACE, TERRACE_SOUTH, Material.STONE_BRICKS);
            if (Math.abs(x) <= EDGE) {
                site.wall(x, TERRACE + 1, TERRACE_SOUTH, Material.STONE_BRICK_WALL);
            }
            if (Math.floorMod(x, 7) == 3 && x < TERRACE_EAST - 1) {
                site.rough(x, GROUND + 1, TERRACE_SOUTH + 1, x, GROUND + 2, TERRACE_SOUTH + 1, Material.STONE_BRICKS);
                site.set(x, GROUND + 3, TERRACE_SOUTH + 1, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
            } else if (site.random.nextInt(5) == 0) {
                int length = 1 + site.random.nextInt(3);
                site.fill(x, TERRACE - length, TERRACE_SOUTH + 1, x, TERRACE, TERRACE_SOUTH + 1,
                        Material.VINE.createBlockData(data -> ((MultipleFacing) data).setFace(BlockFace.NORTH, true)));
            }
        }
        for (int z = -CLEARED; z <= TERRACE_SOUTH; z++) {
            site.rough(TERRACE_EAST, GROUND - 3, z, TERRACE_EAST, TERRACE, z, Material.STONE_BRICKS);
            if (Math.abs(z) <= EDGE) {
                site.wall(TERRACE_EAST, TERRACE + 1, z, Material.STONE_BRICK_WALL);
            }
            if (Math.floorMod(z, 7) == 2 && z < TERRACE_SOUTH - 1) {
                site.rough(TERRACE_EAST + 1, GROUND + 1, z, TERRACE_EAST + 1, GROUND + 2, z, Material.STONE_BRICKS);
                site.set(TERRACE_EAST + 1, GROUND + 3, z, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.WEST, Bisected.Half.BOTTOM));
            }
        }
        stairsUp(-2, 2, true);
        stairsUp(-42, -41, false);
        for (int step = 0; step < 4; step++) {
            int x = TERRACE_EAST + 1 + step;
            for (int z = -30; z <= -28; z++) {
                site.fill(x, GROUND + 1, z, x, TERRACE - step - 1, z, Material.STONE_BRICKS);
                site.set(x, TERRACE - step, z, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.WEST, Bisected.Half.BOTTOM));
            }
            for (int z : new int[]{-31, -27}) {
                site.rough(x, GROUND + 1, z, x, TERRACE - step, z, Material.STONE_BRICKS);
                site.wall(x, TERRACE - step + 1, z, Material.STONE_BRICK_WALL);
            }
        }
        site.air(TERRACE_EAST, TERRACE + 1, -30, TERRACE_EAST, TERRACE + 1, -28);
        site.lantern(TERRACE_EAST + 4, GROUND + 2, -31, false);
    }

    /** Stairs from the village onto the terrace between {@code x1} and {@code x2}, with posts and lamps beside them. */
    private void stairsUp(int x1, int x2, boolean lamps) {
        for (int step = 0; step < 4; step++) {
            int z = TERRACE_SOUTH + 4 - step;
            for (int x = x1; x <= x2; x++) {
                site.fill(x, GROUND + 1, z, x, GROUND + step, z, Material.STONE_BRICKS);
                site.set(x, GROUND + 1 + step, z, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
            }
            for (int x : new int[]{x1 - 1, x2 + 1}) {
                site.rough(x, GROUND + 1, z, x, GROUND + 1 + step, z, Material.STONE_BRICKS);
                site.wall(x, GROUND + 2 + step, z, Material.STONE_BRICK_WALL);
            }
        }
        site.air(x1, TERRACE + 1, TERRACE_SOUTH, x2, TERRACE + 1, TERRACE_SOUTH);
        for (int x = x1; x <= x2; x++) {
            site.set(x, TERRACE, TERRACE_SOUTH, Material.POLISHED_ANDESITE);
        }
        if (lamps) {
            for (int x : new int[]{x1 - 1, x2 + 1}) {
                site.rough(x, TERRACE + 1, TERRACE_SOUTH, x, TERRACE + 1, TERRACE_SOUTH, Material.STONE_BRICKS);
                site.lantern(x, TERRACE + 2, TERRACE_SOUTH, false);
            }
        }
    }

    /**
     * The brook, two blocks deep and four wide, with banks that wander a little: reeds, gravel and a lily pad here
     * and there. It runs under the hedge on both sides through an iron grate.
     */
    private void brook() {
        for (int x = -CLEARED; x <= CLEARED; x++) {
            int north = BROOK_NORTH - (site.random.nextInt(4) == 0 ? 1 : 0);
            int south = BROOK_SOUTH + (site.random.nextInt(4) == 0 ? 1 : 0);
            for (int z = north - 1; z <= south + 1; z++) {
                if (z < north || z > south) {
                    site.set(x, GROUND, z, site.random.nextInt(3) == 0 ? Material.MUD : site.random.nextBoolean() ? Material.GRAVEL : Material.GRASS_BLOCK);
                    if (site.random.nextInt(4) == 0) {
                        site.fill(x, GROUND + 1, z, x, GROUND + 1 + site.random.nextInt(2), z, Material.SUGAR_CANE);
                    }
                    continue;
                }
                boolean shallow = z == north || z == south;
                site.set(x, GROUND - 3, z, site.random.nextBoolean() ? Material.GRAVEL : Material.CLAY);
                site.set(x, GROUND - 2, z, shallow ? Material.SAND : Material.WATER);
                site.set(x, GROUND - 1, z, Material.WATER);
                site.air(x, GROUND, z, x, GROUND + 2, z);
                if (site.random.nextInt(14) == 0) {
                    site.set(x, GROUND, z, Material.LILY_PAD);
                }
            }
        }
        for (int x : new int[]{-EDGE, EDGE}) {
            for (int z = BROOK_NORTH - 1; z <= BROOK_SOUTH + 1; z++) {
                site.rough(x, GROUND - 3, z, x, GROUND - 3, z, Material.STONE_BRICKS);
                site.bars(x, GROUND - 2, z);
                site.bars(x, GROUND - 1, z);
                site.bars(x, GROUND, z);
                site.rough(x, GROUND + 1, z, x, GROUND + 1, z, Material.STONE_BRICKS);
                site.set(x, GROUND + 2, z, Site.slab(Material.STONE_BRICK_SLAB, Slab.Type.BOTTOM));
            }
        }
    }

    /** The mill pond in the east that feeds the brook. */
    private void millPond() {
        int cx = 43;
        int cz = 18;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double distance = Math.pow(dx / 6.0, 2) + Math.pow(dz / 8.5, 2);
                int x = cx + dx;
                int z = cz + dz;
                if (distance > 1.2 || x >= EDGE) {
                    continue;
                }
                if (distance > 0.9) {
                    site.set(x, GROUND, z, site.random.nextBoolean() ? Material.SAND : Material.MUD);
                    if (site.random.nextInt(3) == 0 && site.block(x, GROUND + 1, z).isEmpty()) {
                        site.fill(x, GROUND + 1, z, x, GROUND + 1 + site.random.nextInt(2), z, Material.SUGAR_CANE);
                    }
                    continue;
                }
                int depth = distance < 0.35 ? 4 : distance < 0.7 ? 3 : 2;
                site.set(x, GROUND - depth - 1, z, Material.CLAY);
                site.fill(x, GROUND - depth, z, x, GROUND - 1, z, Material.WATER);
                site.air(x, GROUND, z, x, GROUND + 2, z);
                if (site.random.nextInt(8) == 0) {
                    site.set(x, GROUND, z, Material.LILY_PAD);
                }
            }
        }
        for (int z = 16; z <= 21; z++) {
            site.set(39, GROUND, z, Material.SPRUCE_PLANKS);
            site.air(39, GROUND + 1, z, 39, GROUND + 2, z);
        }
        site.fence(40, GROUND + 1, 16, Material.SPRUCE_FENCE);
        site.fence(40, GROUND + 1, 21, Material.SPRUCE_FENCE);
    }

    /**
     * The quarry pit in the south-west: a ledge three blocks down and a floor seven down, cut in layers of stone,
     * tuff and gravel with a seam of coal; stairs lead down from the north, a ladder from the ledge to the floor.
     */
    private void quarry() {
        for (int x = -44; x <= -26; x++) {
            for (int z = 28; z <= 44; z++) {
                boolean ledge = x >= -42 && x <= -28 && z >= 30 && z <= 42;
                boolean floor = x >= -39 && x <= -31 && z >= 33 && z <= 40;
                if (!ledge) {
                    continue;
                }
                int top = floor ? GROUND - 7 : GROUND - 3;
                for (int y = top - 1; y <= GROUND - 3; y++) {
                    site.set(x, y, z, layer(y));
                }
                site.set(x, top, z, floor ? (site.random.nextInt(4) == 0 ? Material.GRAVEL : Material.STONE) : (site.random.nextInt(3) == 0 ? Material.GRAVEL : Material.COARSE_DIRT));
                site.air(x, top + 1, z, x, GROUND + 3, z);
            }
        }
        for (int x = -44; x <= -26; x++) {
            for (int z = 28; z <= 44; z++) {
                boolean ledge = x >= -42 && x <= -28 && z >= 30 && z <= 42;
                if (ledge) {
                    continue;
                }
                for (int y = GROUND - 3; y <= GROUND; y++) {
                    site.set(x, y, z, layer(y));
                }
                site.set(x, GROUND, z, site.random.nextInt(3) == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
            }
        }
        for (int x = -46; x <= -24; x++) {
            for (int z = 45; z <= CLEARED; z++) {
                int height = 2 + (int) (3 * Math.abs(Math.sin(x * 0.4)) + (z - 45) * 0.8);
                for (int y = GROUND + 1; y <= GROUND + Math.min(height, 12); y++) {
                    site.set(x, y, z, layer(y));
                }
            }
        }
        for (int step = 0; step < 2; step++) {
            int z = 28 + step;
            for (int x = -36; x <= -34; x++) {
                site.set(x, GROUND - 1 - step, z, Site.stairs(Material.COBBLESTONE_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
                site.air(x, GROUND - step, z, x, GROUND + 2, z);
            }
        }
        for (int step = 0; step < 4; step++) {
            int x = -34 + step;
            site.fill(x, GROUND - 7, 40, x, GROUND - 7 + step, 40, Material.STONE);
            site.set(x, GROUND - 6 + step, 40, Site.stairs(Material.STONE_STAIRS, BlockFace.EAST, Bisected.Half.BOTTOM));
            site.air(x, GROUND - 5 + step, 40, x, GROUND - 3 + step, 40);
        }
        site.ladder(-39, GROUND - 6, GROUND - 3, 36, BlockFace.EAST);
    }

    private Material layer(int y) {
        int roll = site.random.nextInt(100);
        if (roll < 3) {
            return Material.COAL_ORE;
        }
        if (roll < 5) {
            return Material.IRON_ORE;
        }
        return switch (Math.floorMod(y, 5)) {
            case 0 -> Material.ANDESITE;
            case 1 -> roll < 50 ? Material.TUFF : Material.STONE;
            case 2 -> Material.STONE;
            case 3 -> roll < 30 ? Material.GRAVEL : Material.COBBLESTONE;
            default -> Material.STONE;
        };
    }

    /** A hedge closes the playing field, and on the terrace the abbey's own wall does. */
    private void hedge() {
        for (int x = -EDGE; x <= EDGE; x++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                if (Math.abs(x) != EDGE && Math.abs(z) != EDGE) {
                    continue;
                }
                if (z >= BROOK_NORTH - 1 && z <= BROOK_SOUTH + 1 || z >= 45 && x <= -24) {
                    continue;
                }
                int top = surface(x, z);
                if (onTerrace(x, z)) {
                    site.rough(x, top + 1, z, x, top + 3, z, Material.STONE_BRICKS);
                    if (Math.floorMod(x + z, 2) == 0) {
                        site.rough(x, top + 4, z, x, top + 4, z, Material.STONE_BRICKS);
                    }
                } else if (!site.block(x, top, z).isLiquid()) {
                    site.fill(x, top + 1, z, x, top + 2, z, Site.leaves(Material.OAK_LEAVES));
                }
            }
        }
    }

    /** Trees in a ring beyond the hedge and wherever there is room inside it. */
    void forest() {
        for (int d = -CLEARED + 2; d <= CLEARED - 2; d += 4) {
            for (int outside : new int[]{-(EDGE + 5), EDGE + 5}) {
                tryTree(d + site.random.nextInt(3) - 1, outside + site.random.nextInt(3) - 1, 1);
                tryTree(outside + site.random.nextInt(3) - 1, d + site.random.nextInt(3) - 1, 1);
            }
        }
        for (int attempt = 0; attempt < 320; attempt++) {
            int x = site.random.nextInt(2 * EDGE - 5) - EDGE + 3;
            int z = site.random.nextInt(2 * EDGE - 5) - EDGE + 3;
            int reach = Math.max(Math.abs(x), Math.abs(z));
            if (reach >= 38 || site.random.nextInt(3) == 0) {
                tryTree(x, z, 2);
            }
        }
        for (int attempt = 0; attempt < 140; attempt++) {
            int x = site.random.nextInt(2 * EDGE - 3) - EDGE + 2;
            int z = site.random.nextInt(2 * EDGE - 3) - EDGE + 2;
            if (clearAround(x, z, 0)) {
                int y = surface(x, z) + 1;
                site.set(x, y, z, Site.leaves(site.random.nextBoolean() ? Material.OAK_LEAVES : Material.AZALEA_LEAVES));
            }
        }
    }

    private void tryTree(int x, int z, int room) {
        if (clearAround(x, z, room)) {
            var log = switch (site.random.nextInt(6)) {
                case 0, 1 -> Material.OAK_LOG;
                case 2 -> Material.BIRCH_LOG;
                case 3, 4 -> Material.SPRUCE_LOG;
                default -> Material.DARK_OAK_LOG;
            };
            site.tree(x, surface(x, z), z, log);
        }
    }

    /** True if the ground at x/z and around it in {@code radius} is open grass with nothing on it. */
    private boolean clearAround(int x, int z, int radius) {
        int top = surface(x, z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (surface(x + dx, z + dz) != top) {
                    return false;
                }
                var ground = site.block(x + dx, top, z + dz).getType();
                if (ground != Material.GRASS_BLOCK && ground != Material.MOSS_BLOCK && ground != Material.COARSE_DIRT) {
                    return false;
                }
                for (int y = top + 1; y <= top + 4; y++) {
                    if (!site.block(x + dx, y, z + dz).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Grass, flowers, bushes and leaf litter on the open ground, so that no lawn is bare. */
    void decorate() {
        for (int x = -EDGE + 1; x < EDGE; x++) {
            for (int z = -EDGE + 1; z < EDGE; z++) {
                int top = surface(x, z);
                var ground = site.block(x, top, z).getType();
                if ((ground != Material.GRASS_BLOCK && ground != Material.MOSS_BLOCK) || !site.block(x, top + 1, z).isEmpty()) {
                    continue;
                }
                int roll = site.random.nextInt(100);
                if (roll < 14) {
                    site.set(x, top + 1, z, Material.SHORT_GRASS);
                } else if (roll < 17) {
                    site.set(x, top + 1, z, FLOWERS.get(site.random.nextInt(FLOWERS.size())));
                } else if (roll < 19 && site.block(x, top + 2, z).isEmpty()) {
                    site.set(x, top + 1, z, Site.half(Material.TALL_GRASS, Bisected.Half.BOTTOM));
                    site.set(x, top + 2, z, Site.half(Material.TALL_GRASS, Bisected.Half.TOP));
                } else if (roll < 21) {
                    site.set(x, top + 1, z, Material.LEAF_LITTER);
                } else if (roll < 22) {
                    site.set(x, top + 1, z, Material.BUSH);
                }
            }
        }
    }
}
