package me.blockcraft.sternenfront.arena;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Random;

/**
 * The temple ruin cut into the northern cliff behind the Raffinerie: one stair up from the yard to a terrace, a
 * pillared front under a broken pediment, and a hall in the rock whose only light falls through a shaft onto the
 * altar where the relic blade appears. On the centre line, so it lies as far from one headquarters as the other.
 */
final class TempleRuin {

    private static final int FLOOR = TessarMap.FLOOR;
    private static final int TERRACE = FLOOR + 3;
    private static final int FRONT = -58;
    private static final int BACK = -68;
    private static final int ALTAR_Z = -65;

    private final Canvas canvas;
    private final Random random;

    TempleRuin(Canvas canvas, Random random) {
        this.canvas = canvas;
        this.random = random;
    }

    /** Where the relic floats: 1.2 blocks over the altar's top. */
    Location relic(World world) {
        return new Location(world, 0.5, TERRACE + 3.2, ALTAR_Z + 0.5);
    }

    void build() {
        stairs();
        hall();
        front();
        altar();
    }

    private void stairs() {
        for (int x = -7; x <= 7; x++) {
            for (int z = FRONT; z <= -56; z++) {
                canvas.fill(x, FLOOR + 1, z, x, TERRACE - 1, z, Material.SANDSTONE);
                canvas.set(x, TERRACE, z, Math.abs(x) <= 1 ? Material.CHISELED_SANDSTONE : Material.SMOOTH_SANDSTONE);
                canvas.fill(x, TERRACE + 1, z, x, TERRACE + 4, z, Material.AIR);
            }
            if (Math.abs(x) >= 4) {
                canvas.set(x, TERRACE + 1, -56, Material.SANDSTONE_WALL);
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int step = 0; step < 3; step++) {
                int z = -53 - step;
                canvas.fill(x, FLOOR + 1, z, x, FLOOR + step, z, Material.SANDSTONE);
                canvas.set(x, FLOOR + 1 + step, z, "minecraft:sandstone_stairs[facing=north]");
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int step = 0; step < 3; step++) {
                int z = -53 - step;
                canvas.fill(side * 4, FLOOR + 1, z, side * 4, FLOOR + 1 + step, z, Material.CUT_SANDSTONE);
                canvas.set(side * 4, FLOOR + 2 + step, z, Material.SANDSTONE_WALL);
            }
        }
    }

    private void hall() {
        for (int x = -6; x <= 6; x++) {
            for (int z = BACK; z < FRONT; z++) {
                boolean border = Math.abs(x) == 6 || z == BACK;
                canvas.set(x, TERRACE, z, x == 0 ? Material.CHISELED_SANDSTONE : border ? Material.CUT_RED_SANDSTONE : Material.SMOOTH_SANDSTONE);
                canvas.fill(x, TERRACE + 1, z, x, TERRACE + 8, z, Material.AIR);
                canvas.set(x, TERRACE + 9, z, z % 3 == 0 ? Material.CUT_RED_SANDSTONE : Material.SMOOTH_SANDSTONE);
            }
        }
        for (int z = -61; z >= BACK + 1; z -= 3) {
            for (int side = -1; side <= 1; side += 2) {
                canvas.fill(side * 6, TERRACE + 1, z, side * 6, TERRACE + 8, z, Material.CUT_SANDSTONE);
                canvas.set(side * 6, TERRACE + 1, z, Material.CHISELED_SANDSTONE);
                canvas.set(side * 6, TERRACE + 8, z, Material.CHISELED_RED_SANDSTONE);
                canvas.set(side * 5, TERRACE + 8, z, "minecraft:lantern[hanging=true]");
            }
        }
        for (int side = -1; side <= 1; side += 2) {
            for (int z : new int[]{-63, -66}) {
                canvas.fill(side * 7, TERRACE + 1, z - 1, side * 7, TERRACE + 3, z, Material.AIR);
                canvas.set(side * 7, TERRACE + 1, z, Material.CHISELED_SANDSTONE);
                canvas.set(side * 7, TERRACE + 2, z, Material.DECORATED_POT);
                canvas.set(side * 7, TERRACE + 1, z - 1, "minecraft:candle[candles=3,lit=true]");
            }
        }
        canvas.set(-5, TERRACE + 9, -66, Material.COBWEB);
        canvas.set(5, TERRACE + 9, -60, Material.COBWEB);
        canvas.set(4, TERRACE + 1, -60, Material.SANDSTONE_SLAB);
        canvas.set(-4, TERRACE + 1, -67, Material.CUT_SANDSTONE_SLAB);
        for (int x = -1; x <= 1; x++) {
            for (int z = ALTAR_Z - 1; z <= ALTAR_Z + 1; z++) {
                canvas.fill(x, TERRACE + 9, z, x, FLOOR + 48, z, Material.AIR);
            }
        }
    }

    private void front() {
        for (int x : new int[]{-5, -2, 2, 5}) {
            boolean broken = x == 5;
            int top = broken ? TERRACE + 5 : TERRACE + 8;
            canvas.set(x, TERRACE + 1, FRONT, Material.CHISELED_SANDSTONE);
            canvas.fill(x, TERRACE + 2, FRONT, x, top - 1, FRONT, Material.CUT_SANDSTONE);
            canvas.set(x, top, FRONT, broken ? Material.SANDSTONE_SLAB : Material.CHISELED_SANDSTONE);
        }
        for (int x = -7; x <= 7; x++) {
            if (x >= 4 && random.nextInt(3) > 0) {
                continue;
            }
            canvas.set(x, TERRACE + 9, FRONT, Math.floorMod(x, 2) == 0 ? Material.CHISELED_RED_SANDSTONE : Material.CUT_RED_SANDSTONE);
        }
        for (int row = 1; row <= 4; row++) {
            int half = 7 - row * 2;
            for (int x = -half; x <= half; x++) {
                if (x > 1 && row > 1) {
                    continue;
                }
                canvas.set(x, TERRACE + 9 + row, FRONT, row == 2 && x == 0 ? Material.GOLD_BLOCK : Material.SMOOTH_RED_SANDSTONE);
            }
        }
        canvas.fill(4, TERRACE + 1, -57, 6, TERRACE + 1, -57, "minecraft:cut_sandstone_slab[type=bottom]");
        canvas.set(6, TERRACE + 1, -56, Material.CHISELED_SANDSTONE);
    }

    private void altar() {
        for (int x = -1; x <= 1; x++) {
            for (int z = ALTAR_Z - 1; z <= ALTAR_Z + 1; z++) {
                canvas.set(x, TERRACE + 1, z, x == 0 && z == ALTAR_Z ? Material.GOLD_BLOCK : Material.CHISELED_RED_SANDSTONE);
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int z = ALTAR_Z - 2; z <= ALTAR_Z + 2; z++) {
                if (Math.max(Math.abs(x), Math.abs(z - ALTAR_Z)) == 2) {
                    canvas.set(x, TERRACE, z, Material.RED_SANDSTONE);
                }
            }
        }
        for (int x : new int[]{-3, 3}) {
            for (int z : new int[]{ALTAR_Z - 2, ALTAR_Z + 2}) {
                canvas.set(x, TERRACE + 1, z, Material.SANDSTONE_WALL);
                canvas.set(x, TERRACE + 2, z, "minecraft:candle[candles=4,lit=true]");
            }
        }
    }
}
