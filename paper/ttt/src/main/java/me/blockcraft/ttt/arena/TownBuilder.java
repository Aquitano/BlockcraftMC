package me.blockcraft.ttt.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Eichenhain, the town TTT builds into an empty world at every start: a plaza with a fountain where the round
 * begins, two streets crossing it, two houses in every quarter (some with an upper floor or a roof to climb), sheds,
 * gardens and trees between them, a barn at the east end and a market at the west end. The traitor tester closes
 * the north end of the street, the chapel with the ender chest the south end. A hedge marks the edge of the town.
 */
public final class TownBuilder {

    public static final String NAME = "Eichenhain";

    private static final int GROUND = 64;
    private static final int EDGE = 44;
    private static final int CLEARED = 46;
    private static final int LOBBY_Y = 100;
    private static final int SPAWN_RADIUS = 6;

    private record House(int x1, int z1, int x2, int z2, BlockFace door, Material wall, Material corner, Material floor,
                         Material roof, boolean upperFloor) {
    }

    private final World world;
    private final Set<Block> chests = new HashSet<>();

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
        for (int sx : new int[]{-1, 1}) {
            for (int sz : new int[]{-1, 1}) {
                quarter(sx, sz);
            }
        }
        barn();
        market();
        var enderChest = chapel();
        var tester = tester();
        return new Town(NAME, world, spawns(), Set.copyOf(chests), enderChest, tester, lobby(),
                new Location(world, 0.5, GROUND + 16, 0.5, 0, 50));
    }

    /** The world keeps what the last round left, so everything the town may cover goes first. */
    private void clear() {
        for (int x = -CLEARED; x <= CLEARED; x++) {
            for (int z = -CLEARED; z <= CLEARED; z++) {
                for (int y = GROUND - 6; y <= LOBBY_Y + 4; y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void ground() {
        for (int x = -EDGE; x <= EDGE; x++) {
            for (int z = -EDGE; z <= EDGE; z++) {
                set(x, GROUND - 3, z, Material.STONE);
                set(x, GROUND - 2, z, Material.DIRT);
                set(x, GROUND - 1, z, Material.DIRT);
                set(x, GROUND, z, Math.floorMod(x * 7 + z * 13, 11) == 0 ? Material.COARSE_DIRT : Material.GRASS_BLOCK);
                if (Math.abs(x) == EDGE || Math.abs(z) == EDGE) {
                    set(x, GROUND + 1, z, leaves(Material.OAK_LEAVES));
                    set(x, GROUND + 2, z, leaves(Material.OAK_LEAVES));
                }
            }
        }
    }

    private void streets() {
        for (int d = 9; d < EDGE; d++) {
            for (int w = -2; w <= 2; w++) {
                var path = Math.abs(w) == 2 ? Material.GRAVEL : Material.DIRT_PATH;
                set(w, GROUND, d, path);
                set(w, GROUND, -d, path);
                set(d, GROUND, w, path);
                set(-d, GROUND, w, path);
            }
        }
    }

    private void plaza() {
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                boolean ring = Math.abs(x) == 8 || Math.abs(z) == 8;
                set(x, GROUND, z, ring ? Material.STONE_BRICKS
                        : Math.floorMod(x + z, 2) == 0 ? Material.POLISHED_ANDESITE : Material.ANDESITE);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(x, GROUND, z, Material.STONE_BRICKS);
                set(x, GROUND + 1, z, rim ? Material.STONE_BRICKS : Material.WATER);
            }
        }
        fill(0, GROUND + 1, 0, 0, GROUND + 3, 0, Material.CHISELED_STONE_BRICKS);
        set(0, GROUND + 4, 0, Material.LANTERN);
        for (int x : new int[]{-7, 7}) {
            for (int z : new int[]{-7, 7}) {
                lampPost(x, z);
            }
        }
    }

    /** Two houses, a shed, a garden chest by the hay and a tree; {@code sx} and {@code sz} pick the quarter. */
    private void quarter(int sx, int sz) {
        boolean firstUpper = sx == sz;
        var materials = sx > 0
                ? new Material[]{Material.OAK_PLANKS, Material.OAK_LOG, Material.SPRUCE_PLANKS, Material.SPRUCE_PLANKS}
                : new Material[]{Material.WHITE_TERRACOTTA, Material.DARK_OAK_LOG, Material.OAK_PLANKS, Material.DARK_OAK_PLANKS};
        house(new House(5 * sx, 13 * sz, 13 * sx, 20 * sz, sx > 0 ? BlockFace.WEST : BlockFace.EAST,
                materials[0], materials[1], materials[2], materials[3], firstUpper));
        house(new House(16 * sx, 5 * sz, 24 * sx, 11 * sz, sz > 0 ? BlockFace.NORTH : BlockFace.SOUTH,
                sz > 0 ? Material.BRICKS : Material.STONE_BRICKS, Material.STRIPPED_SPRUCE_LOG, Material.SPRUCE_PLANKS,
                Material.STONE_BRICKS, !firstUpper));
        shed(12 * sx, 31 * sz);
        fill(22 * sx, GROUND + 1, 17 * sz, 23 * sx, GROUND + 1, 18 * sz, Material.HAY_BLOCK);
        set(23 * sx, GROUND + 2, 18 * sz, Material.HAY_BLOCK);
        chest(21 * sx, GROUND + 1, 18 * sz);
        tree(31 * sx, 22 * sz);
        tree(20 * sx, 34 * sz);
    }

    private void house(House house) {
        int x1 = Math.min(house.x1, house.x2);
        int x2 = Math.max(house.x1, house.x2);
        int z1 = Math.min(house.z1, house.z2);
        int z2 = Math.max(house.z1, house.z2);
        int top = house.upperFloor ? GROUND + 10 : GROUND + 5;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                set(x, GROUND, z, house.floor);
                for (int y = GROUND + 1; y < top; y++) {
                    if (corner) {
                        set(x, y, z, house.corner);
                    } else if (wall) {
                        set(x, y, z, window(x, y, z, x1, z1) ? Material.GLASS : house.wall);
                    } else if (y == GROUND + 5) {
                        set(x, y, z, house.floor);
                    }
                }
                set(x, top, z, house.roof);
                if (wall) {
                    set(x, top + 1, z, slab(house.roof));
                }
            }
        }
        var door = house.door;
        var back = door.getOppositeFace();
        int doorX = door.getModX() == 0 ? (x1 + x2) / 2 : door.getModX() > 0 ? x2 : x1;
        int doorZ = door.getModZ() == 0 ? (z1 + z2) / 2 : door.getModZ() > 0 ? z2 : z1;
        door(doorX, doorZ, door);
        int backX = back.getModX() == 0 ? (x1 + x2) / 2 : back.getModX() > 0 ? x2 - 1 : x1 + 1;
        int backZ = back.getModZ() == 0 ? (z1 + z2) / 2 : back.getModZ() > 0 ? z2 - 1 : z1 + 1;
        for (int y = GROUND + 1; y <= top; y++) {
            set(backX, y, backZ, directional(Material.LADDER, door));
        }
        int sideX = door.getModZ() == 0 ? 0 : 1;
        int sideZ = door.getModX() == 0 ? 0 : 1;
        int span = door.getModZ() == 0 ? (z2 - z1) / 2 - 1 : (x2 - x1) / 2 - 1;
        chest(backX + sideX * span, GROUND + 1, backZ + sideZ * span);
        set(backX - sideX * span, GROUND + 1, backZ - sideZ * span, Material.CRAFTING_TABLE);
        set(backX - sideX * (span - 1), GROUND + 1, backZ - sideZ * (span - 1), Material.BOOKSHELF);
        if (house.upperFloor) {
            chest(backX - sideX * span, GROUND + 6, backZ - sideZ * span);
            set(backX + sideX * span, GROUND + 6, backZ + sideZ * span, Material.BOOKSHELF);
        }
        set((x1 + x2) / 2, GROUND + 1, (z1 + z2) / 2, Material.RED_CARPET);
    }

    private static boolean window(int x, int y, int z, int x1, int z1) {
        int floorY = (y - GROUND - 1) % 5;
        return (floorY == 1 || floorY == 2) && Math.floorMod(x - x1 + z - z1, 3) == 2;
    }

    /** A doorway in the wall facing {@code facing}, with its door. */
    private void door(int x, int z, BlockFace facing) {
        for (var half : Bisected.Half.values()) {
            set(x, half == Bisected.Half.BOTTOM ? GROUND + 1 : GROUND + 2, z, Material.OAK_DOOR.createBlockData(data -> {
                var door = (Door) data;
                door.setFacing(facing);
                door.setHalf(half);
            }));
        }
        set(x + facing.getModX(), GROUND, z + facing.getModZ(), Material.COBBLESTONE);
    }

    private void shed(int x, int z) {
        fill(x - 2, GROUND + 1, z - 2, x + 2, GROUND + 3, z + 2, Material.SPRUCE_PLANKS);
        fill(x - 1, GROUND + 1, z - 1, x + 1, GROUND + 3, z + 2, Material.AIR);
        fill(x - 2, GROUND + 4, z - 2, x + 2, GROUND + 4, z + 2, Material.SPRUCE_SLAB);
        chest(x, GROUND + 1, z - 1);
        set(x - 1, GROUND + 1, z - 1, Material.SPRUCE_LOG);
        set(x + 1, GROUND + 1, z - 1, Material.HAY_BLOCK);
    }

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
                        set(x, y, z, x == x1 && Math.abs(z) <= 1 && y <= GROUND + 3 ? Material.AIR : Material.SPRUCE_PLANKS);
                    } else if (y == GROUND + 4 && x >= 36) {
                        set(x, y, z, Material.SPRUCE_SLAB);
                    }
                }
                set(x, GROUND + 7, z, Material.DARK_OAK_PLANKS);
            }
        }
        fill(36, GROUND + 1, 0, 36, GROUND + 3, 0, Material.SPRUCE_LOG);
        for (int y = GROUND + 1; y <= GROUND + 4; y++) {
            set(35, y, 0, directional(Material.LADDER, BlockFace.WEST));
        }
        fill(37, GROUND + 1, -5, 39, GROUND + 2, -3, Material.HAY_BLOCK);
        fill(37, GROUND + 1, 3, 39, GROUND + 1, 5, Material.HAY_BLOCK);
        chest(39, GROUND + 1, 0);
        chest(31, GROUND + 1, -5);
        chest(39, GROUND + 5, -5);
        set(39, GROUND + 5, 5, Material.HAY_BLOCK);
    }

    private void market() {
        var canopies = new Material[]{Material.RED_WOOL, Material.YELLOW_WOOL, Material.BLUE_WOOL, Material.LIME_WOOL};
        int index = 0;
        for (int x : new int[]{-38, -32}) {
            for (int z : new int[]{-6, 6}) {
                fill(x - 1, GROUND + 1, z, x + 1, GROUND + 1, z, Material.SPRUCE_PLANKS);
                for (int dx : new int[]{-2, 2}) {
                    for (int dz : new int[]{-1, 1}) {
                        fill(x + dx, GROUND + 1, z + dz, x + dx, GROUND + 3, z + dz, Material.SPRUCE_FENCE);
                    }
                }
                fill(x - 2, GROUND + 4, z - 1, x + 2, GROUND + 4, z + 1, canopies[index++]);
            }
        }
        chest(-38, GROUND + 1, 6 + 1);
        chest(-32, GROUND + 1, -6 - 1);
        set(-35, GROUND + 1, 0, Material.CAULDRON);
        tree(-40, -14);
        tree(-40, 14);
    }

    /** The chapel at the south end of the street; its altar is the round's only ender chest. */
    private Block chapel() {
        for (int x = -4; x <= 4; x++) {
            for (int z = 30; z <= 38; z++) {
                boolean wall = Math.abs(x) == 4 || z == 30 || z == 38;
                set(x, GROUND, z, Material.POLISHED_DIORITE);
                for (int y = GROUND + 1; y <= GROUND + 6; y++) {
                    if (wall) {
                        boolean doorway = z == 30 && x == 0 && y <= GROUND + 2;
                        boolean window = Math.abs(x) == 4 && (y == GROUND + 3 || y == GROUND + 4) && z % 2 == 0;
                        set(x, y, z, doorway ? Material.AIR : window ? Material.LIGHT_BLUE_STAINED_GLASS : Material.QUARTZ_BRICKS);
                    }
                }
                set(x, GROUND + 7, z, Material.DEEPSLATE_TILES);
            }
        }
        fill(0, GROUND + 8, 34, 0, GROUND + 11, 34, Material.QUARTZ_PILLAR);
        set(0, GROUND + 12, 34, Material.LANTERN);
        for (int z : new int[]{32, 34}) {
            fill(-3, GROUND + 1, z, -1, GROUND + 1, z, Material.SPRUCE_SLAB);
            fill(1, GROUND + 1, z, 3, GROUND + 1, z, Material.SPRUCE_SLAB);
        }
        set(0, GROUND + 1, 37, Material.QUARTZ_BLOCK);
        return set(0, GROUND + 2, 37, Material.ENDER_CHEST);
    }

    /**
     * The tester at the north end of the street: a stone room with one doorway and the button on its back wall.
     * The lamps over the doorway and on the back wall show everyone outside and in what the test found.
     */
    private TesterRoom tester() {
        for (int x = -4; x <= 4; x++) {
            for (int z = -38; z <= -30; z++) {
                boolean wall = Math.abs(x) == 4 || z == -30 || z == -38;
                set(x, GROUND, z, Material.SMOOTH_STONE);
                for (int y = GROUND + 1; y <= GROUND + 5; y++) {
                    if (wall) {
                        set(x, y, z, Material.STONE_BRICKS);
                    }
                }
                set(x, GROUND + 6, z, Material.POLISHED_ANDESITE);
            }
        }
        var entrance = List.of(set(0, GROUND + 1, -30, Material.AIR), set(0, GROUND + 2, -30, Material.AIR));
        var lamps = new ArrayList<Block>();
        for (int x = -1; x <= 1; x++) {
            lamps.add(set(x, GROUND + 4, -30, Material.REDSTONE_LAMP));
        }
        for (int x : new int[]{-2, 0, 2}) {
            lamps.add(set(x, GROUND + 4, -38, Material.REDSTONE_LAMP));
        }
        var button = set(0, GROUND + 2, -37, Material.STONE_BUTTON.createBlockData(data -> {
            var attached = (FaceAttachable) data;
            attached.setAttachedFace(FaceAttachable.AttachedFace.WALL);
            ((Directional) data).setFacing(BlockFace.SOUTH);
        }));
        lampPost(-4, -28);
        lampPost(4, -28);
        return new TesterRoom(Box.of(-3, GROUND + 1, -37, 3, GROUND + 4, -31), button, entrance, List.copyOf(lamps));
    }

    private List<Location> spawns() {
        var spawns = new ArrayList<Location>();
        int count = 12;
        for (int index = 0; index < count; index++) {
            double angle = 2 * Math.PI * index / count;
            double x = Math.sin(angle) * SPAWN_RADIUS + 0.5;
            double z = Math.cos(angle) * SPAWN_RADIUS + 0.5;
            float yaw = (float) Math.toDegrees(Math.atan2(x - 0.5, -(z - 0.5)));
            spawns.add(new Location(world, x, GROUND + 1, z, yaw, 0));
        }
        return List.copyOf(spawns);
    }

    private Location lobby() {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                boolean edge = Math.abs(x) == 7 || Math.abs(z) == 7;
                set(x, LOBBY_Y, z, edge ? Material.RED_CONCRETE : Material.SMOOTH_QUARTZ);
                if (edge) {
                    set(x, LOBBY_Y + 1, z, Material.GLASS);
                    set(x, LOBBY_Y + 2, z, Material.GLASS);
                }
            }
        }
        return new Location(world, 0.5, LOBBY_Y + 1, 0.5);
    }

    private void tree(int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int y = GROUND + 4; y <= GROUND + 7; y++) {
                    int reach = y >= GROUND + 6 ? 1 : 2;
                    if (Math.abs(dx) <= reach && Math.abs(dz) <= reach && !(Math.abs(dx) == 2 && Math.abs(dz) == 2)) {
                        set(x + dx, y, z + dz, leaves(Material.OAK_LEAVES));
                    }
                }
            }
        }
        fill(x, GROUND + 1, z, x, GROUND + 5, z, Material.OAK_LOG);
    }

    private void lampPost(int x, int z) {
        fill(x, GROUND + 1, z, x, GROUND + 3, z, Material.SPRUCE_FENCE);
        set(x, GROUND + 4, z, Material.LANTERN);
    }

    private void chest(int x, int y, int z) {
        chests.add(set(x, y, z, Material.CHEST));
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        var box = Box.of(x1, y1, z1, x2, y2, z2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    set(x, y, z, material);
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

    private static Material slab(Material roof) {
        return switch (roof) {
            case STONE_BRICKS -> Material.STONE_BRICK_SLAB;
            case DARK_OAK_PLANKS -> Material.DARK_OAK_SLAB;
            default -> Material.SPRUCE_SLAB;
        };
    }
}
