package me.blockcraft.cores.arena;

import me.blockcraft.api.game.LootChests;
import me.blockcraft.cores.arena.CoresMap.Box;
import me.blockcraft.cores.game.Core;
import me.blockcraft.cores.game.Side;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Himmelsfeste, the map Cores builds into an empty world at every start: a floating base per team at either end,
 * each with two cores in their protected cubes, its spawn, iron to mine, an enchanting table with shelves, anvils,
 * workbenches and a chest of experience bottles; diamonds in the middle, and two side islands with loot and gold.
 * A short lane runs straight from base to base through the middle, two long ones round the flanks. The blue half
 * mirrors the red one, so {@code d} below is the distance from the middle towards a team's end.
 */
public final class SkyMap {

    public static final String NAME = "Himmelsfeste";

    private static final int FLOOR = 64;
    private static final Box CLEARED = new Box(-42, 40, -82, 42, 125, 82);
    private static final Box BORDER = new Box(-40, 0, -80, 40, 319, 80);
    private static final int BASE_HALF_WIDTH = 12;
    private static final int BASE_NEAR = 48;
    private static final int BASE_FAR = 72;
    private static final int CORE_X = 8;
    private static final int CORE_D = 56;
    private static final int SPAWN_D = 68;
    private static final int FLANK_X = 28;
    private static final int[][] UNDERSIDE = {{1, 63}, {3, 62}, {6, 61}, {9, 60}};
    private static final int XP_STACKS = 27;

    private final World world;
    private final LootChests loot;
    private final Set<Long> protectedBlocks = new HashSet<>();
    private final List<Box> noBuild = new ArrayList<>();
    private final List<Core> cores = new ArrayList<>();
    private final Map<Side, Location> spawns = new EnumMap<>(Side.class);
    private final List<Block> lootChests = new ArrayList<>();

    private SkyMap(World world, LootChests loot) {
        this.world = world;
        this.loot = loot;
    }

    public static CoresMap build(World world, LootChests loot) {
        return new SkyMap(world, loot).build();
    }

    private CoresMap build() {
        clear();
        for (var side : Side.values()) {
            base(side);
        }
        middle();
        for (int sign : new int[]{-1, 1}) {
            flank(sign);
        }
        var lobby = lobby();
        lootChests.forEach(this::fillLoot);
        return new CoresMap(NAME, world, Map.copyOf(spawns), List.copyOf(cores), Set.copyOf(protectedBlocks),
                List.copyOf(noBuild), BORDER, lobby, new Location(world, 0.5, 90, 0.5, 0, 60));
    }

