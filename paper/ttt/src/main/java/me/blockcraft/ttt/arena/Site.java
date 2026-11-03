package me.blockcraft.ttt.arena;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.FaceAttachable;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Candle;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.block.data.type.Wall;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The world a map is being built into, with the tools every district shares: placing blocks without physics,
 * weathering stone, furniture, and the lists the finished map hands TTT (lights, wooden doors, traitor doors,
 * chores). Fences, panes, bars and walls are remembered and joined to their neighbours by {@link #connect} once
 * everything stands.
 */
final class Site {

    /** The village's grass; floors of ground-level buildings lie here and players stand one above. */
    static final int GROUND = 64;
    /** The abbey's terrace, four blocks above the village. */
    static final int TERRACE = 68;
    /** The floor of every cellar; cellars are four blocks tall under a ceiling at {@code GROUND - 1}. */
    static final int CELLAR = 58;

    static final List<BlockFace> SIDES = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    final World world;
    final Random random;
    private final List<Block> connecting = new ArrayList<>();
    private final List<Block> lights = new ArrayList<>();
    private final List<Block> doors = new ArrayList<>();
    private final List<Block> traitorDoors = new ArrayList<>();
    private final List<Chore> chores = new ArrayList<>();

    Site(World world, long seed) {
        this.world = world;
        this.random = new Random(seed);
    }

    List<Block> lights() {
        return lights;
    }

    List<Block> doors() {
        return doors;
    }

    List<Block> traitorDoors() {
        return traitorDoors;
    }

    List<Chore> chores() {
        return chores;
    }

    void chore(Chore chore) {
        chores.add(chore);
    }

    Block block(int x, int y, int z) {
        return world.getBlockAt(x, y, z);
    }

    Block set(int x, int y, int z, Material material) {
        var block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        return block;
    }

    Block set(int x, int y, int z, BlockData data) {
        var block = world.getBlockAt(x, y, z);
        block.setBlockData(data, false);
        return block;
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        fill(x1, y1, z1, x2, y2, z2, material.createBlockData());
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockData data) {
        var box = Box.of(x1, y1, z1, x2, y2, z2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    set(x, y, z, data);
                }
            }
        }
    }

    void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        fill(x1, y1, z1, x2, y2, z2, Material.AIR);
    }

    /** Fills the range with {@code material}, aged block by block as {@link #worn} ages it. */
    void rough(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        var box = Box.of(x1, y1, z1, x2, y2, z2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    set(x, y, z, worn(material));
                }
            }
        }
    }

    /** The outer walls of a box from {@code y1} to {@code y2}, the inside left alone. */
    void walls(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        var box = Box.of(x1, y1, z1, x2, y2, z2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                if (x == box.minX() || x == box.maxX() || z == box.minZ() || z == box.maxZ()) {
                    for (int y = box.minY(); y <= box.maxY(); y++) {
                        set(x, y, z, worn(material));
                    }
                }
            }
        }
    }

    /** A room dug or walled out: floor, walls and ceiling of {@code shell}, and air inside. */
    void vault(int x1, int y1, int z1, int x2, int y2, int z2, Material shell) {
        var box = Box.of(x1, y1, z1, x2, y2, z2);
        rough(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ(), shell);
        air(box.minX() + 1, box.minY() + 1, box.minZ() + 1, box.maxX() - 1, box.maxY() - 1, box.maxZ() - 1);
    }

    /** Packed earth for a lane over open ground, cobbles where carts turn, gravel at the edges; floors stay. */
    void pave(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                var ground = block(x, GROUND, z).getType();
                if (ground != Material.GRASS_BLOCK && ground != Material.MOSS_BLOCK && ground != Material.COARSE_DIRT) {
                    continue;
                }
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                int roll = random.nextInt(10);
                set(x, GROUND, z, edge && roll < 3 ? Material.GRAVEL
                        : roll == 0 ? Material.COBBLESTONE : roll == 1 ? Material.COARSE_DIRT : Material.DIRT_PATH);
            }
        }
    }

    /** Stone and wood as weather leaves them: some bricks mossy or cracked, some cobbles mossy. */
    Material worn(Material material) {
        int roll = random.nextInt(100);
        return switch (material) {
            case STONE_BRICKS -> roll < 18 ? Material.MOSSY_STONE_BRICKS : roll < 30 ? Material.CRACKED_STONE_BRICKS : material;
            case COBBLESTONE -> roll < 25 ? Material.MOSSY_COBBLESTONE : material;
            case DEEPSLATE_BRICKS -> roll < 20 ? Material.CRACKED_DEEPSLATE_BRICKS : material;
            case DEEPSLATE_TILES -> roll < 20 ? Material.CRACKED_DEEPSLATE_TILES : material;
            case TUFF_BRICKS -> roll < 15 ? Material.CHISELED_TUFF_BRICKS : roll < 30 ? Material.TUFF : material;
            case STONE -> roll < 10 ? Material.ANDESITE : roll < 16 ? Material.COBBLESTONE : material;
            case MUD_BRICKS -> roll < 15 ? Material.PACKED_MUD : material;
            default -> material;
        };
    }

    void lantern(int x, int y, int z, boolean hanging) {
        lights.add(set(x, y, z, Material.LANTERN.createBlockData(data -> ((Lantern) data).setHanging(hanging))));
    }

    /** A lantern on a chain of {@code length} blocks below the block at {@code y + length + 1}. */
    void chandelier(int x, int y, int z, int length) {
        fill(x, y + 1, z, x, y + length, z, Material.IRON_CHAIN);
        lantern(x, y, z, true);
    }

    /** A torch on the wall behind it; {@code facing} points away from that wall. */
    void torch(int x, int y, int z, BlockFace facing) {
        lights.add(set(x, y, z, directional(Material.WALL_TORCH, facing)));
    }

    void candles(int x, int y, int z, int count) {
        lights.add(set(x, y, z, Material.CANDLE.createBlockData(data -> {
            var candle = (Candle) data;
            candle.setCandles(count);
            candle.setLit(true);
        })));
    }

    /** A lit fire that a blackout puts out. */
    void fire(int x, int y, int z) {
        lights.add(set(x, y, z, Material.CAMPFIRE.createBlockData(data -> ((Lightable) data).setLit(true))));
    }

    /** A redstone lamp that burns without power, because nothing updates it. */
    void lamp(int x, int y, int z) {
        lights.add(set(x, y, z, Material.REDSTONE_LAMP.createBlockData(data -> ((Lightable) data).setLit(true))));
    }

    /** A block whose station a chore lights up, so it starts out cold. */
    Block unlit(int x, int y, int z, Material material, BlockFace facing) {
        return set(x, y, z, material.createBlockData(data -> {
            ((Lightable) data).setLit(false);
            if (data instanceof Directional directional) {
                directional.setFacing(facing);
            }
        }));
    }

    /**
     * A wooden door in the wall whose outside faces {@code outward}; a sabotage can lock it. The doorway reaches two
     * blocks up.
     */
    Block door(int x, int y, int z, BlockFace outward, Material material) {
        var lower = hang(x, y, z, outward, material);
        doors.add(lower);
        return lower;
    }

    /** An iron door only living traitors open, in the wall whose far side faces {@code outward}. */
    Block traitorDoor(int x, int y, int z, BlockFace outward) {
        var lower = hang(x, y, z, outward, Material.IRON_DOOR);
        traitorDoors.add(lower);
        return lower;
    }

    private Block hang(int x, int y, int z, BlockFace outward, Material material) {
        for (var half : Bisected.Half.values()) {
            set(x, half == Bisected.Half.BOTTOM ? y : y + 1, z, material.createBlockData(data -> {
                var door = (Door) data;
                door.setFacing(outward.getOppositeFace());
                door.setHalf(half);
            }));
        }
        return block(x, y, z);
    }

    Block chest(int x, int y, int z, BlockFace facing) {
        return set(x, y, z, directional(Material.CHEST, facing));
    }

    /** A closed hatch lying flush with the floor above it. */
    void hatch(int x, int y, int z, BlockFace facing, Material material) {
        set(x, y, z, material.createBlockData(data -> {
            var trapdoor = (TrapDoor) data;
            trapdoor.setHalf(Bisected.Half.TOP);
            trapdoor.setFacing(facing);
        }));
    }

    /** A trapdoor folded up against the block behind it, as a shutter or a shelf front. */
    void shutter(int x, int y, int z, BlockFace facing, Material material) {
        set(x, y, z, material.createBlockData(data -> {
            var trapdoor = (TrapDoor) data;
            trapdoor.setFacing(facing);
            trapdoor.setOpen(true);
        }));
    }

    /** A ladder from {@code y1} to {@code y2}; {@code facing} points away from the wall it hangs on. */
    void ladder(int x, int y1, int y2, int z, BlockFace facing) {
        fill(x, y1, z, x, y2, z, directional(Material.LADDER, facing));
    }

    void button(int x, int y, int z, BlockFace facing, Material material) {
        set(x, y, z, material.createBlockData(data -> {
            ((FaceAttachable) data).setAttachedFace(FaceAttachable.AttachedFace.WALL);
            ((Directional) data).setFacing(facing);
        }));
    }

    void bed(int x, int y, int z, BlockFace facing, Material material) {
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

    void table(int x, int y, int z) {
        fence(x, y, z, Material.SPRUCE_FENCE);
        set(x, y + 1, z, Material.SPRUCE_PRESSURE_PLATE);
    }

    /** A chair whose sitter looks towards {@code facing}. */
    void chair(int x, int y, int z, BlockFace facing, Material stairs) {
        set(x, y, z, stairs(stairs, facing.getOppositeFace(), Bisected.Half.BOTTOM));
    }

    void fence(int x, int y, int z, Material material) {
        connecting.add(set(x, y, z, material));
    }

    void fences(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        var box = Box.of(x1, y1, z1, x2, y2, z2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    fence(x, y, z, material);
                }
            }
        }
    }

    void pane(int x, int y, int z, Material material) {
        connecting.add(set(x, y, z, material));
    }

    void bars(int x, int y, int z) {
        connecting.add(set(x, y, z, Material.IRON_BARS));
    }

    void wall(int x, int y, int z, Material material) {
        connecting.add(set(x, y, z, material));
    }

    void pot(int x, int y, int z) {
        var plants = List.of(Material.POTTED_RED_TULIP, Material.POTTED_FERN, Material.POTTED_AZURE_BLUET,
                Material.POTTED_CORNFLOWER, Material.POTTED_OXEYE_DAISY, Material.POTTED_SPRUCE_SAPLING);
        set(x, y, z, plants.get(random.nextInt(plants.size())));
    }

    void tree(int x, int y, int z, Material log) {
        var leaves = switch (log) {
            case BIRCH_LOG -> Material.BIRCH_LEAVES;
            case SPRUCE_LOG -> Material.SPRUCE_LEAVES;
            case DARK_OAK_LOG -> Material.DARK_OAK_LEAVES;
            default -> Material.OAK_LEAVES;
        };
        if (log == Material.SPRUCE_LOG) {
            int height = 7 + random.nextInt(3);
            for (int dy = 2; dy <= height; dy++) {
                int reach = Math.min(dy == height ? 0 : (height - dy) % 2 == 0 ? 1 : 2, (height - dy + 2) / 2);
                crown(x, y + dy, z, reach, leaves);
            }
            set(x, y + height + 1, z, leaves(leaves));
            fill(x, y + 1, z, x, y + height - 1, z, log);
            return;
        }
        int trunk = log == Material.BIRCH_LOG ? 5 + random.nextInt(2) : 4 + random.nextInt(2);
        for (int dy = trunk - 1; dy <= trunk + 2; dy++) {
            crown(x, y + dy, z, dy >= trunk + 1 ? 1 : 2, leaves);
        }
        fill(x, y + 1, z, x, y + trunk, z, log);
    }

    /** A fruit tree: a short trunk under a low, round crown. */
    void fruitTree(int x, int y, int z) {
        fill(x, y + 1, z, x, y + 2, z, Material.OAK_LOG);
        crown(x, y + 3, z, 2, Material.OAK_LEAVES);
        crown(x, y + 4, z, 1, Material.FLOWERING_AZALEA_LEAVES);
        set(x, y + 3, z, Material.OAK_LOG);
    }

    private void crown(int x, int y, int z, int reach, Material leaves) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                boolean corner = Math.abs(dx) == reach && Math.abs(dz) == reach && reach > 0;
                if ((!corner || random.nextInt(3) == 0) && block(x + dx, y, z + dz).isEmpty()) {
                    set(x + dx, y, z + dz, leaves(leaves));
                }
            }
        }
    }

    /**
     * A gable roof over the box, its ridge along the longer side: stairs rise from both eaves, which overhang the
     * walls by one block, to a ridge of slabs; the gable ends are {@code gable} with a small window.
     */
    void roof(int x1, int z1, int x2, int z2, int baseY, Material stairs, Material ridge, Material gable) {
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
                    roofBlock(ridgeAlongX, along, near, y, slab(ridge, Slab.Type.BOTTOM));
                } else {
                    roofBlock(ridgeAlongX, along, near, y, stairs(stairs, rising, Bisected.Half.BOTTOM));
                    roofBlock(ridgeAlongX, along, far, y, stairs(stairs, rising.getOppositeFace(), Bisected.Half.BOTTOM));
                }
            }
            for (int across = Math.max(near + 1, low); across <= Math.min(far - 1, high); across++) {
                for (int end : new int[]{from, to}) {
                    boolean window = across == (low + high) / 2 && step == 2;
                    roofBlock(ridgeAlongX, end, across, y, (window ? Material.GLASS : gable).createBlockData());
                }
            }
        }
    }

    private void roofBlock(boolean ridgeAlongX, int along, int across, int y, BlockData data) {
        if (ridgeAlongX) {
            set(along, y, across, data);
        } else {
            set(across, y, along, data);
        }
    }

    /**
     * The walls of a timber-framed or stone house standing on {@code base}: beams at the corners and every four
     * blocks up, panes along each wall, a floor of {@code floor} on every storey and the attic floor on top. Returns
     * the height of the top beam.
     */
    int shell(int x1, int z1, int x2, int z2, int base, int storeys, Material wall, Material beam, Material floor) {
        int top = base + 4 * storeys;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                set(x, base, z, floor);
                for (int y = base + 1; y <= top; y++) {
                    int level = (y - base) % 4;
                    if (!edge) {
                        set(x, y, z, level == 0 ? floor : Material.AIR);
                    } else if (corner || level == 0) {
                        set(x, y, z, corner ? beam.createBlockData() : log(beam, x == x1 || x == x2 ? Axis.Z : Axis.X));
                    } else if ((level == 2 || level == 3) && window(x, z, x1, z1, x2, z2)) {
                        pane(x, y, z, Material.GLASS_PANE);
                    } else {
                        set(x, y, z, worn(wall));
                    }
                }
                set(x, top + 1, z, edge ? worn(wall) : floor);
            }
        }
        return top;
    }

    private static boolean window(int x, int z, int x1, int z1, int x2, int z2) {
        boolean alongX = z == z1 || z == z2;
        int along = alongX ? x - x1 : z - z1;
        int length = alongX ? x2 - x1 : z2 - z1;
        return along >= 2 && along <= length - 2 && along % 3 == 2;
    }

    /** Fences, panes, bars and walls join whatever solid or alike stands beside them, as they would when placed by hand. */
    void connect() {
        for (var block : connecting) {
            var data = block.getBlockData();
            if (data instanceof MultipleFacing facing) {
                for (var side : SIDES) {
                    if (facing.getAllowedFaces().contains(side)) {
                        facing.setFace(side, joins(block, block.getRelative(side)));
                    }
                }
            } else if (data instanceof Wall wall) {
                for (var side : SIDES) {
                    wall.setHeight(side, joins(block, block.getRelative(side)) ? Wall.Height.LOW : Wall.Height.NONE);
                }
                wall.setUp(!block.getRelative(BlockFace.UP).isEmpty() || !straight(wall));
            } else {
                continue;
            }
            block.setBlockData(data, false);
        }
    }

    private static boolean straight(Wall wall) {
        boolean northSouth = wall.getHeight(BlockFace.NORTH) != Wall.Height.NONE && wall.getHeight(BlockFace.SOUTH) != Wall.Height.NONE;
        boolean eastWest = wall.getHeight(BlockFace.EAST) != Wall.Height.NONE && wall.getHeight(BlockFace.WEST) != Wall.Height.NONE;
        boolean anyOther = northSouth
                ? wall.getHeight(BlockFace.EAST) != Wall.Height.NONE || wall.getHeight(BlockFace.WEST) != Wall.Height.NONE
                : wall.getHeight(BlockFace.NORTH) != Wall.Height.NONE || wall.getHeight(BlockFace.SOUTH) != Wall.Height.NONE;
        return (northSouth || eastWest) && !anyOther;
    }

    private static boolean joins(Block block, Block neighbour) {
        var type = neighbour.getType();
        if (type == block.getType()) {
            return true;
        }
        if (Tag.FENCES.isTagged(block.getType())) {
            return Tag.FENCES.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || type.isOccluding();
        }
        return Tag.WALLS.isTagged(type) || type == Material.IRON_BARS || type.name().endsWith("GLASS_PANE")
                || Tag.FENCE_GATES.isTagged(type) || type.isOccluding();
    }

    static BlockFace left(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            default -> BlockFace.NORTH;
        };
    }

    static BlockData leaves(Material material) {
        return material.createBlockData(data -> ((Leaves) data).setPersistent(true));
    }

    static BlockData directional(Material material, BlockFace facing) {
        return material.createBlockData(data -> ((Directional) data).setFacing(facing));
    }

    static BlockData rotated(Material material, BlockFace facing) {
        return material.createBlockData(data -> ((Rotatable) data).setRotation(facing));
    }

    static BlockData log(Material material, Axis axis) {
        return material.createBlockData(data -> ((Orientable) data).setAxis(axis));
    }

    static BlockData stairs(Material material, BlockFace facing, Bisected.Half half) {
        return material.createBlockData(data -> {
            var stairs = (Stairs) data;
            stairs.setFacing(facing);
            stairs.setHalf(half);
        });
    }

    static BlockData slab(Material material, Slab.Type type) {
        return material.createBlockData(data -> ((Slab) data).setType(type));
    }

    static BlockData half(Material material, Bisected.Half half) {
        return material.createBlockData(data -> ((Bisected) data).setHalf(half));
    }
}
