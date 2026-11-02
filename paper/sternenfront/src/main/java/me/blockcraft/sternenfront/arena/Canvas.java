package me.blockcraft.sternenfront.arena;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.block.data.type.Wall;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Places the map's blocks without physics, so nothing falls, flows or reconnects while the map goes up. Fences,
 * panes, bars and walls therefore remember where they stand and are joined to their neighbours at the end.
 */
final class Canvas {

    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final World world;
    private final Map<Material, BlockData> plain = new EnumMap<>(Material.class);
    private final Map<String, BlockData> parsed = new HashMap<>();
    private final List<long[]> joints = new ArrayList<>();

    Canvas(World world) {
        this.world = world;
    }

    World world() {
        return world;
    }

    void set(int x, int y, int z, Material material) {
        set(x, y, z, plain.computeIfAbsent(material, Material::createBlockData));
    }

    void set(int x, int y, int z, String data) {
        set(x, y, z, parsed.computeIfAbsent(data, Bukkit::createBlockData));
    }

    void set(int x, int y, int z, BlockData data) {
        var block = world.getBlockAt(x, y, z);
        if (block.getBlockData().equals(data)) {
            return;
        }
        block.setBlockData(data, false);
        if (data instanceof Fence || data instanceof GlassPane || data instanceof Wall) {
            joints.add(new long[]{x, y, z});
        }
    }

    void setIfAir(int x, int y, int z, Material material) {
        if (isAir(x, y, z)) {
            set(x, y, z, material);
        }
    }

    void clear(int x, int y, int z) {
        set(x, y, z, Material.AIR);
    }

    boolean isAir(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType().isAir();
    }

    Material type(int x, int y, int z) {
        return world.getBlockAt(x, y, z).getType();
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, material);
                }
            }
        }
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, String data) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, data);
                }
            }
        }
    }

    /** A vertical cylinder; {@code hollow} keeps only the ring one block thick. */
    void cylinder(int cx, int cz, int y1, int y2, double radius, Material material, boolean hollow) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= radius && (!hollow || d > radius - 1.2)) {
                    fill(cx + dx, y1, cz + dz, cx + dx, y2, cz + dz, material);
                }
            }
        }
    }

    /** The upper half of a sphere standing on {@code y}; {@code hollow} keeps a shell one block thick. */
    void dome(int cx, int y, int cz, double radius, Material material, boolean hollow) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= r; dy++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d <= radius && (!hollow || d > radius - 1.2)) {
                        set(cx + dx, y + dy, cz + dz, material);
                    }
                }
            }
        }
    }

    /** A line of blocks between two points, one block per step along the longest axis. */
    void line(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        int steps = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            set((int) Math.round(x1 + (x2 - x1) * t), (int) Math.round(y1 + (y2 - y1) * t),
                    (int) Math.round(z1 + (z2 - z1) * t), material);
        }
    }

    /** Joins every fence, pane, bar and wall placed so far to the neighbours it would join in the game. */
    void join() {
        for (var at : joints) {
            var block = world.getBlockAt((int) at[0], (int) at[1], (int) at[2]);
            var data = block.getBlockData();
            if (data instanceof Wall wall) {
                var joined = new boolean[SIDES.length];
                for (int i = 0; i < SIDES.length; i++) {
                    joined[i] = joins(wall, block.getRelative(SIDES[i]).getBlockData());
                    wall.setHeight(SIDES[i], joined[i] ? Wall.Height.LOW : Wall.Height.NONE);
                }
                boolean northSouth = joined[0] && joined[2] && !joined[1] && !joined[3];
                boolean eastWest = joined[1] && joined[3] && !joined[0] && !joined[2];
                wall.setUp(!(northSouth || eastWest) || !block.getRelative(BlockFace.UP).getType().isAir());
                block.setBlockData(wall, false);
            } else if (data instanceof MultipleFacing facing) {
                for (var face : SIDES) {
                    facing.setFace(face, joins(data, block.getRelative(face).getBlockData()));
                }
                block.setBlockData(facing, false);
            }
        }
        joints.clear();
    }

    private static boolean joins(BlockData self, BlockData other) {
        var material = other.getMaterial();
        if (material.isOccluding()) {
            return true;
        }
        return switch (self) {
            case Fence _ -> other instanceof Fence;
            case Wall _ -> other instanceof Wall;
            default -> other instanceof GlassPane || material.name().endsWith("GLASS");
        };
    }
}