    /** The world keeps what the last round built, so everything the map may cover goes first. */
    private void clear() {
        for (int x = CLEARED.minX(); x <= CLEARED.maxX(); x++) {
            for (int z = CLEARED.minZ(); z <= CLEARED.maxZ(); z++) {
                for (int y = CLEARED.minY(); y <= CLEARED.maxY(); y++) {
                    var block = world.getBlockAt(x, y, z);
                    if (!block.isEmpty()) {
                        block.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private void base(Side side) {
        for (int x = -BASE_HALF_WIDTH; x <= BASE_HALF_WIDTH; x++) {
            for (int d = BASE_NEAR; d <= BASE_FAR; d++) {
                boolean edge = Math.abs(x) == BASE_HALF_WIDTH || d == BASE_NEAR || d == BASE_FAR;
                set(x, FLOOR, z(side, d), edge ? side.concrete() : brick(x, d), true);
            }
        }
        for (var layer : UNDERSIDE) {
            int inset = layer[0];
            for (int x = -BASE_HALF_WIDTH + inset; x <= BASE_HALF_WIDTH - inset; x++) {
                for (int d = BASE_NEAR + inset; d <= BASE_FAR - inset; d++) {
                    set(x, layer[1], z(side, d), rock(x, layer[1], d), true);
                }
            }
        }
        fill(side, -1, 1, FLOOR, FLOOR, SPAWN_D - 1, SPAWN_D + 1, side.wool(), true);
        noBuild.add(box(side, -1, 1, FLOOR + 1, FLOOR + 3, SPAWN_D - 1, SPAWN_D + 1));
        spawns.put(side, new Location(world, 0.5, FLOOR + 1, z(side, SPAWN_D) + 0.5, side == Side.RED ? 0 : 180, 0));
        for (boolean left : new boolean[]{true, false}) {
            core(side, left, (left ? -side.direction() : side.direction()) * CORE_X);
        }
        set(-6, FLOOR + 1, z(side, 69), Material.ENCHANTING_TABLE, true);
        fill(side, -8, -4, FLOOR + 1, FLOOR + 2, 71, 71, Material.BOOKSHELF, true);
        fill(side, 4, 7, FLOOR + 1, FLOOR + 1, 71, 71, Material.ANVIL, true);
        for (int x : new int[]{-3, 3}) {
            set(x, FLOOR + 1, z(side, 70), Material.CRAFTING_TABLE, true);
        }
        var experience = set(0, FLOOR + 1, z(side, 71), Material.CHEST, true);
        var bottles = new ItemStack[XP_STACKS];
        Arrays.fill(bottles, ItemStack.of(Material.EXPERIENCE_BOTTLE, 64));
        ((Chest) experience.getState()).getBlockInventory().setContents(bottles);
        for (int sign : new int[]{-1, 1}) {
            fill(side, sign * 9, sign * 11, FLOOR + 1, FLOOR + 2, 62, 67, Material.IRON_BLOCK, false);
            for (int d : new int[]{BASE_NEAR, BASE_FAR}) {
                set(sign * BASE_HALF_WIDTH, FLOOR + 1, z(side, d), Material.OAK_FENCE, true);
                set(sign * BASE_HALF_WIDTH, FLOOR + 2, z(side, d), Material.LANTERN, true);
            }
        }
        fill(side, -1, 1, FLOOR, FLOOR, 11, BASE_NEAR - 1, Material.STONE_BRICKS, false);
    }

    /** A beacon on its iron, ringed by glass: its cube keeps every block as it is, so it is only reached from above. */
    private void core(Side side, boolean left, int x) {
        var cube = box(side, x - 1, x + 1, FLOOR + 1, FLOOR + 3, CORE_D - 1, CORE_D + 1);
        noBuild.add(cube);
        for (int bx = cube.minX(); bx <= cube.maxX(); bx++) {
            for (int bz = cube.minZ(); bz <= cube.maxZ(); bz++) {
                set(bx, FLOOR + 1, bz, Material.IRON_BLOCK, true);
                set(bx, FLOOR + 2, bz, side.glass(), true);
                protectedBlocks.add(CoresMap.key(bx, FLOOR + 3, bz));
            }
        }
        cores.add(new Core(side, left, set(x, FLOOR + 2, z(side, CORE_D), Material.BEACON, true)));
    }

    private void middle() {
        for (int x = -12; x <= 12; x++) {
            for (int z = -10; z <= 10; z++) {
                set(x, FLOOR, z, Material.GRASS_BLOCK, false);
                if (Math.abs(x) <= 11 && Math.abs(z) <= 9) {
                    set(x, FLOOR - 1, z, Material.DIRT, false);
                }
                if (Math.abs(x) <= 8 && Math.abs(z) <= 6) {
                    set(x, FLOOR - 2, z, rock(x, FLOOR - 2, z), false);
                }
                if (Math.abs(x) <= 4 && Math.abs(z) <= 3) {
                    set(x, FLOOR - 3, z, rock(x, FLOOR - 3, z), false);
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                boolean ring = Math.abs(x) == 2 || Math.abs(z) == 2;
                set(x, FLOOR + 1, z, ring ? Material.STONE_BRICKS : Material.DIAMOND_BLOCK, false);
            }
        }
        for (int x : new int[]{-6, 6}) {
            lootChests.add(set(x, FLOOR + 1, 0, Material.CHEST, false));
        }
    }

    /** A side island per team on one flank, the lane between them, and the paths to the base and to the middle. */
    private void flank(int sign) {
        int lane = sign * FLANK_X;
        line(lane - 1, lane + 1, -33, 33);
        line(sign * 13, lane - 2 * sign, -1, 1);
        for (var side : Side.values()) {
            for (int x = -6; x <= 6; x++) {
                for (int d = 34; d <= 46; d++) {
                    set(lane + x, FLOOR, z(side, d), Material.GRASS_BLOCK, false);
                    if (Math.abs(x) <= 4 && d >= 36 && d <= 44) {
                        set(lane + x, FLOOR - 1, z(side, d), Material.DIRT, false);
                    }
                    if (Math.abs(x) <= 2 && d >= 38 && d <= 42) {
                        set(lane + x, FLOOR - 2, z(side, d), rock(x, FLOOR - 2, d), false);
                    }
                }
            }
            lootChests.add(set(lane, FLOOR + 1, z(side, 40), Material.CHEST, false));
            fill(side, lane + 2 * sign, lane + 3 * sign, FLOOR + 1, FLOOR + 1, 42, 43, Material.GOLD_BLOCK, false);
            fill(side, sign * 13, lane + sign, FLOOR, FLOOR, 59, 61, Material.STONE_BRICKS, false);
            fill(side, lane - 1, lane + 1, FLOOR, FLOOR, 47, 58, Material.STONE_BRICKS, false);
        }
    }

    private Location lobby() {
        int y = 119;
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                boolean edge = Math.abs(x) == 7 || Math.abs(z) == 7;
                var floor = edge ? (z < 0 ? Side.RED : Side.BLUE).concrete() : Material.SMOOTH_QUARTZ;
                set(x, y, z, floor, true);
                if (edge) {
                    set(x, y + 1, z, Material.GLASS, true);
                    set(x, y + 2, z, Material.GLASS, true);
                }
            }
        }
        return new Location(world, 0.5, y + 1, 0.5);
    }

    private void fillLoot(Block chest) {
        ((Chest) chest.getState()).getBlockInventory().setContents(loot.open(chest.getLocation()).getContents());
    }

    private void line(int minX, int maxX, int minZ, int maxZ) {
        for (int x = Math.min(minX, maxX); x <= Math.max(minX, maxX); x++) {
            for (int z = minZ; z <= maxZ; z++) {
                set(x, FLOOR, z, Material.STONE_BRICKS, false);
            }
        }
    }

    private void fill(Side side, int x1, int x2, int y1, int y2, int d1, int d2, Material material, boolean base) {
        var box = box(side, x1, x2, y1, y2, d1, d2);
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    set(x, y, z, material, base);
                }
            }
        }
    }

    private static Box box(Side side, int x1, int x2, int y1, int y2, int d1, int d2) {
        int z1 = z(side, d1);
        int z2 = z(side, d2);
        return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
    }

    private Block set(int x, int y, int z, Material material, boolean base) {
        var block = world.getBlockAt(x, y, z);
        block.setType(material, false);
        if (base) {
            protectedBlocks.add(CoresMap.key(x, y, z));
        }
        return block;
    }

    private static int z(Side side, int d) {
        return side.direction() * d;
    }

    private static Material brick(int x, int d) {
        return switch (Math.floorMod(x * 31 + d * 17, 9)) {
            case 0 -> Material.MOSSY_STONE_BRICKS;
            case 1 -> Material.CRACKED_STONE_BRICKS;
            default -> Material.STONE_BRICKS;
        };
    }

    private static Material rock(int x, int y, int d) {
        return switch (Math.floorMod(x * 7 + y * 13 + d * 3, 5)) {
            case 0 -> Material.ANDESITE;
            case 1 -> Material.COBBLESTONE;
            default -> Material.STONE;
        };
    }
}
