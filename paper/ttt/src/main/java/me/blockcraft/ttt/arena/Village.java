package me.blockcraft.ttt.arena;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Slab;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static me.blockcraft.ttt.arena.Site.CELLAR;
import static me.blockcraft.ttt.arena.Site.GROUND;

/**
 * The village below the abbey: the market with its fountain and maypole where the round starts, the town hall with
 * the traitor tester and its lava pit, the smithy whose flat roof the town hall's balcony reaches, the apothecary,
 * the cobbler's house above the old mine tunnel, the gate tower, the inn with its cellar, the bakery, the weaver's
 * house and the well square.
 */
final class Village {

    private static final int SPAWNS = 12;
    private static final double SPAWN_RADIUS = 6;
    private static final double CENTRE_X = 0.5;
    private static final double CENTRE_Z = -1.5;
    private static final int FLOOR = GROUND;
    private static final int UPPER = GROUND + 4;

    private final Site site;
    private TesterRoom tester;
    private Block letterDesk;
    private Block kettle;
    private Block anvil;

    Village(Site site) {
        this.site = site;
    }

    TesterRoom tester() {
        return tester;
    }

    /** Where the charter for the abbot is collected. */
    Block letterDesk() {
        return letterDesk;
    }

    /** Where the abbey's herbs go. */
    Block kettle() {
        return kettle;
    }

    /** Where the horseshoes for the farm are made. */
    Block anvil() {
        return anvil;
    }

    void build() {
        streets();
        market();
        townHall();
        smithy();
        apothecary();
        cobbler();
        mineTunnel();
        cottage();
        gateTower();
        inn();
        bakery();
        weaver();
        wellSquare();
        pondCottage();
    }

    /** Where the round starts: a ring round the fountain, everyone facing it. */
    List<Location> spawns() {
        var spawns = new ArrayList<Location>();
        for (int index = 0; index < SPAWNS; index++) {
            double angle = 2 * Math.PI * index / SPAWNS;
            double x = CENTRE_X + Math.sin(angle) * SPAWN_RADIUS;
            double z = CENTRE_Z + Math.cos(angle) * SPAWN_RADIUS;
            float yaw = (float) Math.toDegrees(Math.atan2(x - CENTRE_X, -(z - CENTRE_Z)));
            spawns.add(new Location(site.world, Math.floor(x) + 0.5, GROUND + 1, Math.floor(z) + 0.5, yaw, 0));
        }
        return List.copyOf(spawns);
    }

    /** Packed earth for the lanes, cobbles where the carts turn, gravel at the edges. */
    private void streets() {
        site.pave(-2, 8, 2, 17);
        site.pave(-27, 3, -12, 5);
        site.pave(-45, -3, -27, 1);
        site.pave(-40, -15, -12, -11);
        site.pave(12, 0, 34, 4);
        site.pave(12, -15, 13, 7);
        site.pave(23, 5, 25, 18);
        site.pave(-15, 6, -12, 18);
        site.pave(-2, 26, 2, 30);
        site.pave(29, -15, 31, 0);
        site.pave(-2, -15, 2, -11);
    }

