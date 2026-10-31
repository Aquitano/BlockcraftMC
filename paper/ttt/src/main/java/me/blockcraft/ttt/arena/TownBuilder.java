package me.blockcraft.ttt.arena;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bell;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Eichenhain, the village TTT builds into an empty world at every start. Two streets cross at a plaza with a
 * fountain where the round begins; around it stand nine houses with gable roofs and furnished rooms, an inn with an
 * upper floor, a smithy, a barn with a loft and a market with a well. Two cellars are joined by a tunnel under the
 * street. The traitor tester closes the north end of the main street, the chapel with the round's ender chest and
 * its graveyard the south end. A pond lies in the north-east, a forest and a hedge ring the village.
 *
 * <p>Blocks are set without physics, so fences, panes and bars get their connections from {@link #connect} once
 * everything stands, and the layout is the same at every start because the decoration draws from a fixed seed.
 */
public final class TownBuilder {

    public static final String NAME = "Eichenhain";

    private static final int GROUND = 64;
    private static final int EDGE = 44;
    private static final int CLEARED = 52;
    private static final int BEDROCK = GROUND - 9;
    private static final int LOBBY_Y = 150;
    private static final int SPAWN_RADIUS = 6;
    private static final int SPAWNS = 12;
    private static final long SEED = 0x7177L;
    private static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
    private static final List<Material> FLOWERS = List.of(Material.DANDELION, Material.POPPY, Material.AZURE_BLUET,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.RED_TULIP, Material.WHITE_TULIP, Material.ALLIUM);

    /** What a house is built of: walls, the beams at its corners and under its roof, floors, roof stairs and ridge. */
    private record Palette(Material wall, Material beam, Material floor, Material stairs, Material ridge) {
    }

    private static final Palette TIMBER = new Palette(Material.WHITE_TERRACOTTA, Material.DARK_OAK_LOG,
            Material.SPRUCE_PLANKS, Material.DARK_OAK_STAIRS, Material.DARK_OAK_SLAB);
    private static final Palette OAK = new Palette(Material.OAK_PLANKS, Material.STRIPPED_OAK_LOG,
            Material.SPRUCE_PLANKS, Material.SPRUCE_STAIRS, Material.SPRUCE_SLAB);
    private static final Palette BRICK = new Palette(Material.BRICKS, Material.STRIPPED_SPRUCE_LOG,
            Material.OAK_PLANKS, Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB);
    private static final Palette STONE = new Palette(Material.STONE_BRICKS, Material.SPRUCE_LOG,
            Material.DARK_OAK_PLANKS, Material.STONE_BRICK_STAIRS, Material.STONE_BRICK_SLAB);
    private static final Palette BIRCH = new Palette(Material.BIRCH_PLANKS, Material.STRIPPED_DARK_OAK_LOG,
            Material.OAK_PLANKS, Material.BRICK_STAIRS, Material.BRICK_SLAB);

    /**
     * A house between two corners: {@code door} is the wall the door is in, {@code floors} one or two, and
     * {@code cellar} a room below with a trapdoor and ladder at the back corner left of the door.
     */
    private record House(int x1, int z1, int x2, int z2, BlockFace door, Palette palette, int floors, boolean cellar) {

        int minX() {
            return Math.min(x1, x2);
        }

        int maxX() {
            return Math.max(x1, x2);
        }

        int minZ() {
            return Math.min(z1, z2);
        }

        int maxZ() {
            return Math.max(z1, z2);
        }
    }

    private final World world;
    private final Random random = new Random(SEED);
    private final Set<Block> chests = new HashSet<>();
    private final List<Block> connecting = new ArrayList<>();

    private TownBuilder(World world) {
        this.world = world;
    }

    public static Town build(World world) {
        return new TownBuilder(world).build();
    }

    private Town build() {
        clear();
        ground();
        streets();
        plaza();
        houses();
        inn();
        smithy();
        barn();
        market();
        pond();
        cellarTunnel();
        gardens();
        var enderChest = chapel();
        graveyard();
        var tester = tester();
        forest();
        decorate();
        var lobby = lobby();
        connect();
        return new Town(NAME, world, spawns(), Set.copyOf(chests), enderChest, tester, lobby,
                new Location(world, 0.5, GROUND + 18, 12.5, 180, 45));
    }

    /** The world keeps what the last round left, so everything the village may cover goes first. */
    private void clear() {
        for (int x = -CLEARED; x <= CLEARED; x++) {
            for (int z = -CLEARED; z <= CLEARED; z++) {
                for (int y = BEDROCK; y <= LOBBY_Y + 12; y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    /**
     * Grass over dirt and stone, deep enough for the cellars, with patches of coarse dirt and moss so it does not
     * read as a grid; a hedge closes the village and a ring of forest stands beyond the border so the void is not
     * the first thing anyone sees.
     */
    private void ground() {
        for (int x = -CLEARED; x <= CLEARED; x++) {
            for (int z = -CLEARED; z <= CLEARED; z++) {
                set(x, BEDROCK, z, Material.BEDROCK);
                fill(x, BEDROCK + 1, z, x, GROUND - 4, z, Material.STONE);
                fill(x, GROUND - 3, z, x, GROUND - 1, z, Material.DIRT);
                set(x, GROUND, z, patch(x, z));
            }
        }
        for (int x = -EDGE; x <= EDGE; x++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                if (Math.abs(x) == EDGE || Math.abs(z) == EDGE) {
                    fill(x, GROUND + 1, z, x, GROUND + 2, z, leaves(Material.OAK_LEAVES));
                }
            }
        }
        for (int d = -CLEARED + 2; d <= CLEARED - 2; d += 5) {
            for (int outside : new int[]{-(EDGE + 4), EDGE + 4}) {
                tree(d + random.nextInt(3) - 1, outside + random.nextInt(3) - 1, treeKind());
                tree(outside + random.nextInt(3) - 1, d + random.nextInt(3) - 1, treeKind());
            }
        }
    }

    private Material patch(int x, int z) {
        double noise = Math.sin(x * 0.37 + z * 0.11) + Math.cos(z * 0.29 - x * 0.17) + random.nextDouble() * 0.6;
        if (noise > 1.85) {
            return Material.COARSE_DIRT;
        }
        if (noise < -1.75) {
            return Material.MOSS_BLOCK;
        }
        return Material.GRASS_BLOCK;
    }

    private void streets() {
        for (int d = 9; d < EDGE; d++) {
            for (int w = -2; w <= 2; w++) {
                for (var at : new int[][]{{w, d}, {w, -d}, {d, w}, {-d, w}}) {
                    set(at[0], GROUND, at[1], streetBlock(Math.abs(w) == 2));
                }
            }
        }
        for (int d = 12; d < 40; d += 9) {
            for (int sign : new int[]{-1, 1}) {
                lampPost(3 * sign, d);
                lampPost(-3 * sign, -d);
                lampPost(d, 3 * sign);
                lampPost(-d, -3 * sign);
            }
        }
    }

    private Material streetBlock(boolean kerb) {
        if (kerb) {
            return random.nextInt(4) == 0 ? Material.COBBLESTONE : Material.GRAVEL;
        }
        return random.nextInt(6) == 0 ? Material.COARSE_DIRT : Material.DIRT_PATH;
    }

    /** Cobbles round a fountain, with benches, flower beds and four lamps; the round starts in a ring round the water. */
    private void plaza() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                boolean ring = Math.abs(x) == 8 || Math.abs(z) == 8;
                set(x, GROUND, z, ring ? Material.STONE_BRICKS
                        : switch (random.nextInt(5)) {
                            case 0 -> Material.ANDESITE;
                            case 1 -> Material.COBBLESTONE;
                            default -> Material.POLISHED_ANDESITE;
                        });
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(x, GROUND, z, Material.STONE_BRICKS);
                set(x, GROUND + 1, z, rim ? Material.STONE_BRICK_SLAB : Material.WATER);
            }
        }
        fill(0, GROUND + 1, 0, 0, GROUND + 3, 0, Material.CHISELED_STONE_BRICKS);
        set(0, GROUND + 4, 0, Material.WATER);
        for (int x : new int[]{-7, 7}) {
            for (int z : new int[]{-7, 7}) {
                lampPost(x, z);
                set(x - Integer.signum(x), GROUND, z, Material.GRASS_BLOCK);
                set(x - Integer.signum(x), GROUND + 1, z, flower());
                set(x, GROUND, z - Integer.signum(z), Material.GRASS_BLOCK);
                set(x, GROUND + 1, z - Integer.signum(z), flower());
            }
        }
        for (var side : SIDES) {
            for (int offset : new int[]{-5, 5}) {
                int x = side.getModX() * 7 + (side.getModX() == 0 ? offset : 0);
                int z = side.getModZ() * 7 + (side.getModZ() == 0 ? offset : 0);
                set(x, GROUND + 1, z, stairs(Material.SPRUCE_STAIRS, side, Bisected.Half.BOTTOM));
            }
        }
    }

    /** The eight houses of the quarters; two of them have cellars that a tunnel joins under the west street. */
    private void houses() {
        house(new House(6, -21, 13, -13, BlockFace.WEST, TIMBER, 2, false));
        house(new House(16, -13, 25, -6, BlockFace.SOUTH, BRICK, 1, false));
        house(new House(-25, -13, -16, -6, BlockFace.SOUTH, OAK, 1, true));
        house(new House(19, 6, 26, 13, BlockFace.NORTH, STONE, 1, false));
        house(new House(-13, 13, -6, 21, BlockFace.EAST, BIRCH, 2, false));
        house(new House(-26, 6, -16, 13, BlockFace.NORTH, STONE, 1, true));
        house(new House(-13, 26, -7, 33, BlockFace.EAST, OAK, 1, false));
        house(new House(19, 26, 26, 33, BlockFace.NORTH, TIMBER, 1, false));
    }

    private void house(House house) {
        int x1 = house.minX();
        int x2 = house.maxX();
        int z1 = house.minZ();
        int z2 = house.maxZ();
        int wallTop = GROUND + 4 * house.floors();
        shell(x1, z1, x2, z2, wallTop, house.palette());
        if (house.floors() == 2) {
            fill(x1 + 1, GROUND + 4, z1 + 1, x2 - 1, GROUND + 4, z2 - 1, house.palette().floor());
        }
        roof(x1, z1, x2, z2, wallTop + 1, house.palette());
        var door = house.door();
        var doorway = doorway(x1, z1, x2, z2, door);
        door(doorway[0], GROUND + 1, doorway[1], door, Material.SPRUCE_DOOR);
        var inward = door.getOppositeFace();
        var left = turnLeft(door);
        int backX = doorway[0] + inward.getModX() * (span(house, door) - 1);
        int backZ = doorway[1] + inward.getModZ() * (span(house, door) - 1);
        int halfWidth = width(house, door) / 2 - 1;
        int cornerX = backX + left.getModX() * halfWidth;
        int cornerZ = backZ + left.getModZ() * halfWidth;
        int otherX = backX - left.getModX() * halfWidth;
        int otherZ = backZ - left.getModZ() * halfWidth;
        if (house.floors() == 2) {
            for (int y = GROUND + 1; y <= GROUND + 4; y++) {
                set(cornerX, y, cornerZ, directional(Material.LADDER, door));
            }
            set(cornerX, GROUND + 4, cornerZ, directional(Material.LADDER, door));
            furnishUpstairs(otherX, otherZ, backX, backZ, inward, left, house);
        }
        furnish(doorway[0], doorway[1], inward, left, house, backX, backZ, otherX, otherZ);
        if (house.cellar()) {
            cellar(x1, z1, x2, z2, cornerX, cornerZ, door);
        }
    }

    /** The walls and ceiling: beams at the corners and along the top, windows of panes in every wall. */
    private void shell(int x1, int z1, int x2, int z2, int wallTop, Palette palette) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                set(x, GROUND, z, palette.floor());
                for (int y = GROUND + 1; y <= wallTop; y++) {
                    if (!wall) {
                        set(x, y, z, Material.AIR);
                    } else if (corner || y == wallTop || (y - GROUND) % 4 == 0) {
                        set(x, y, z, corner ? palette.beam().createBlockData() : beam(palette.beam(), x == x1 || x == x2));
                    } else if (window(x, y, z, x1, z1, x2, z2)) {
                        pane(x, y, z, Material.GLASS_PANE);
                    } else {
                        set(x, y, z, palette.wall());
                    }
                }
                set(x, wallTop + 1, z, wall ? palette.wall() : palette.floor());
            }
        }
    }

    private BlockData beam(Material log, boolean alongZ) {
        return log.createBlockData(data -> {
            if (data instanceof Orientable orientable) {
                orientable.setAxis(alongZ ? Axis.Z : Axis.X);
            }
        });
    }

    private static boolean window(int x, int y, int z, int x1, int z1, int x2, int z2) {
        int level = (y - GROUND) % 4;
        if (level != 2 && level != 3) {
            return false;
        }
        boolean xWall = z == z1 || z == z2;
        int along = xWall ? x - x1 : z - z1;
        int length = xWall ? x2 - x1 : z2 - z1;
        return along >= 2 && along <= length - 2 && along % 3 == 2;
    }

    /**
     * A gable roof over the longer side: stairs rise from both eaves to a ridge of slabs, the gable ends are wall
     * with a round window, and the eaves overhang the walls by one block.
     */
    private void roof(int x1, int z1, int x2, int z2, int baseY, Palette palette) {
        boolean ridgeAlongX = x2 - x1 >= z2 - z1;
        int low = ridgeAlongX ? z1 : x1;
        int high = ridgeAlongX ? z2 : x2;
        int from = ridgeAlongX ? x1 : z1;
        int to = ridgeAlongX ? x2 : z2;
        var rising = ridgeAlongX ? BlockFace.SOUTH : BlockFace.EAST;
        for (int step = 0; ; step++) {
            int near = low - 1 + step;
            int far = high + 1 - step;
            int y = baseY + step;
            if (near > far) {
                break;
            }
            for (int along = from - 1; along <= to + 1; along++) {
                if (near == far) {
                    placeRoof(ridgeAlongX, along, near, y, slab(palette.ridge(), Slab.Type.BOTTOM));
                } else {
                    placeRoof(ridgeAlongX, along, near, y, stairs(palette.stairs(), rising, Bisected.Half.BOTTOM));
                    placeRoof(ridgeAlongX, along, far, y, stairs(palette.stairs(), rising.getOppositeFace(), Bisected.Half.BOTTOM));
                }
            }
            for (int across = Math.max(near + 1, low); across <= Math.min(far - 1, high); across++) {
                for (int end : new int[]{from, to}) {
                    boolean middle = across == (low + high) / 2 && step == 2;
                    placeRoof(ridgeAlongX, end, across, y, middle ? Material.GLASS.createBlockData() : palette.wall().createBlockData());
                }
            }
        }
    }

    private void placeRoof(boolean ridgeAlongX, int along, int across, int y, BlockData data) {
        if (ridgeAlongX) {
            set(along, y, across, data);
        } else {
            set(across, y, along, data);
        }
    }

    /** The doorway in the middle of the wall that faces {@code side}, with a step of cobblestone outside. */
    private int[] doorway(int x1, int z1, int x2, int z2, BlockFace side) {
        int x = side.getModX() == 0 ? (x1 + x2) / 2 : side.getModX() > 0 ? x2 : x1;
        int z = side.getModZ() == 0 ? (z1 + z2) / 2 : side.getModZ() > 0 ? z2 : z1;
        set(x + side.getModX(), GROUND, z + side.getModZ(), Material.COBBLESTONE);
        lantern(x + side.getModX() + turnLeft(side).getModX(), GROUND + 3, z + side.getModZ() + turnLeft(side).getModZ(), false);
        return new int[]{x, z};
    }

    private static int span(House house, BlockFace door) {
        return door.getModX() != 0 ? house.maxX() - house.minX() : house.maxZ() - house.minZ();
    }

    private static int width(House house, BlockFace door) {
        return door.getModX() != 0 ? house.maxZ() - house.minZ() : house.maxX() - house.minX();
    }

    /**
     * The ground floor: a chest in the back corner, a table with chairs in the middle, a bed, a stove and a shelf
     * along the walls, a carpet by the door and a lamp from the ceiling.
     */
    private void furnish(int doorX, int doorZ, BlockFace inward, BlockFace left, House house,
                         int backX, int backZ, int otherX, int otherZ) {
        int floor = GROUND + 1;
        chest(otherX, floor, otherZ, inward.getOppositeFace());
        int midX = doorX + inward.getModX() * (span(house, inward) / 2);
        int midZ = doorZ + inward.getModZ() * (span(house, inward) / 2);
        table(midX, floor, midZ);
        set(midX + left.getModX(), floor, midZ + left.getModZ(), stairs(Material.OAK_STAIRS, left, Bisected.Half.BOTTOM));
        set(midX - left.getModX(), floor, midZ - left.getModZ(), stairs(Material.OAK_STAIRS, left.getOppositeFace(), Bisected.Half.BOTTOM));
        int bedX = doorX + inward.getModX() + left.getModX() * (width(house, inward) / 2 - 1);
        int bedZ = doorZ + inward.getModZ() + left.getModZ() * (width(house, inward) / 2 - 1);
        bed(bedX, floor, bedZ, inward, house.palette() == BRICK ? Material.BLUE_BED : Material.RED_BED);
        int stoveX = doorX + inward.getModX() - left.getModX() * (width(house, inward) / 2 - 1);
        int stoveZ = doorZ + inward.getModZ() - left.getModZ() * (width(house, inward) / 2 - 1);
        set(stoveX, floor, stoveZ, directional(Material.FURNACE, left));
        set(stoveX + inward.getModX(), floor, stoveZ + inward.getModZ(), directional(Material.BARREL, BlockFace.UP));
        set(stoveX + inward.getModX(), floor + 1, stoveZ + inward.getModZ(), Material.POTTED_RED_TULIP);
        if (house.floors() == 1) {
            set(backX, floor, backZ, Material.BOOKSHELF);
            set(backX, floor + 1, backZ, Material.BOOKSHELF);
        }
        set(doorX + inward.getModX(), floor, doorZ + inward.getModZ(), Material.BROWN_CARPET);
        lantern(midX, house.floors() == 2 ? GROUND + 3 : GROUND + 4, midZ, true);
    }

    /** Upstairs: a second chest, beds and a desk under the window. */
    private void furnishUpstairs(int otherX, int otherZ, int backX, int backZ, BlockFace inward, BlockFace left, House house) {
        int floor = GROUND + 5;
        chest(otherX, floor, otherZ, inward.getOppositeFace());
        set(backX, floor, backZ, Material.BOOKSHELF);
        bed(backX - inward.getModX() * 2 + left.getModX(), floor, backZ - inward.getModZ() * 2 + left.getModZ(),
                inward.getOppositeFace(), Material.WHITE_BED);
        set(backX - inward.getModX() * 2 - left.getModX(), floor, backZ - inward.getModZ() * 2 - left.getModZ(), Material.CRAFTING_TABLE);
        lantern(backX - inward.getModX() * 2, GROUND + 7, backZ - inward.getModZ() * 2, true);
    }

    /**
     * A cellar of the house's size below it, reached through a trapdoor and a ladder; it holds a chest, barrels
     * and a lamp, and the tunnel opens into it.
     */
    private void cellar(int x1, int z1, int x2, int z2, int ladderX, int ladderZ, BlockFace door) {
        int floor = GROUND - 4;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, floor - 1, z, Material.COBBLESTONE);
                for (int y = floor; y < GROUND; y++) {
                    set(x, y, z, wall ? (random.nextInt(3) == 0 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS) : Material.AIR);
                }
            }
        }
        for (int y = floor; y < GROUND; y++) {
            set(ladderX, y, ladderZ, directional(Material.LADDER, door));
        }
        set(ladderX, GROUND, ladderZ, Material.SPRUCE_TRAPDOOR.createBlockData(data -> {
            var trapdoor = (TrapDoor) data;
            trapdoor.setHalf(Bisected.Half.TOP);
            trapdoor.setFacing(door);
        }));
        int midX = (x1 + x2) / 2;
        int midZ = (z1 + z2) / 2;
        chest(x1 + 1, floor, z1 + 1, BlockFace.SOUTH);
        set(x2 - 1, floor, z1 + 1, directional(Material.BARREL, BlockFace.UP));
        set(x2 - 1, floor + 1, z1 + 1, directional(Material.BARREL, BlockFace.UP));
        set(x2 - 1, floor, z2 - 1, directional(Material.BARREL, BlockFace.UP));
        set(x1 + 1, floor, z2 - 1, Material.COBWEB);
        lantern(midX, GROUND - 1, midZ, true);
    }

    /** The passage between the two cellars on the west side, under the street, lit every few blocks. */
    private void cellarTunnel() {
        int x = -21;
        int floor = GROUND - 4;
        for (int z = -6; z <= 6; z++) {
            for (int dx = -1; dx <= 1; dx++) {
                set(x + dx, floor - 1, z, Material.COBBLESTONE);
                fill(x + dx, floor, z, x + dx, floor + 2, z, Material.AIR);
                set(x + dx, floor + 3, z, Material.STONE);
            }
            for (int side : new int[]{-2, 2}) {
                for (int y = floor - 1; y <= floor + 3; y++) {
                    set(x + side, y, z, z % 4 == 0 && y == floor + 2 ? Material.SPRUCE_LOG : Material.STONE);
                }
            }
            if (z % 4 == 0) {
                lantern(x, floor + 2, z, true);
                fill(x - 1, floor + 3, z, x + 1, floor + 3, z, Material.SPRUCE_PLANKS);
            }
        }
        set(x - 1, floor, 0, Material.COBWEB);
        set(x + 1, floor, -3, directional(Material.BARREL, BlockFace.UP));
        chest(x + 1, floor, 3, BlockFace.WEST);
    }

    /**
     * The inn east of the south street: two floors, a bar with stools, tables, kegs and a fireplace below, rooms
     * with beds above, reached by stairs.
     */
    private void inn() {
        int x1 = 6;
        int z1 = 12;
        int x2 = 17;
        int z2 = 22;
        int wallTop = GROUND + 8;
        shell(x1, z1, x2, z2, wallTop, TIMBER);
        fill(x1 + 1, GROUND + 4, z1 + 1, x2 - 1, GROUND + 4, z2 - 1, Material.SPRUCE_PLANKS);
        roof(x1, z1, x2, z2, wallTop + 1, TIMBER);
        var doorway = doorway(x1, z1, x2, z2, BlockFace.WEST);
        door(doorway[0], GROUND + 1, doorway[1], BlockFace.WEST, Material.DARK_OAK_DOOR);
        for (int z = z1 + 1; z <= z1 + 5; z++) {
            set(x2 - 3, GROUND + 1, z, Material.SPRUCE_PLANKS);
            set(x2 - 3, GROUND + 2, z, slab(Material.SPRUCE_SLAB, Slab.Type.BOTTOM));
            set(x2 - 4, GROUND + 1, z, stairs(Material.SPRUCE_STAIRS, BlockFace.WEST, Bisected.Half.BOTTOM));
        }
        set(x2 - 1, GROUND + 1, z1 + 1, directional(Material.BARREL, BlockFace.WEST));
        set(x2 - 1, GROUND + 2, z1 + 1, directional(Material.BARREL, BlockFace.WEST));
        set(x2 - 1, GROUND + 1, z1 + 2, directional(Material.BARREL, BlockFace.WEST));
        chest(x2 - 1, GROUND + 1, z1 + 4, BlockFace.WEST);
        for (int[] spot : new int[][]{{x1 + 3, z2 - 3}, {x1 + 6, z2 - 3}}) {
            table(spot[0], GROUND + 1, spot[1]);
            set(spot[0], GROUND + 1, spot[1] - 1, stairs(Material.SPRUCE_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
            set(spot[0], GROUND + 1, spot[1] + 1, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH, Bisected.Half.BOTTOM));
            lantern(spot[0], GROUND + 3, spot[1], true);
        }
        set(x2 - 1, GROUND + 1, z2 - 2, Material.CAMPFIRE.createBlockData(data -> ((Lightable) data).setLit(false)));
        fill(x2 - 1, GROUND + 2, z2 - 2, x2 - 1, GROUND + 3, z2 - 2, Material.BRICKS);
        for (int step = 0; step < 4; step++) {
            set(x1 + 1, GROUND + 1 + step, z1 + 1 + step, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH, Bisected.Half.BOTTOM));
            set(x1 + 1, GROUND + 4, z1 + 1 + step, Material.AIR);
        }
        int up = GROUND + 5;
        for (int z : new int[]{z1 + 2, z2 - 2}) {
            bed(x2 - 2, up, z, BlockFace.WEST, Material.GREEN_BED);
            bed(x2 - 5, up, z, BlockFace.WEST, Material.YELLOW_BED);
        }
        fill(x1 + 4, up, z1 + 5, x2 - 1, up + 2, z1 + 5, Material.SPRUCE_PLANKS);
        door(x1 + 6, up, z1 + 5, BlockFace.NORTH, Material.SPRUCE_DOOR);
        chest(x2 - 1, up, z1 + 4, BlockFace.WEST);
        chest(x1 + 4, up, z2 - 1, BlockFace.NORTH);
        lantern(x1 + 5, GROUND + 7, z2 - 4, true);
        lantern(x2 - 3, GROUND + 7, z1 + 2, true);
    }

    /** The smithy west of the north street: open to the road, with forge, anvil, grindstone and a chest of tools. */
    private void smithy() {
        int x1 = -14;
        int z1 = -21;
        int x2 = -6;
        int z2 = -14;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, GROUND, z, Material.STONE_BRICKS);
                boolean back = z == z1;
                boolean side = x == x1;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                for (int y = GROUND + 1; y <= GROUND + 4; y++) {
                    if (corner) {
                        set(x, y, z, Material.SPRUCE_LOG);
                    } else if (back || side) {
                        set(x, y, z, y == GROUND + 4 ? Material.SPRUCE_PLANKS : Material.COBBLESTONE);
                    }
                }
            }
        }
        roof(x1, z1, x2, z2, GROUND + 5, STONE);
        fill(x1 + 1, GROUND + 1, z1 + 1, x1 + 3, GROUND + 1, z1 + 2, Material.STONE_BRICKS);
        set(x1 + 2, GROUND + 1, z1 + 1, Material.LAVA_CAULDRON);
        set(x1 + 2, GROUND + 2, z1 + 1, Material.BLAST_FURNACE.createBlockData(data -> ((Directional) data).setFacing(BlockFace.SOUTH)));
        fill(x1 + 1, GROUND + 2, z1 + 1, x1 + 1, GROUND + 4, z1 + 1, Material.BRICKS);
        set(x1 + 5, GROUND + 1, z1 + 3, directional(Material.ANVIL, BlockFace.EAST));
        set(x1 + 3, GROUND + 1, z2 - 2, Material.GRINDSTONE.createBlockData(data -> {
            ((FaceAttachable) data).setAttachedFace(FaceAttachable.AttachedFace.FLOOR);
            ((Directional) data).setFacing(BlockFace.NORTH);
        }));
        set(x1 + 1, GROUND + 1, z2 - 1, Material.SMITHING_TABLE);
        set(x1 + 1, GROUND + 1, z2 - 2, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(3)));
        chest(x2 - 1, GROUND + 1, z1 + 1, BlockFace.SOUTH);
        lantern(x1 + 4, GROUND + 4, z1 + 3, true);
        for (int z = z1 + 2; z <= z2; z += 3) {
            set(x2 + 1, GROUND + 1, z, Material.HAY_BLOCK);
        }
    }

    /** The barn at the east end, with hay below, a loft above reached by a ladder, and a fenced paddock beside it. */
    private void barn() {
        int x1 = 30;
        int x2 = 40;
        for (int x = x1; x <= x2; x++) {
            for (int z = -6; z <= 6; z++) {
                boolean wall = x == x1 || x == x2 || Math.abs(z) == 6;
                boolean corner = (x == x1 || x == x2) && Math.abs(z) == 6;
                set(x, GROUND, z, Material.SPRUCE_PLANKS);
                for (int y = GROUND + 1; y <= GROUND + 6; y++) {
                    if (corner) {
                        set(x, y, z, Material.DARK_OAK_LOG);
                    } else if (wall) {
                        boolean gate = x == x1 && Math.abs(z) <= 1 && y <= GROUND + 3;
                        set(x, y, z, gate ? Material.AIR : y == GROUND + 6 ? Material.DARK_OAK_PLANKS : Material.RED_TERRACOTTA);
                    } else if (y == GROUND + 4 && x >= 35) {
                        set(x, y, z, Material.SPRUCE_PLANKS);
                    } else {
                        set(x, y, z, Material.AIR);
                    }
                }
            }
        }
        roof(x1, -6, x2, 6, GROUND + 7, STONE);
        for (int y = GROUND + 1; y <= GROUND + 4; y++) {
            set(35, y, 0, directional(Material.LADDER, BlockFace.WEST));
        }
        fill(36, GROUND + 1, 0, 36, GROUND + 3, 0, Material.SPRUCE_LOG);
        fill(37, GROUND + 1, -5, 39, GROUND + 2, -3, Material.HAY_BLOCK);
        fill(37, GROUND + 1, 3, 39, GROUND + 1, 5, Material.HAY_BLOCK);
        fill(31, GROUND + 1, 4, 32, GROUND + 1, 5, Material.HAY_BLOCK);
        set(31, GROUND + 1, -5, Material.COMPOSTER);
        chest(39, GROUND + 1, 0, BlockFace.WEST);
        chest(31, GROUND + 1, -4, BlockFace.EAST);
        chest(39, GROUND + 5, -5, BlockFace.WEST);
        fill(37, GROUND + 5, 3, 39, GROUND + 5, 5, Material.HAY_BLOCK);
        set(38, GROUND + 6, 4, Material.HAY_BLOCK);
        lantern(33, GROUND + 3, 0, true);
        for (int x = 30; x <= 40; x++) {
            for (int z = 9; z <= 17; z++) {
                if (x == 30 || x == 40 || z == 9 || z == 17) {
                    fence(x, GROUND + 1, z, Material.SPRUCE_FENCE);
                }
            }
        }
        set(30, GROUND + 1, 13, Material.AIR);
        set(36, GROUND + 1, 14, Material.WATER_CAULDRON.createBlockData(data -> ((Levelled) data).setLevel(3)));
        fill(33, GROUND + 1, 11, 34, GROUND + 1, 11, Material.HAY_BLOCK);
    }

    /** Four stalls under awnings at the west end and a well between them. */
    private void market() {
        var canopies = new Material[]{Material.RED_WOOL, Material.YELLOW_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL};
        var wares = new Material[]{Material.MELON, Material.PUMPKIN, Material.HAY_BLOCK, Material.BARREL};
        int index = 0;
        for (int x : new int[]{-38, -30}) {
            for (int z : new int[]{-7, 7}) {
                fill(x - 1, GROUND + 1, z, x + 1, GROUND + 1, z, Material.SPRUCE_PLANKS);
                set(x, GROUND + 2, z, wares[index]);
                for (int dx : new int[]{-2, 2}) {
                    for (int dz : new int[]{-1, 1}) {
                        fill(x + dx, GROUND + 1, z + dz, x + dx, GROUND + 3, z + dz, Material.SPRUCE_FENCE);
                    }
                }
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        set(x + dx, GROUND + 4, z + dz, Math.abs(dx) == 2 ? canopies[index] : dz == 0 ? Material.WHITE_WOOL : canopies[index]);
                    }
                }
                index++;
            }
        }
        chest(-38, GROUND + 1, 8, BlockFace.SOUTH);
        chest(-30, GROUND + 1, -8, BlockFace.NORTH);
        chest(-38, GROUND + 1, -8, BlockFace.NORTH);
        chest(-30, GROUND + 1, 8, BlockFace.SOUTH);
        well(-34, 0);
        tree(-41, -13, Material.OAK_LOG);
        tree(-41, 13, Material.BIRCH_LOG);
    }

    /** A stone ring around water with posts and a little roof. */
    private void well(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean rim = dx != 0 || dz != 0;
                set(cx + dx, GROUND, cz + dz, Material.COBBLESTONE);
                set(cx + dx, GROUND + 1, cz + dz, rim ? Material.COBBLESTONE_WALL : Material.WATER);
            }
        }
        fill(cx, GROUND - 3, cz, cx, GROUND, cz, Material.WATER);
        for (int dx : new int[]{-1, 1}) {
            fill(cx + dx, GROUND + 2, cz, cx + dx, GROUND + 3, cz, Material.SPRUCE_FENCE);
        }
        fill(cx - 1, GROUND + 4, cz - 1, cx + 1, GROUND + 4, cz + 1, Material.SPRUCE_SLAB);
    }

    /** The pond in the north-east: reeds and lilies, a jetty with a chest at its end and a footbridge. */
    private void pond() {
        int cx = 30;
        int cz = -31;
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double distance = Math.pow(dx / 7.0, 2) + Math.pow(dz / 5.0, 2);
                if (distance > 1.15) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                if (distance > 0.85) {
                    set(x, GROUND, z, random.nextBoolean() ? Material.SAND : Material.GRAVEL);
                    if (random.nextInt(3) == 0) {
                        fill(x, GROUND + 1, z, x, GROUND + 1 + random.nextInt(2), z, Material.SUGAR_CANE);
                    }
                    continue;
                }
                int depth = distance < 0.4 ? 3 : distance < 0.7 ? 2 : 1;
                set(x, GROUND - depth, z, Material.CLAY);
                fill(x, GROUND - depth + 1, z, x, GROUND, z, Material.WATER);
                if (random.nextInt(9) == 0) {
                    set(x, GROUND + 1, z, Material.LILY_PAD);
                }
            }
        }
        for (int z = cz + 6; z >= cz; z--) {
            set(cx, GROUND, z, Material.SPRUCE_PLANKS);
            set(cx, GROUND + 1, z, Material.AIR);
        }
        fence(cx - 1, GROUND + 1, cz, Material.SPRUCE_FENCE);
        fence(cx + 1, GROUND + 1, cz, Material.SPRUCE_FENCE);
        chest(cx, GROUND + 1, cz - 1, BlockFace.SOUTH);
        for (int x = cx - 8; x <= cx + 8; x++) {
            set(x, GROUND, cz + 2, Material.SPRUCE_PLANKS);
            set(x, GROUND + 1, cz + 2, Material.AIR);
            if (Math.abs(x - cx) > 1) {
                fence(x, GROUND + 1, cz + 1, Material.SPRUCE_FENCE);
                fence(x, GROUND + 1, cz + 3, Material.SPRUCE_FENCE);
            }
        }
    }

    /** Vegetable plots behind the houses, fenced, with a scarecrow made of a pumpkin on a fence. */
    private void gardens() {
        for (int[] plot : new int[][]{{16, -24, 22, -18}, {-26, -24, -18, -18}, {-26, 20, -19, 26}, {28, 22, 36, 28}}) {
            for (int x = plot[0]; x <= plot[2]; x++) {
                for (int z = plot[1]; z <= plot[3]; z++) {
                    boolean edge = x == plot[0] || x == plot[2] || z == plot[1] || z == plot[3];
                    if (edge) {
                        fence(x, GROUND + 1, z, Material.OAK_FENCE);
                    } else if ((z - plot[1]) % 3 == 0) {
                        set(x, GROUND, z, Material.WATER);
                    } else {
                        set(x, GROUND, z, Material.FARMLAND);
                        var crop = (x + z) % 3 == 0 ? Material.CARROTS : Material.WHEAT;
                        set(x, GROUND + 1, z, crop.createBlockData(data -> ((Ageable) data).setAge(((Ageable) data).getMaximumAge())));
                    }
                }
            }
            set((plot[0] + plot[2]) / 2, GROUND + 1, plot[1], Material.OAK_FENCE_GATE.createBlockData(data -> ((Directional) data).setFacing(BlockFace.NORTH)));
            int sx = (plot[0] + plot[2]) / 2;
            int sz = (plot[1] + plot[3]) / 2;
            set(sx, GROUND, sz, Material.DIRT);
            fence(sx, GROUND + 1, sz, Material.OAK_FENCE);
            set(sx, GROUND + 2, sz, Material.HAY_BLOCK);
            set(sx, GROUND + 3, sz, directional(Material.CARVED_PUMPKIN, BlockFace.SOUTH));
            set(plot[2] - 1, GROUND, plot[3] - 1, Material.DIRT);
            chest(plot[2] - 1, GROUND + 1, plot[3] - 1, BlockFace.NORTH);
        }
    }

    /** The chapel at the south end of the street; its altar is the round's only ender chest. */
    private Block chapel() {
        int x1 = -5;
        int x2 = 5;
        int z1 = 30;
        int z2 = 40;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                set(x, GROUND, z, (x + z) % 2 == 0 ? Material.POLISHED_DIORITE : Material.SMOOTH_STONE);
                for (int y = GROUND + 1; y <= GROUND + 7; y++) {
                    if (!wall) {
                        set(x, y, z, Material.AIR);
                    } else if (corner) {
                        set(x, y, z, Material.QUARTZ_PILLAR);
                    } else {
                        boolean doorway = z == z1 && Math.abs(x) <= 1 && y <= GROUND + 3;
                        boolean window = (x == x1 || x == x2) && y >= GROUND + 3 && y <= GROUND + 5 && z % 3 == 0;
                        if (doorway) {
                            set(x, y, z, Material.AIR);
                        } else if (window) {
                            pane(x, y, z, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
                        } else {
                            set(x, y, z, Material.QUARTZ_BRICKS);
                        }
                    }
                }
            }
        }
        roof(x1, z1, x2, z2, GROUND + 8, new Palette(Material.QUARTZ_BRICKS, Material.QUARTZ_PILLAR, Material.SMOOTH_STONE,
                Material.DEEPSLATE_TILE_STAIRS, Material.DEEPSLATE_TILE_SLAB));
        for (int y = GROUND + 8; y <= GROUND + 15; y++) {
            set(0, y, z1 + 1, Material.QUARTZ_PILLAR);
        }
        set(0, GROUND + 16, z1 + 1, Material.BELL.createBlockData(data -> {
            var bell = (Bell) data;
            bell.setAttachment(Bell.Attachment.FLOOR);
            bell.setFacing(BlockFace.SOUTH);
        }));
        for (int z = z1 + 3; z <= z2 - 4; z += 2) {
            for (int x : new int[]{-3, -2, 2, 3}) {
                set(x, GROUND + 1, z, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH, Bisected.Half.BOTTOM));
            }
        }
        fill(-1, GROUND + 1, z2 - 1, 1, GROUND + 1, z2 - 1, Material.QUARTZ_BLOCK);
        set(-1, GROUND + 2, z2 - 1, Material.CANDLE.createBlockData(data -> ((Candle) data).setCandles(3)));
        set(1, GROUND + 2, z2 - 1, Material.CANDLE.createBlockData(data -> ((Candle) data).setCandles(3)));
        fill(0, GROUND + 1, z1 + 1, 0, GROUND + 1, z2 - 2, Material.RED_CARPET);
        lantern(0, GROUND + 7, z1 + 5, true);
        return set(0, GROUND + 2, z2 - 1, directional(Material.ENDER_CHEST, BlockFace.NORTH));
    }

    /** The graveyard beside the chapel: rows of stones, an open grave with something in it, and a fence round it all. */
    private void graveyard() {
        int x1 = 8;
        int x2 = 16;
        int z1 = 30;
        int z2 = 40;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(x, GROUND, z, random.nextInt(3) == 0 ? Material.PODZOL : Material.GRASS_BLOCK);
                if (x == x1 || x == x2 || z == z1 || z == z2) {
                    fence(x, GROUND + 1, z, Material.DARK_OAK_FENCE);
                }
            }
        }
        set(x1, GROUND + 1, z1 + 2, Material.AIR);
        for (int x = x1 + 2; x <= x2 - 2; x += 3) {
            for (int z = z1 + 2; z <= z2 - 3; z += 3) {
                if (x == x1 + 5 && z == z1 + 5) {
                    fill(x, GROUND - 2, z, x, GROUND, z + 1, Material.AIR);
                    set(x, GROUND - 3, z, Material.COARSE_DIRT);
                    set(x, GROUND - 3, z + 1, Material.COARSE_DIRT);
                    chest(x, GROUND - 2, z, BlockFace.SOUTH);
                    set(x + 1, GROUND + 1, z, Material.COARSE_DIRT);
                    continue;
                }
                set(x, GROUND + 1, z, random.nextBoolean() ? Material.MOSSY_COBBLESTONE_WALL : Material.COBBLESTONE_WALL);
                set(x, GROUND, z + 1, Material.COARSE_DIRT);
                if (random.nextInt(3) == 0) {
                    set(x, GROUND + 1, z + 1, random.nextBoolean() ? Material.DEAD_BUSH : Material.WITHER_ROSE);
                }
            }
        }
        tree(x2 + 3, z2 - 1, Material.DARK_OAK_LOG);
    }

    /**
     * The tester at the north end of the street: a building of polished stone with iron trim and one doorway. The
     * lamps over the doorway and on the back wall show everyone outside and in what the test found.
     */
    private TesterRoom tester() {
        int x1 = -4;
        int x2 = 4;
        int z1 = -38;
        int z2 = -30;
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 2; z++) {
                set(x, GROUND, z, Material.POLISHED_ANDESITE);
            }
        }
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = Math.abs(x) == x2 || z == z1 || z == z2;
                boolean corner = Math.abs(x) == x2 && (z == z1 || z == z2);
                set(x, GROUND, z, (x + z) % 2 == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE);
                for (int y = GROUND + 1; y <= GROUND + 5; y++) {
                    set(x, y, z, !wall ? Material.AIR : corner ? Material.IRON_BLOCK
                            : y == GROUND + 5 ? Material.POLISHED_DEEPSLATE : Material.SMOOTH_STONE);
                }
                set(x, GROUND + 6, z, wall ? Material.POLISHED_DEEPSLATE_SLAB : Material.SMOOTH_STONE_SLAB);
            }
        }
        for (int z = z1 + 2; z <= z2 - 2; z += 2) {
            for (int x : new int[]{x1, x2}) {
                bars(x, GROUND + 3, z);
            }
        }
        var entrance = List.of(set(0, GROUND + 1, z2, Material.AIR), set(0, GROUND + 2, z2, Material.AIR));
        var lamps = new ArrayList<Block>();
        for (int x = -1; x <= 1; x++) {
            lamps.add(set(x, GROUND + 4, z2, Material.REDSTONE_LAMP));
        }
        for (int x : new int[]{-2, 0, 2}) {
            lamps.add(set(x, GROUND + 4, z1, Material.REDSTONE_LAMP));
        }
        var button = set(0, GROUND + 2, z1 + 1, Material.STONE_BUTTON.createBlockData(data -> {
            ((FaceAttachable) data).setAttachedFace(FaceAttachable.AttachedFace.WALL);
            ((Directional) data).setFacing(BlockFace.SOUTH);
        }));
        lantern(0, GROUND + 5, (z1 + z2) / 2, true);
        lampPost(-3, z2 + 2);
        lampPost(3, z2 + 2);
        return new TesterRoom(Box.of(x1 + 1, GROUND + 1, z1 + 1, x2 - 1, GROUND + 4, z2 - 1), button, entrance, List.copyOf(lamps));
    }

    /** Trees fill the space between the village and the hedge wherever there is grass to stand on. */
    private void forest() {
        for (int attempt = 0; attempt < 400; attempt++) {
            int x = random.nextInt(2 * EDGE - 5) - EDGE + 3;
            int z = random.nextInt(2 * EDGE - 5) - EDGE + 3;
            boolean band = Math.max(Math.abs(x), Math.abs(z)) >= 34;
            boolean open = Math.max(Math.abs(x), Math.abs(z)) >= 27 && random.nextInt(4) == 0;
            if ((band || open) && clearAround(x, z, 2)) {
                tree(x, z, treeKind());
            }
        }
        for (int attempt = 0; attempt < 160; attempt++) {
            int x = random.nextInt(2 * EDGE - 3) - EDGE + 2;
            int z = random.nextInt(2 * EDGE - 3) - EDGE + 2;
            if (clearAround(x, z, 0)) {
                set(x, GROUND + 1, z, leaves(random.nextBoolean() ? Material.OAK_LEAVES : Material.AZALEA_LEAVES));
                if (random.nextBoolean() && clearAround(x + 1, z, 0)) {
                    set(x + 1, GROUND + 1, z, leaves(Material.OAK_LEAVES));
                }
            }
        }
    }

    private Material treeKind() {
        return switch (random.nextInt(5)) {
            case 0, 1 -> Material.OAK_LOG;
            case 2 -> Material.BIRCH_LOG;
            case 3 -> Material.SPRUCE_LOG;
            default -> Material.DARK_OAK_LOG;
        };
    }

    /** True if the grass at x/z and around it in {@code radius} carries nothing yet. */
    private boolean clearAround(int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                var ground = world.getBlockAt(x + dx, GROUND, z + dz).getType();
                if (ground != Material.GRASS_BLOCK && ground != Material.MOSS_BLOCK && ground != Material.COARSE_DIRT) {
                    return false;
                }
                for (int y = GROUND + 1; y <= GROUND + 3; y++) {
                    if (!world.getBlockAt(x + dx, y, z + dz).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Grass, flowers and leaf litter on the open ground, so that the lawns are not bare. */
    private void decorate() {
        for (int x = -EDGE + 1; x < EDGE; x++) {
            for (int z = -EDGE + 1; z < EDGE; z++) {
                var ground = world.getBlockAt(x, GROUND, z).getType();
                if ((ground != Material.GRASS_BLOCK && ground != Material.MOSS_BLOCK) || !world.getBlockAt(x, GROUND + 1, z).isEmpty()) {
                    continue;
                }
                int roll = random.nextInt(100);
                if (roll < 14) {
                    set(x, GROUND + 1, z, Material.SHORT_GRASS);
                } else if (roll < 17) {
                    set(x, GROUND + 1, z, flower());
                } else if (roll < 19 && world.getBlockAt(x, GROUND + 2, z).isEmpty()) {
                    set(x, GROUND + 1, z, half(Material.TALL_GRASS, Bisected.Half.BOTTOM));
                    set(x, GROUND + 2, z, half(Material.TALL_GRASS, Bisected.Half.TOP));
                } else if (roll < 21) {
                    set(x, GROUND + 1, z, Material.LEAF_LITTER);
                }
            }
        }
    }

    private Material flower() {
        return FLOWERS.get(random.nextInt(FLOWERS.size()));
    }

    /**
     * The waiting hall high above the village: a timbered room with a long table and benches, open on all sides
     * behind a railing so the village shows below.
     */
    private Location lobby() {
        int half = 8;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                boolean corner = Math.abs(x) == half && Math.abs(z) == half;
                set(x, LOBBY_Y - 1, z, Material.STONE_BRICKS);
                set(x, LOBBY_Y, z, edge ? Material.STRIPPED_DARK_OAK_LOG : (x + z) % 2 == 0 ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS);
                if (corner) {
                    fill(x, LOBBY_Y + 1, z, x, LOBBY_Y + 5, z, Material.DARK_OAK_LOG);
                } else if (edge) {
                    fence(x, LOBBY_Y + 1, z, Material.DARK_OAK_FENCE);
                    set(x, LOBBY_Y + 2, z, Material.AIR);
                    if (Math.abs(x) == half && z % 4 == 0 || Math.abs(z) == half && x % 4 == 0) {
                        lantern(x, LOBBY_Y + 2, z, false);
                    }
                }
                set(x, LOBBY_Y + 5, z, edge ? Material.DARK_OAK_PLANKS : Material.AIR);
            }
        }
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z += 2 * half) {
                fill(x, LOBBY_Y + 2, z, x, LOBBY_Y + 4, z, Material.AIR);
            }
        }
        for (int x = -half; x <= half; x += 2 * half) {
            for (int z = -half + 1; z < half; z++) {
                fill(x, LOBBY_Y + 2, z, x, LOBBY_Y + 4, z, Material.AIR);
            }
        }
        for (int x = -half; x <= half; x += 4) {
            for (int z = -half; z <= half; z += 2 * half) {
                fill(x, LOBBY_Y + 1, z, x, LOBBY_Y + 5, z, Material.DARK_OAK_LOG);
            }
        }
        roof(-half, -half, half, half, LOBBY_Y + 6, TIMBER);
        for (int x = -4; x <= 4; x++) {
            table(x, LOBBY_Y + 1, 0);
            set(x, LOBBY_Y + 1, -1, stairs(Material.SPRUCE_STAIRS, BlockFace.NORTH, Bisected.Half.BOTTOM));
            set(x, LOBBY_Y + 1, 1, stairs(Material.SPRUCE_STAIRS, BlockFace.SOUTH, Bisected.Half.BOTTOM));
        }
        set(0, LOBBY_Y + 2, 0, Material.CANDLE.createBlockData(data -> ((Candle) data).setCandles(4)));
        set(-3, LOBBY_Y + 2, 0, Material.SKELETON_SKULL);
        set(3, LOBBY_Y + 2, 0, Material.POTTED_WITHER_ROSE);
        for (int x : new int[]{-5, 0, 5}) {
            lantern(x, LOBBY_Y + 4, 0, true);
        }
        fill(-half + 1, LOBBY_Y + 1, half - 1, -half + 2, LOBBY_Y + 1, half - 1, Material.BOOKSHELF);
        set(half - 1, LOBBY_Y + 1, half - 1, directional(Material.BARREL, BlockFace.UP));
        set(half - 1, LOBBY_Y + 1, -half + 1, directional(Material.BARREL, BlockFace.UP));
        return new Location(world, 0.5, LOBBY_Y + 1, -5.5, 0, 10);
    }

    /** Where the round starts: a ring around the fountain, everyone facing it. */
    private List<Location> spawns() {
        var spawns = new ArrayList<Location>();
        for (int index = 0; index < SPAWNS; index++) {
            double angle = 2 * Math.PI * index / SPAWNS;
            double x = Math.sin(angle) * SPAWN_RADIUS + 0.5;
            double z = Math.cos(angle) * SPAWN_RADIUS + 0.5;
            float yaw = (float) Math.toDegrees(Math.atan2(x - 0.5, -(z - 0.5)));
            spawns.add(new Location(world, x, GROUND + 1, z, yaw, 0));
        }
        return List.copyOf(spawns);
    }

    private void tree(int x, int z, Material log) {
        var leaves = switch (log) {
            case BIRCH_LOG -> Material.BIRCH_LEAVES;
            case SPRUCE_LOG -> Material.SPRUCE_LEAVES;
            case DARK_OAK_LOG -> Material.DARK_OAK_LEAVES;
            default -> Material.OAK_LEAVES;
        };
        if (log == Material.SPRUCE_LOG) {
            int height = 7 + random.nextInt(3);
            for (int y = 2; y <= height; y++) {
                int reach = y == height ? 0 : (height - y) % 2 == 0 ? 1 : 2;
                reach = Math.min(reach, (height - y + 2) / 2);
                ring(x, GROUND + y, z, reach, leaves);
            }
            set(x, GROUND + height + 1, z, leaves(leaves));
            fill(x, GROUND + 1, z, x, GROUND + height - 1, z, log);
            return;
        }
        int trunk = log == Material.BIRCH_LOG ? 5 + random.nextInt(2) : 4 + random.nextInt(2);
        for (int y = trunk - 1; y <= trunk + 2; y++) {
            int reach = y >= trunk + 1 ? 1 : 2;
            ring(x, GROUND + y, z, reach, leaves);
        }
        fill(x, GROUND + 1, z, x, GROUND + trunk, z, log);
    }

    private void ring(int x, int y, int z, int reach, Material leaves) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                boolean corner = Math.abs(dx) == reach && Math.abs(dz) == reach && reach > 0;
                if ((!corner || random.nextInt(3) == 0) && world.getBlockAt(x + dx, y, z + dz).isEmpty()) {
                    set(x + dx, y, z + dz, leaves(leaves));
                }
            }
        }
    }

    private void lampPost(int x, int z) {
        fill(x, GROUND + 1, z, x, GROUND + 3, z, Material.SPRUCE_FENCE);
        set(x, GROUND + 4, z, Material.LANTERN);
    }

    private void table(int x, int y, int z) {
        set(x, y, z, Material.SPRUCE_FENCE);
        set(x, y + 1, z, Material.SPRUCE_PRESSURE_PLATE);
    }

    private void lantern(int x, int y, int z, boolean hanging) {
        set(x, y, z, Material.LANTERN.createBlockData(data -> ((Lantern) data).setHanging(hanging)));
    }

    private void bed(int x, int y, int z, BlockFace facing, Material material) {
        for (var part : Bed.Part.values()) {
            int dx = part == Bed.Part.HEAD ? facing.getModX() : 0;
            int dz = part == Bed.Part.HEAD ? facing.getModZ() : 0;
            set(x + dx, y, z + dz, material.createBlockData(data -> {
                var bed = (Bed) data;
                bed.setPart(part);
                bed.setFacing(facing);
            }));
        }
    }

    /** A door in the wall that faces {@code facing}; the doorway reaches two blocks up. */
    private void door(int x, int y, int z, BlockFace facing, Material material) {
        for (var half : Bisected.Half.values()) {
            set(x, half == Bisected.Half.BOTTOM ? y : y + 1, z, material.createBlockData(data -> {
                var door = (Door) data;
                door.setFacing(facing.getOppositeFace());
                door.setHalf(half);
            }));
        }
    }

    private void chest(int x, int y, int z, BlockFace facing) {
        chests.add(set(x, y, z, directional(Material.CHEST, facing)));
    }

    private void fence(int x, int y, int z, Material material) {
        set(x, y, z, material);
        connecting.add(world.getBlockAt(x, y, z));
    }

    private void pane(int x, int y, int z, Material material) {
        set(x, y, z, material);
        connecting.add(world.getBlockAt(x, y, z));
    }

    private void bars(int x, int y, int z) {
        fill(x, y, z, x, y + 1, z, Material.IRON_BARS);
        connecting.add(world.getBlockAt(x, y, z));
        connecting.add(world.getBlockAt(x, y + 1, z));
    }

    /** Fences, panes and bars join whatever solid or alike stands beside them, as they would when placed by hand. */
    private void connect() {
        for (var block : connecting) {
            if (!(block.getBlockData() instanceof MultipleFacing facing)) {
                continue;
            }
            for (var side : SIDES) {
                if (facing.getAllowedFaces().contains(side)) {
                    facing.setFace(side, joins(block, block.getRelative(side)));
                }
            }
            block.setBlockData(facing, false);
        }
    }

    private static boolean joins(Block block, Block neighbour) {
        var type = neighbour.getType();
        if (type == block.getType()) {
            return true;
        }
        boolean fence = Tag.FENCES.isTagged(block.getType());
        if (fence) {
            return Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || neighbour.getType().isOccluding();
        }
        return Tag.WALLS.isTagged(type) || type == Material.IRON_BARS || type.name().endsWith("GLASS_PANE")
                || neighbour.getType().isOccluding();
    }

    private static BlockFace turnLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        fill(x1, y1, z1, x2, y2, z2, material.createBlockData());
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData data) {
        var box = Box.of(x1, y1, z1, x2, y2, z2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    set(x, y, z, data);
                }
            }
        }
    }

    private Block set(int x, int y, int z, Material material) {
        var block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        return block;
    }

    private Block set(int x, int y, int z, BlockData data) {
        var block = world.getBlockAt(x, y, z);
        block.setBlockData(data, false);
        return block;
    }

    private static BlockData leaves(Material material) {
        return material.createBlockData(data -> ((Leaves) data).setPersistent(true));
    }

    private static BlockData directional(Material material, BlockFace facing) {
        return material.createBlockData(data -> ((Directional) data).setFacing(facing));
    }

    private static BlockData stairs(Material material, BlockFace facing, Bisected.Half half) {
        return material.createBlockData(data -> {
            var stairs = (Stairs) data;
            stairs.setFacing(facing);
            stairs.setHalf(half);
        });
    }

    private static BlockData slab(Material material, Slab.Type type) {
        return material.createBlockData(data -> ((Slab) data).setType(type));
    }

    private static BlockData half(Material material, Bisected.Half half) {
        return material.createBlockData(data -> ((Bisected) data).setHalf(half));
    }
}