    /**
     * Cobbles between the houses with a fountain in the middle, a maypole, two market stalls, lamps at the corners
     * and a hay cart; the ring round the fountain stays clear for the spawns.
     */
    private void market() {
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 7; z++) {
                boolean frayed = (Math.abs(x) == 11 || z == -11 || z == 7) && site.random.nextInt(3) == 0;
                site.set(x, GROUND, z, frayed ? Material.GRAVEL : switch (site.random.nextInt(7)) {
                    case 0 -> Material.ANDESITE;
                    case 1 -> Material.MOSSY_COBBLESTONE;
                    case 2, 3 -> Material.POLISHED_ANDESITE;
                    default -> Material.COBBLESTONE;
                });
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -4; z <= 0; z++) {
                boolean rim = Math.abs(x) == 2 || z == -4 || z == 0;
                site.set(x, GROUND, z, Material.STONE_BRICKS);
                site.set(x, GROUND + 1, z, rim ? Site.slab(Material.STONE_BRICK_SLAB, Slab.Type.BOTTOM) : Material.WATER.createBlockData());
            }
        }
        site.fill(0, GROUND + 1, -2, 0, GROUND + 3, -2, Material.CHISELED_STONE_BRICKS);
        site.set(0, GROUND + 4, -2, Material.WATER);
        for (var side : Site.SIDES) {
            site.set(side.getModX(), GROUND + 3, -2 + side.getModZ(), Site.stairs(Material.STONE_BRICK_STAIRS, side.getOppositeFace(), Bisected.Half.TOP));
        }

        site.fill(9, GROUND + 1, 2, 9, GROUND + 11, 2, Material.STRIPPED_BIRCH_LOG);
        for (int y = GROUND + 2; y <= GROUND + 10; y += 2) {
            site.set(9, y, 2, Material.STRIPPED_SPRUCE_LOG);
        }
        for (var side : Site.SIDES) {
            site.set(9 + side.getModX(), GROUND + 9, 2 + side.getModZ(), Site.leaves(Material.SPRUCE_LEAVES));
        }
        site.set(9, GROUND + 12, 2, Site.leaves(Material.SPRUCE_LEAVES));
        stall(-9, -8, Material.RED_WOOL, Material.PUMPKIN);
        stall(-9, 5, Material.YELLOW_WOOL, Material.MELON);
        stall(9, -8, Material.BLUE_WOOL, Material.HAY_BLOCK);
        for (int[] post : new int[][]{{-9, -1}, {-7, 7}, {7, -11}, {-7, -11}, {6, 7}}) {
            site.fences(post[0], GROUND + 1, post[1], post[0], GROUND + 3, post[1], Material.DARK_OAK_FENCE);
            site.lantern(post[0], GROUND + 4, post[1], false);
        }
        site.fill(-10, GROUND + 1, 1, -10, GROUND + 1, 3, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
        site.fences(-11, GROUND + 1, 1, -11, GROUND + 1, 3, Material.SPRUCE_FENCE);
        site.set(-10, GROUND + 2, 1, Material.HAY_BLOCK);
        site.set(-10, GROUND + 2, 2, Material.HAY_BLOCK);
        site.set(-10, GROUND + 3, 2, Material.HAY_BLOCK);
        for (int x : new int[]{-6, -5, 5, 6}) {
            site.chair(x, GROUND + 1, -10, BlockFace.SOUTH, Material.SPRUCE_STAIRS);
        }
    }

    private void stall(int x, int z, Material canopy, Material ware) {
        site.fill(x - 1, GROUND + 1, z, x + 1, GROUND + 1, z, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(x, GROUND + 2, z, ware);
        site.set(x - 1, GROUND + 2, z, Material.FLOWER_POT);
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-1, 1}) {
                site.fences(x + dx, GROUND + 1, z + dz, x + dx, GROUND + 3, z + dz, Material.SPRUCE_FENCE);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                site.set(x + dx, GROUND + 4, z + dz, Math.floorMod(dx, 2) == 0 ? canopy : Material.WHITE_WOOL);
            }
        }
    }

    /**
     * The town hall west of the market: stone below and timber above. The ground floor holds the tester between a
     * closet and the archive, the clerk's desk and a hatch down to the Ratskeller; the lava pit under the tester's
     * floor shows through a grate in the Ratskeller's wall, and the trap's button hides beside it behind the kegs.
     * Upstairs, the council chamber opens south onto a balcony that bridges the lane to the smithy's roof.
     */
    private void townHall() {
        int x1 = -26;
        int x2 = -14;
        int z1 = -10;
        int z2 = 2;
        site.vault(x1, CELLAR, -6, x2, GROUND - 1, z2, Material.STONE_BRICKS);
        int top = site.shell(x1, z1, x2, z2, GROUND, 2, Material.STONE_BRICKS, Material.DARK_OAK_LOG, Material.SPRUCE_PLANKS);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = UPPER + 1; y <= UPPER + 3; y++) {
                    if ((x == x1 || x == x2 || z == z1 || z == z2) && site.block(x, y, z).getType().name().endsWith("STONE_BRICKS")) {
                        site.set(x, y, z, Material.WHITE_TERRACOTTA);
                    }
                }
            }
        }
        site.roof(x1, z1, x2, z2, top + 1, Material.BRICK_STAIRS, Material.BRICK_SLAB, Material.WHITE_TERRACOTTA);
        site.door(x2, FLOOR + 1, -2, BlockFace.EAST, Material.DARK_OAK_DOOR);
        site.door(-20, FLOOR + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.door(-20, UPPER + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.set(x2 + 1, GROUND, -2, Material.POLISHED_ANDESITE);
        site.torch(x2 + 1, FLOOR + 3, -4, BlockFace.EAST);
        site.torch(x2 + 1, FLOOR + 3, 0, BlockFace.EAST);

        for (int x = x1 + 1; x < x2; x++) {
            site.fill(x, FLOOR + 1, -6, x, FLOOR + 3, -6, Material.POLISHED_ANDESITE);
        }
        for (int z = -9; z <= -7; z++) {
            site.fill(-23, FLOOR + 1, z, -23, FLOOR + 3, z, Material.POLISHED_ANDESITE);
            site.fill(-17, FLOOR + 1, z, -17, FLOOR + 3, z, Material.POLISHED_ANDESITE);
        }
        site.door(-24, FLOOR + 1, -6, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.door(-16, FLOOR + 1, -6, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.chest(-25, FLOOR + 1, -9, BlockFace.EAST);
        site.set(-25, FLOOR + 1, -7, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-15, FLOOR + 1, -9, Material.BOOKSHELF);
        site.set(-15, FLOOR + 2, -9, Material.BOOKSHELF);
        site.chest(-15, FLOOR + 1, -8, BlockFace.WEST);
        site.lantern(-24, FLOOR + 3, -8, true);
        site.lantern(-15, FLOOR + 3, -7, true);
        tester = testerRoom();

        site.ladder(-25, CELLAR + 1, GROUND - 1, 0, BlockFace.EAST);
        site.hatch(-25, GROUND, 0, BlockFace.EAST, Material.SPRUCE_TRAPDOOR);
        for (int step = 0; step < 4; step++) {
            int x = -16 - step;
            if (step > 0) {
                site.fill(x, FLOOR + 1, 1, x, FLOOR + step, 1, Material.SPRUCE_PLANKS);
            }
            site.set(x, FLOOR + 1 + step, 1, Site.stairs(Material.SPRUCE_STAIRS, BlockFace.WEST, Bisected.Half.BOTTOM));
            if (step < 3) {
                site.air(x, UPPER, 1, x, UPPER, 1);
                site.fence(x, UPPER + 1, 0, Material.SPRUCE_FENCE);
            }
        }
        letterDesk = site.set(-17, FLOOR + 1, -3, Material.CARTOGRAPHY_TABLE);
        site.chair(-18, FLOOR + 1, -3, BlockFace.EAST, Material.DARK_OAK_STAIRS);
        site.set(-17, FLOOR + 1, -4, Site.directional(Material.BARREL, BlockFace.UP));
        for (int x : new int[]{-24, -23, -22}) {
            site.chair(x, FLOOR + 1, -1, BlockFace.NORTH, Material.SPRUCE_STAIRS);
        }
        site.lantern(-20, FLOOR + 3, -3, true);
        site.lantern(-22, FLOOR + 3, 0, true);
        site.set(-15, FLOOR + 1, 1, Material.POTTED_FERN);

        ratskeller();

        for (int x = -22; x <= -18; x++) {
            site.table(x, UPPER + 1, -5);
            site.chair(x, UPPER + 1, -6, BlockFace.SOUTH, Material.DARK_OAK_STAIRS);
            site.chair(x, UPPER + 1, -4, BlockFace.NORTH, Material.DARK_OAK_STAIRS);
        }
        var ledger = site.set(-24, UPPER + 1, -5, Site.directional(Material.LECTERN, BlockFace.EAST));
        site.chore(new Chore.Puzzle("ledger", ledger, false));
        site.fill(-25, UPPER + 1, -9, -25, UPPER + 3, -7, Material.BOOKSHELF);
        site.fill(-25, UPPER + 1, -3, -25, UPPER + 3, -2, Material.BOOKSHELF);
        site.fill(-21, UPPER + 1, -9, -19, UPPER + 1, -9, Material.RED_CARPET);
        site.set(-20, UPPER + 1, -9, Site.directional(Material.DARK_OAK_STAIRS, BlockFace.NORTH));
        site.lantern(-20, UPPER + 3, -5, true);
        site.lantern(-17, UPPER + 3, -1, true);
        site.set(-15, UPPER + 1, -9, Site.directional(Material.BARREL, BlockFace.WEST));

        for (int x = -24; x <= -16; x++) {
            for (int z = 3; z <= 5; z++) {
                site.set(x, UPPER, z, Material.SPRUCE_PLANKS);
            }
        }
        for (int z = 3; z <= 5; z++) {
            site.fence(-25, UPPER + 1, z, Material.SPRUCE_FENCE);
            site.fence(-15, UPPER + 1, z, Material.SPRUCE_FENCE);
            site.set(-25, UPPER, z, Site.log(Material.DARK_OAK_LOG, Axis.Z));
            site.set(-15, UPPER, z, Site.log(Material.DARK_OAK_LOG, Axis.Z));
        }
        site.lantern(-20, UPPER - 1, 4, true);
        site.pot(-24, UPPER + 1, 3);
    }

    /**
     * The tester behind the hall's north wall: one doorway with lamps over it and three more on the back wall, which
     * looks through two windows onto the lane. Its floor drops into a pit of lava when the trap is sprung.
     */
    private TesterRoom testerRoom() {
        for (int x = -22; x <= -18; x++) {
            for (int z = -9; z <= -7; z++) {
                site.set(x, FLOOR, z, (x + z) % 2 == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE);
            }
        }
        site.rough(-23, CELLAR + 1, -10, -17, GROUND - 1, -6, Material.POLISHED_BLACKSTONE_BRICKS);
        site.fill(-22, CELLAR + 2, -9, -18, CELLAR + 3, -7, Material.LAVA);
        site.air(-22, CELLAR + 4, -9, -18, GROUND - 1, -7);
        site.bars(-20, CELLAR + 3, -6);
        site.bars(-20, CELLAR + 4, -6);

        var entrance = List.of(site.set(-20, FLOOR + 1, -6, Material.AIR), site.set(-20, FLOOR + 2, -6, Material.AIR));
        var lamps = new ArrayList<Block>();
        for (int x = -21; x <= -19; x++) {
            lamps.add(site.set(x, FLOOR + 3, -6, Material.REDSTONE_LAMP));
        }
        for (int x : new int[]{-22, -20, -18}) {
            lamps.add(site.set(x, FLOOR + 3, -10, Material.REDSTONE_LAMP));
        }
        var button = site.block(-20, FLOOR + 2, -9);
        site.button(-20, FLOOR + 2, -9, BlockFace.SOUTH, Material.STONE_BUTTON);
        site.button(-16, CELLAR + 2, -5, BlockFace.SOUTH, Material.STONE_BUTTON);
        var trapButton = site.block(-16, CELLAR + 2, -5);
        var floor = Box.of(-22, FLOOR, -9, -18, FLOOR, -7).blocks(site.world);
        return new TesterRoom(Box.of(-22, FLOOR + 1, -9, -18, FLOOR + 3, -7), button, entrance, List.copyOf(lamps),
                Optional.of(new TesterRoom.Trap(trapButton, floor)));
    }

    /** The town hall's wine cellar: kegs, a table and the grate onto the lava pit. */
    private void ratskeller() {
        for (int x = -25; x <= -15; x++) {
            for (int z = -5; z <= 1; z++) {
                site.set(x, CELLAR, z, site.random.nextInt(3) == 0 ? Material.COBBLESTONE : Material.STONE_BRICKS);
            }
        }
        for (int z = -4; z <= 0; z += 2) {
            site.set(-15, CELLAR + 1, z, Site.directional(Material.BARREL, BlockFace.WEST));
            site.set(-15, CELLAR + 2, z, Site.directional(Material.BARREL, BlockFace.WEST));
        }
        site.set(-15, CELLAR + 1, -5, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-15, CELLAR + 2, -5, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-17, CELLAR + 1, -5, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-16, CELLAR + 1, -5, Material.COBWEB);
        site.table(-21, CELLAR + 1, -2);
        site.chair(-22, CELLAR + 1, -2, BlockFace.EAST, Material.SPRUCE_STAIRS);
        site.chest(-25, CELLAR + 1, -5, BlockFace.SOUTH);
        site.lantern(-21, GROUND - 2, -2, true);
        site.set(-24, CELLAR + 1, 1, Material.COBWEB);
    }

    /**
     * The smithy south of the lane: open to the east, a forge with a chimney through the flat roof, the anvil the
     * farm's horseshoes come from, a grindstone and a quench trough. A ladder on the west wall and the town hall's
     * balcony both reach the roof.
     */
    private void smithy() {
        int x1 = -26;
        int x2 = -16;
        int z1 = 6;
        int z2 = 14;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                site.set(x, GROUND, z, site.random.nextInt(4) == 0 ? Material.COBBLESTONE : Material.STONE_BRICKS);
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                boolean wall = x == x1 || z == z1 || z == z2;
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    if (corner || (x == x2 && z == 10)) {
                        site.set(x, y, z, Material.SPRUCE_LOG);
                    } else if (wall) {
                        site.set(x, y, z, site.worn(Material.COBBLESTONE));
                    } else {
                        site.set(x, y, z, Material.AIR);
                    }
                }
                site.set(x, UPPER, z, x == x1 || x == x2 || z == z1 || z == z2 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS);
            }
        }
        site.air(-21, GROUND + 1, z2, -20, GROUND + 2, z2);
        for (int x = x1; x <= x2; x++) {
            if (x > -25) {
                continue;
            }
            site.fence(x, UPPER + 1, z1, Material.SPRUCE_FENCE);
        }
        for (int x = x1; x <= x2; x++) {
            site.fence(x, UPPER + 1, z2, Material.SPRUCE_FENCE);
        }
        for (int z = z1; z <= z2; z++) {
            site.fence(x2, UPPER + 1, z, Material.SPRUCE_FENCE);
            if (z != 10) {
                site.fence(x1, UPPER + 1, z, Material.SPRUCE_FENCE);
            }
        }
        site.ladder(x1 - 1, GROUND + 1, UPPER, 10, BlockFace.WEST);

        site.fill(-25, GROUND + 1, 7, -25, GROUND + 3, 9, Material.BRICKS);
        site.fill(-24, GROUND + 3, 7, -24, GROUND + 3, 9, Material.BRICKS);
        site.fill(-24, GROUND + 1, 7, -24, GROUND + 2, 7, Material.BRICKS);
        site.fill(-24, GROUND + 1, 9, -24, GROUND + 2, 9, Material.BRICKS);
        site.set(-24, GROUND + 2, 8, Material.BRICKS);
        site.fill(-25, UPPER, 8, -25, UPPER + 4, 8, Material.BRICKS);
        site.fire(-25, UPPER + 5, 8);
        var forge = site.unlit(-24, GROUND + 1, 8, Material.BLAST_FURNACE, BlockFace.EAST);
        site.chore(new Chore.Hold("forge", forge, true));
        anvil = site.set(-21, GROUND + 1, 9, Site.directional(Material.ANVIL, BlockFace.NORTH));
        site.set(-23, GROUND + 1, 11, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(3)));
        site.set(-19, GROUND + 1, 12, Material.GRINDSTONE.createBlockData(data -> {
            ((FaceAttachable) data).setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) data).setFacing(BlockFace.EAST);
        }));
        site.set(-25, GROUND + 1, 12, Material.SMITHING_TABLE);
        site.chest(-25, GROUND + 1, 13, BlockFace.EAST);
        site.fill(-23, GROUND + 1, 13, -22, GROUND + 1, 13, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-22, GROUND + 2, 13, Site.directional(Material.IRON_TRAPDOOR, BlockFace.NORTH));
        for (int x = -22; x <= -18; x += 2) {
            site.shutter(x, GROUND + 2, 7, BlockFace.SOUTH, Material.SPRUCE_TRAPDOOR);
        }
        site.lantern(-21, GROUND + 3, 11, true);
        site.lantern(-17, GROUND + 3, 7, true);
        site.set(-15, GROUND + 1, 12, Material.HAY_BLOCK);
        site.set(-15, GROUND + 1, 13, Site.log(Material.OAK_LOG, Axis.Z));
        site.set(-15, GROUND + 2, 13, Site.log(Material.OAK_LOG, Axis.Z));
    }

    /**
     * The apothecary west of the town hall: shelves of jars and herbs behind a counter, the brewing stand where the
     * remedy is mixed, and a bedroom upstairs.
     */
    private void apothecary() {
        int x1 = -42;
        int x2 = -34;
        int z1 = -10;
        int z2 = -4;
        int top = site.shell(x1, z1, x2, z2, GROUND, 2, Material.WHITE_TERRACOTTA, Material.DARK_OAK_LOG, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.WHITE_TERRACOTTA);
        site.door(-38, FLOOR + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.set(-38, GROUND, z2 + 1, Material.COBBLESTONE);
        site.lantern(-37, FLOOR + 3, z2 + 1, false);
        site.set(-37, FLOOR + 2, z2 + 1, Material.SPRUCE_FENCE);
        site.set(-37, FLOOR + 1, z2 + 1, Material.SPRUCE_FENCE);
        for (int x = -41; x <= -35; x++) {
            if (x == -38) {
                continue;
            }
            site.set(x, FLOOR + 1, -7, Site.stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH, Bisected.Half.TOP));
        }
        for (int x = -41; x <= -36; x++) {
            site.set(x, FLOOR + 1, -9, x % 2 == 0 ? Material.BOOKSHELF.createBlockData() : Site.directional(Material.BARREL, BlockFace.SOUTH));
            site.set(x, FLOOR + 2, -9, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
            site.pot(x, FLOOR + 3, -9);
        }
        var stand = site.set(-39, FLOOR + 1, -8, Material.BREWING_STAND);
        site.chore(new Chore.Puzzle("potion", stand, true));
        site.set(-41, FLOOR + 1, -8, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(2)));
        site.candles(-36, FLOOR + 2, -7, 2);
        site.chest(-41, FLOOR + 1, -5, BlockFace.EAST);
        site.lantern(-38, FLOOR + 3, -6, true);
        site.ladder(-35, FLOOR + 1, UPPER, -9, BlockFace.WEST);
        site.bed(-40, UPPER + 1, -8, BlockFace.NORTH, Material.GREEN_BED);
        site.set(-41, UPPER + 1, -9, Material.CRAFTING_TABLE);
        site.set(-36, UPPER + 1, -5, Site.directional(Material.BARREL, BlockFace.NORTH));
        site.fill(-39, UPPER + 1, -6, -37, UPPER + 1, -5, Material.GREEN_CARPET);
        site.lantern(-38, UPPER + 3, -7, true);
        for (int x = -41; x <= -36; x += 2) {
            site.set(x, UPPER + 3, -5, Site.leaves(Material.AZALEA_LEAVES));
        }
    }

    /**
     * The cobbler's house south of the west street. Its cellar is the old miners' store: tools, barrels, and the
     * opening of the tunnel that runs under the brook to the quarry.
     */
    private void cobbler() {
        int x1 = -42;
        int x2 = -33;
        int z1 = 3;
        int z2 = 11;
        site.vault(x1, CELLAR, z1, x2, GROUND - 1, z2, Material.COBBLESTONE);
        int top = site.shell(x1, z1, x2, z2, GROUND, 1, Material.OAK_PLANKS, Material.SPRUCE_LOG, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.OAK_PLANKS);
        site.door(-38, FLOOR + 1, z1, BlockFace.NORTH, Material.OAK_DOOR);
        site.set(-38, GROUND, z1 - 1, Material.COBBLESTONE);
        site.door(x2, FLOOR + 1, 8, BlockFace.EAST, Material.OAK_DOOR);
        site.lantern(-37, FLOOR + 3, z1 - 1, false);
        site.set(-37, FLOOR + 1, z1 - 1, Material.OAK_FENCE);
        site.set(-37, FLOOR + 2, z1 - 1, Material.OAK_FENCE);
        site.table(-38, FLOOR + 1, 7);
        site.chair(-39, FLOOR + 1, 7, BlockFace.EAST, Material.OAK_STAIRS);
        site.chair(-37, FLOOR + 1, 7, BlockFace.WEST, Material.OAK_STAIRS);
        site.bed(-35, FLOOR + 1, 10, BlockFace.NORTH, Material.BROWN_BED);
        site.set(-41, FLOOR + 1, 4, Material.CRAFTING_TABLE);
        site.set(-40, FLOOR + 1, 4, Site.directional(Material.BARREL, BlockFace.UP));
        site.chest(-34, FLOOR + 1, 4, BlockFace.WEST);
        site.lantern(-38, FLOOR + 3, 8, true);
        site.ladder(-41, CELLAR + 1, GROUND - 1, 10, BlockFace.EAST);
        site.hatch(-41, GROUND, 10, BlockFace.EAST, Material.SPRUCE_TRAPDOOR);

        for (int x = x1 + 1; x < x2; x++) {
            for (int z = z1 + 1; z < z2; z++) {
                site.set(x, CELLAR, z, site.random.nextBoolean() ? Material.GRAVEL : Material.COBBLESTONE);
            }
        }
        site.fill(-41, CELLAR + 1, 4, -39, CELLAR + 1, 4, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(-41, CELLAR + 2, 4, Site.directional(Material.BARREL, BlockFace.UP));
        site.chest(-34, CELLAR + 1, 5, BlockFace.WEST);
        site.set(-34, CELLAR + 1, 7, Site.directional(Material.BARREL, BlockFace.WEST));
        site.set(-34, CELLAR + 1, 8, Site.directional(Material.BARREL, BlockFace.WEST));
        site.set(-39, CELLAR + 1, 9, Material.COBWEB);
        site.lantern(-37, GROUND - 2, 7, true);
    }

    /**
     * The miners' tunnel from the cobbler's cellar to the quarry floor, under the lane and the brook: timbered every
     * four blocks, rails on the floor, a side niche with a chest.
     */
    private void mineTunnel() {
        int floor = CELLAR - 1;
        int z1 = 11;
        int z2 = 32;
        site.rough(-37, floor, z1 + 1, -34, floor + 4, z2, Material.STONE);
        site.air(-36, floor + 1, z1, -35, floor + 3, z2);
        site.air(-36, CELLAR + 1, z1, -35, CELLAR + 2, z1);
        site.set(-36, CELLAR, z1, Material.COBBLESTONE);
        site.set(-35, CELLAR, z1, Material.COBBLESTONE);
        site.set(-36, floor + 1, z1 + 1, Site.stairs(Material.COBBLESTONE_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
        site.set(-35, floor + 1, z1 + 1, Site.stairs(Material.COBBLESTONE_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
        for (int z = z1 + 2; z <= z2; z++) {
            site.set(-35, floor + 1, z, Material.RAIL);
            if (z % 4 == 0) {
                for (int x : new int[]{-37, -34}) {
                    site.fill(x, floor + 1, z, x, floor + 3, z, Material.SPRUCE_LOG);
                }
                site.fill(-36, floor + 4, z, -35, floor + 4, z, Site.log(Material.SPRUCE_LOG, Axis.X));
            }
            if (z % 8 == 2) {
                site.lantern(-36, floor + 3, z, true);
            }
        }
        site.air(-38, floor + 1, 26, -37, floor + 2, 26);
        site.chest(-38, floor + 1, 26, BlockFace.EAST);
        site.set(-36, floor + 3, 17, Material.COBWEB);
        site.set(-36, floor + 1, 30, Site.directional(Material.BARREL, BlockFace.UP));
    }

    /** A small cottage between the smithy and the cobbler, with a fenced chicken run. */
    private void cottage() {
        int x1 = -31;
        int x2 = -28;
        int z1 = 8;
        int z2 = 13;
        int top = site.shell(x1, z1, x2, z2, GROUND, 1, Material.COBBLESTONE, Material.OAK_LOG, Material.OAK_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.OAK_STAIRS, Material.OAK_SLAB, Material.OAK_PLANKS);
        site.door(-30, FLOOR + 1, z1, BlockFace.NORTH, Material.OAK_DOOR);
        site.bed(-29, FLOOR + 1, 12, BlockFace.NORTH, Material.LIGHT_BLUE_BED);
        site.chest(-30, FLOOR + 1, 12, BlockFace.EAST);
        site.set(-29, FLOOR + 1, 9, Site.directional(Material.FURNACE, BlockFace.WEST));
        site.lantern(-30, FLOOR + 3, 10, true);
        for (int x = -32; x <= -27; x++) {
            for (int z = 15; z <= 17; z++) {
                if (x == -32 || x == -27 || z == 17) {
                    site.fence(x, GROUND + 1, z, Material.OAK_FENCE);
                }
            }
        }
        site.set(-29, GROUND + 1, 15, Material.COMPOSTER);
        site.set(-30, GROUND + 1, 16, Material.HAY_BLOCK);
    }

    /** The old gate tower at the west end of the street, with a ladder to its battlements; its arch is barred. */
    private void gateTower() {
        int x1 = -46;
        int x2 = -43;
        int z1 = -8;
        int z2 = -4;
        int top = GROUND + 10;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                site.set(x, GROUND, z, Material.STONE_BRICKS);
                if (edge) {
                    site.rough(x, GROUND + 1, z, x, top, z, Material.STONE_BRICKS);
                    if (Math.floorMod(x + z, 2) == 0) {
                        site.rough(x, top + 1, z, x, top + 1, z, Material.STONE_BRICKS);
                    }
                } else {
                    site.air(x, GROUND + 1, z, x, top + 1, z);
                    site.set(x, top, z, Material.SPRUCE_PLANKS);
                }
            }
        }
        site.ladder(-45, GROUND + 1, top, -6, BlockFace.EAST);
        site.door(-44, FLOOR + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.air(-43, GROUND + 6, -6, -43, GROUND + 6, -6);
        site.chest(-44, top + 1, -7, BlockFace.SOUTH);
        site.torch(-44, GROUND + 3, -7, BlockFace.SOUTH);
        for (int x = x1; x <= x2; x++) {
            for (int z = 2; z <= 5; z++) {
                int height = GROUND + 5 + (x + z) % 2;
                site.rough(x, GROUND, z, x, height, z, Material.STONE_BRICKS);
            }
        }
        for (int z = -3; z <= 1; z++) {
            for (int x = x1; x <= x2; x++) {
                site.rough(x, GROUND + 6, z, x, GROUND + 7, z, Material.STONE_BRICKS);
            }
            for (int y = GROUND + 1; y <= GROUND + 5; y++) {
                site.bars(x1, y, z);
            }
        }
        for (int x = x1 + 1; x <= x2; x++) {
            site.set(x, GROUND + 5, -3, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.NORTH, Bisected.Half.TOP));
            site.set(x, GROUND + 5, 1, Site.stairs(Material.STONE_BRICK_STAIRS, BlockFace.SOUTH, Bisected.Half.TOP));
        }
        site.torch(-42, GROUND + 3, -4, BlockFace.EAST);
    }

    /**
     * The inn "Zum Goldenen Krug" east of the market: a taproom with a bar, tables and a fireplace, a kitchen at the
     * back with the soup kettle over the fire, guest rooms upstairs, an attic, and a cellar of kegs with a hatch for
     * the drayman and the smugglers' iron door.
     */
    private void inn() {
        int x1 = 14;
        int x2 = 28;
        int z1 = -13;
        int z2 = -1;
        site.vault(x1, CELLAR, z1, x2, GROUND - 1, z2, Material.STONE_BRICKS);
        int top = site.shell(x1, z1, x2, z2, GROUND, 2, Material.WHITE_TERRACOTTA, Material.DARK_OAK_LOG, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.DARK_OAK_STAIRS, Material.DARK_OAK_SLAB, Material.WHITE_TERRACOTTA);
        site.fill(x1, GROUND, z1, x2, GROUND, z2, Material.SPRUCE_PLANKS);
        site.door(x1, FLOOR + 1, -7, BlockFace.WEST, Material.DARK_OAK_DOOR);
        site.door(21, FLOOR + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.door(25, FLOOR + 1, z1, BlockFace.NORTH, Material.SPRUCE_DOOR);
        site.set(x1 - 1, GROUND, -7, Material.COBBLESTONE);
        site.torch(x1 - 1, FLOOR + 3, -4, BlockFace.WEST);
        site.torch(x1 - 1, FLOOR + 3, -10, BlockFace.WEST);
        site.shutter(x1 - 1, FLOOR + 3, -7, BlockFace.WEST, Material.DARK_OAK_TRAPDOOR);

        for (int x = 16; x <= 22; x++) {
            site.set(x, FLOOR + 1, -9, Site.stairs(Material.SPRUCE_STAIRS, BlockFace.NORTH, Bisected.Half.TOP));
            if (x % 2 == 0) {
                site.chair(x, FLOOR + 1, -8, BlockFace.NORTH, Material.SPRUCE_STAIRS);
            }
        }
        site.fill(16, FLOOR + 1, -12, 22, FLOOR + 1, -12, Site.directional(Material.BARREL, BlockFace.SOUTH));
        site.fill(16, FLOOR + 2, -12, 20, FLOOR + 2, -12, Site.directional(Material.BARREL, BlockFace.SOUTH));
        site.chest(22, FLOOR + 2, -12, BlockFace.SOUTH);
        site.set(15, FLOOR + 1, -12, Material.FLOWER_POT);
        for (int[] spot : new int[][]{{17, -4}, {21, -4}}) {
            site.table(spot[0], FLOOR + 1, spot[1]);
            site.chair(spot[0] - 1, FLOOR + 1, spot[1], BlockFace.EAST, Material.SPRUCE_STAIRS);
            site.chair(spot[0] + 1, FLOOR + 1, spot[1], BlockFace.WEST, Material.SPRUCE_STAIRS);
            site.lantern(spot[0], FLOOR + 3, spot[1], true);
        }
        site.fire(27, FLOOR + 1, -4);
        site.fill(27, FLOOR + 1, -5, 27, FLOOR + 3, -5, Material.BRICKS);
        site.fill(27, FLOOR + 1, -3, 27, FLOOR + 3, -3, Material.BRICKS);
        site.set(27, FLOOR + 3, -4, Material.BRICKS);
        site.chair(25, FLOOR + 1, -4, BlockFace.EAST, Material.SPRUCE_STAIRS);
        site.lantern(20, FLOOR + 3, -10, true);

        for (int z = z1 + 1; z <= -8; z++) {
            site.fill(24, FLOOR + 1, z, 24, FLOOR + 3, z, Material.SPRUCE_PLANKS);
        }
        site.door(24, FLOOR + 1, -10, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.fill(25, GROUND, -12, 27, GROUND, -8, Material.STONE_BRICKS);
        site.fire(26, GROUND, -11);
        kettle = site.set(26, FLOOR + 1, -11, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(3)));
        site.set(25, FLOOR + 1, -9, Site.directional(Material.BARREL, BlockFace.UP));
        site.table(27, FLOOR + 1, -9);
        site.lantern(26, FLOOR + 3, -9, true);
        site.ladder(27, CELLAR + 1, GROUND - 1, -12, BlockFace.WEST);
        site.hatch(27, GROUND, -12, BlockFace.WEST, Material.SPRUCE_TRAPDOOR);

        for (int step = 0; step < 4; step++) {
            int x = 16 + step;
            if (step > 0) {
                site.fill(x, FLOOR + 1, -2, x, FLOOR + step, -2, Material.SPRUCE_PLANKS);
            }
            site.set(x, FLOOR + 1 + step, -2, Site.stairs(Material.SPRUCE_STAIRS, BlockFace.EAST, Bisected.Half.BOTTOM));
            if (step < 3) {
                site.air(x, UPPER, -2, x, UPPER, -2);
                site.fence(x, UPPER + 1, -3, Material.SPRUCE_FENCE);
            }
        }
        upstairs();
        innCellar();
    }

    private void upstairs() {
        for (int x = 15; x <= 27; x++) {
            site.fill(x, UPPER + 1, -8, x, UPPER + 3, -8, Material.SPRUCE_PLANKS);
            site.fill(x, UPPER + 1, -6, x, UPPER + 3, -6, Material.SPRUCE_PLANKS);
        }
        for (int z = -12; z <= -9; z++) {
            site.fill(20, UPPER + 1, z, 20, UPPER + 3, z, Material.SPRUCE_PLANKS);
            site.fill(24, UPPER + 1, z, 24, UPPER + 3, z, Material.SPRUCE_PLANKS);
        }
        for (int z = -5; z <= -2; z++) {
            site.fill(21, UPPER + 1, z, 21, UPPER + 3, z, Material.SPRUCE_PLANKS);
        }
        for (int x : new int[]{17, 22, 26}) {
            site.door(x, UPPER + 1, -8, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        }
        site.door(18, UPPER + 1, -6, BlockFace.NORTH, Material.SPRUCE_DOOR);
        site.door(24, UPPER + 1, -6, BlockFace.NORTH, Material.SPRUCE_DOOR);
        site.bed(15, UPPER + 1, -11, BlockFace.NORTH, Material.RED_BED);
        site.set(19, UPPER + 1, -12, Site.directional(Material.BARREL, BlockFace.SOUTH));
        site.bed(21, UPPER + 1, -11, BlockFace.NORTH, Material.YELLOW_BED);
        site.bed(27, UPPER + 1, -11, BlockFace.NORTH, Material.GREEN_BED);
        site.set(25, UPPER + 1, -12, Site.directional(Material.BARREL, BlockFace.UP));
        site.bed(26, UPPER + 1, -3, BlockFace.SOUTH, Material.BLUE_BED);
        site.chest(22, UPPER + 1, -2, BlockFace.NORTH);
        site.chair(15, UPPER + 1, -5, BlockFace.EAST, Material.SPRUCE_STAIRS);
        site.table(16, UPPER + 1, -5);
        site.lantern(21, UPPER + 3, -7, true);
        site.lantern(17, UPPER + 3, -10, true);
        site.lantern(24, UPPER + 3, -4, true);

        int attic = UPPER + 6;
        site.ladder(27, UPPER + 1, attic - 1, -7, BlockFace.WEST);
        site.chest(16, attic, -7, BlockFace.EAST);
        site.set(16, attic, -6, Material.HAY_BLOCK);
        site.set(17, attic, -8, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(18, attic, -7, Material.COBWEB);
        site.lantern(21, attic + 2, -7, true);
    }

    private void innCellar() {
        for (int x = 15; x <= 27; x++) {
            for (int z = -12; z <= -2; z++) {
                site.set(x, CELLAR, z, site.random.nextInt(3) == 0 ? Material.COBBLESTONE : Material.STONE_BRICKS);
            }
        }
        for (int z = -11; z <= -3; z += 2) {
            site.set(15, CELLAR + 1, z, Site.directional(Material.BARREL, BlockFace.EAST));
            site.set(15, CELLAR + 2, z, Site.directional(Material.BARREL, BlockFace.EAST));
        }
        for (int x = 17; x <= 23; x++) {
            if (x != 20) {
                site.set(x, CELLAR + 1, -12, Site.directional(Material.BARREL, BlockFace.SOUTH));
                site.set(x, CELLAR + 2, -12, Site.directional(Material.BARREL, BlockFace.SOUTH));
            }
        }
        site.chest(16, CELLAR + 1, -2, BlockFace.NORTH);
        site.set(22, CELLAR + 1, -2, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(23, CELLAR + 1, -2, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(25, CELLAR + 1, -2, Material.COBWEB);
        site.lantern(20, GROUND - 2, -7, true);
        site.lantern(25, GROUND - 2, -5, true);
        site.traitorDoor(24, CELLAR + 1, -1, BlockFace.SOUTH);

        site.air(20, CELLAR + 1, -13, 20, CELLAR + 2, -13);
        site.rough(19, CELLAR, -15, 21, GROUND - 1, -14, Material.STONE_BRICKS);
        site.air(20, CELLAR + 1, -14, 20, GROUND - 1, -14);
        site.ladder(20, CELLAR + 1, GROUND - 1, -14, BlockFace.SOUTH);
        site.hatch(20, GROUND, -14, BlockFace.SOUTH, Material.SPRUCE_TRAPDOOR);
        site.set(19, GROUND + 1, -14, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(21, GROUND + 1, -14, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(21, GROUND + 2, -14, Site.directional(Material.BARREL, BlockFace.UP));
    }

    /**
     * The bakery south of the street: a brick oven with its chimney, the flour bin, a
     * counter of bread and a table for the baker.
     */
    private void bakery() {
        int x1 = 14;
        int x2 = 22;
        int z1 = 5;
        int z2 = 13;
        int top = site.shell(x1, z1, x2, z2, GROUND, 1, Material.BIRCH_PLANKS, Material.STRIPPED_DARK_OAK_LOG, Material.OAK_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.BRICK_STAIRS, Material.BRICK_SLAB, Material.BIRCH_PLANKS);
        site.door(18, FLOOR + 1, z1, BlockFace.NORTH, Material.BIRCH_DOOR);
        site.door(x1, FLOOR + 1, 10, BlockFace.WEST, Material.BIRCH_DOOR);
        site.set(18, GROUND, z1 - 1, Material.COBBLESTONE);
        site.lantern(17, FLOOR + 3, z1 - 1, false);
        site.set(17, FLOOR + 1, z1 - 1, Material.BIRCH_FENCE);
        site.set(17, FLOOR + 2, z1 - 1, Material.BIRCH_FENCE);

        site.fill(21, FLOOR + 1, 7, 21, FLOOR + 3, 10, Material.BRICKS);
        var oven = site.unlit(21, FLOOR + 1, 8, Material.SMOKER, BlockFace.WEST);
        site.chore(new Chore.Hold("oven", oven, true));
        site.set(21, FLOOR + 1, 9, Material.BRICKS);
        site.fill(21, top, 8, 21, top + 7, 8, Material.BRICKS);
        site.fire(21, top + 8, 8);
        site.set(15, FLOOR + 1, 12, Site.directional(Material.BARREL, BlockFace.UP));
        site.set(16, FLOOR + 1, 12, Material.WHITE_WOOL);
        site.set(15, FLOOR + 1, 11, Material.WHITE_WOOL);
        site.set(15, FLOOR + 2, 12, Material.WHITE_CARPET);
        for (int x = 15; x <= 19; x++) {
            if (x != 18) {
                site.set(x, FLOOR + 1, 7, Site.slab(Material.SPRUCE_SLAB, Slab.Type.TOP));
            }
        }
        site.table(18, FLOOR + 1, 10);
        site.chair(18, FLOOR + 1, 11, BlockFace.NORTH, Material.BIRCH_STAIRS);
        site.chest(20, FLOOR + 1, 12, BlockFace.NORTH);
        site.lantern(18, FLOOR + 3, 9, true);
    }

    /** The weaver's house in the east: looms and bolts of wool below, a bedroom and the stores above. */
    private void weaver() {
        int x1 = 34;
        int x2 = 44;
        int z1 = -13;
        int z2 = -5;
        int top = site.shell(x1, z1, x2, z2, GROUND, 2, Material.MUD_BRICKS, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB, Material.MUD_BRICKS);
        site.door(38, FLOOR + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.door(x1, FLOOR + 1, -9, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.set(38, GROUND, z2 + 1, Material.COBBLESTONE);
        site.torch(40, FLOOR + 3, z2 + 1, BlockFace.SOUTH);
        site.set(36, FLOOR + 1, -12, Site.directional(Material.LOOM, BlockFace.SOUTH));
        site.set(38, FLOOR + 1, -12, Site.directional(Material.LOOM, BlockFace.SOUTH));
        var wool = List.of(Material.RED_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL, Material.WHITE_WOOL, Material.GREEN_WOOL);
        for (int x = 40; x <= 42; x++) {
            for (int y = FLOOR + 1; y <= FLOOR + 1 + (x % 2 == 0 ? 1 : 0); y++) {
                site.set(x, y, -12, wool.get(site.random.nextInt(wool.size())));
            }
        }
        site.chest(35, FLOOR + 1, -6, BlockFace.EAST);
        site.table(40, FLOOR + 1, -8);
        site.chair(41, FLOOR + 1, -8, BlockFace.WEST, Material.SPRUCE_STAIRS);
        site.lantern(38, FLOOR + 3, -9, true);
        site.ladder(43, FLOOR + 1, UPPER, -12, BlockFace.WEST);
        site.bed(36, UPPER + 1, -11, BlockFace.NORTH, Material.WHITE_BED);
        site.fill(40, UPPER + 1, -12, 42, UPPER + 1, -11, Material.WHITE_WOOL);
        site.set(41, UPPER + 2, -12, Material.LIGHT_GRAY_WOOL);
        site.set(43, UPPER + 1, -6, Site.directional(Material.BARREL, BlockFace.UP));
        site.lantern(39, UPPER + 3, -9, true);
    }

    /** The square in the east with the village well, a lime tree and benches. */
    private void wellSquare() {
        for (int x = 31; x <= 42; x++) {
            for (int z = -3; z <= 2; z++) {
                if (site.block(x, GROUND, z).getType() == Material.GRASS_BLOCK || site.block(x, GROUND, z).getType() == Material.DIRT_PATH) {
                    site.set(x, GROUND, z, site.random.nextInt(3) == 0 ? Material.MOSSY_COBBLESTONE : Material.COBBLESTONE);
                }
            }
        }
        int cx = 36;
        int cz = -1;
        site.fill(cx, GROUND - 5, cz, cx, GROUND - 1, cz, Material.WATER);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    site.set(cx + dx, GROUND, cz + dz, Material.STONE_BRICKS);
                    site.wall(cx + dx, GROUND + 1, cz + dz, Material.MOSSY_COBBLESTONE_WALL);
                }
            }
        }
        var bucket = site.set(cx, GROUND, cz, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(3)));
        site.chore(new Chore.Hold("well", bucket, false));
        site.fences(cx - 1, GROUND + 2, cz, cx - 1, GROUND + 3, cz, Material.SPRUCE_FENCE);
        site.fences(cx + 1, GROUND + 2, cz, cx + 1, GROUND + 3, cz, Material.SPRUCE_FENCE);
        site.fill(cx - 1, GROUND + 4, cz - 1, cx + 1, GROUND + 4, cz + 1, Site.slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
        site.lantern(cx, GROUND + 3, cz, true);
        site.tree(41, GROUND, 1, Material.OAK_LOG);
        site.chair(33, GROUND + 1, 1, BlockFace.NORTH, Material.SPRUCE_STAIRS);
        site.chair(34, GROUND + 1, 1, BlockFace.NORTH, Material.SPRUCE_STAIRS);
        site.fences(32, GROUND + 1, -3, 32, GROUND + 3, -3, Material.DARK_OAK_FENCE);
        site.lantern(32, GROUND + 4, -3, false);
    }

    /** A fisher's cottage by the mill pond. */
    private void pondCottage() {
        int x1 = 38;
        int x2 = 45;
        int z1 = 3;
        int z2 = 8;
        int top = site.shell(x1, z1, x2, z2, GROUND, 1, Material.SPRUCE_PLANKS, Material.STRIPPED_OAK_LOG, Material.SPRUCE_PLANKS);
        site.roof(x1, z1, x2, z2, top + 1, Material.MOSSY_COBBLESTONE_STAIRS, Material.MOSSY_COBBLESTONE_SLAB, Material.SPRUCE_PLANKS);
        site.door(x1, FLOOR + 1, 5, BlockFace.WEST, Material.SPRUCE_DOOR);
        site.door(41, FLOOR + 1, z2, BlockFace.SOUTH, Material.SPRUCE_DOOR);
        site.bed(44, FLOOR + 1, 4, BlockFace.SOUTH, Material.CYAN_BED);
        site.chest(39, FLOOR + 1, 7, BlockFace.EAST);
        site.set(44, FLOOR + 1, 7, Site.directional(Material.BARREL, BlockFace.WEST));
        site.set(43, FLOOR + 1, 7, Material.CAULDRON);
        site.lantern(41, FLOOR + 3, 5, true);
        site.fences(42, GROUND + 1, 9, 42, GROUND + 1, 9, Material.SPRUCE_FENCE);
    }
}
