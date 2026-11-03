package me.blockcraft.sternenfront.arena;

import me.blockcraft.sternenfront.arena.FrontMap.PostSite;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * A command post's marks: a beacon on its iron base with white glass on top, whose beam the round tints with the
 * owner's colour, and a ring of white concrete in the ground along the edge of its zone.
 */
final class Posts {

    private static final int FLOOR = TessarMap.FLOOR;

    private final Canvas canvas;
    private final Terrain terrain;

    Posts(Canvas canvas, Terrain terrain) {
        this.canvas = canvas;
        this.terrain = terrain;
    }

    PostSite post(String key, int x, int z, double radius, List<Location> spawns) {
        var indicators = new ArrayList<Block>();
        canvas.fill(x - 1, FLOOR, z - 1, x + 1, FLOOR, z + 1, Material.IRON_BLOCK);
        canvas.set(x, FLOOR + 1, z, Material.BEACON);
        canvas.set(x, FLOOR + 2, z, Material.WHITE_STAINED_GLASS);
        indicators.add(canvas.world().getBlockAt(x, FLOOR + 2, z));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                    canvas.set(x + dx, FLOOR, z + dz, Material.POLISHED_BLACKSTONE);
                }
            }
        }
        for (int corner = 0; corner < 4; corner++) {
            int cx = x + (corner % 2 == 0 ? -2 : 2);
            int cz = z + (corner < 2 ? -2 : 2);
            canvas.set(cx, FLOOR + 1, cz, Material.POLISHED_BLACKSTONE_WALL);
            canvas.set(cx, FLOOR + 2, cz, "minecraft:end_rod[facing=up]");
        }
        int r = (int) Math.ceil(radius) + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(distance - radius) > 0.5) {
                    continue;
                }
                for (int y = FLOOR + 2; y >= FLOOR - 1; y--) {
                    if (canvas.type(x + dx, y, z + dz).isSolid() && canvas.isAir(x + dx, y + 1, z + dz)) {
                        canvas.set(x + dx, y, z + dz, Material.WHITE_CONCRETE);
                        indicators.add(canvas.world().getBlockAt(x + dx, y, z + dz));
                        break;
                    }
                }
            }
        }
        var world = canvas.world();
        return new PostSite(key, new Location(world, x + 0.5, FLOOR + 1, z + 0.5), radius, spawns, List.copyOf(indicators),
                new Location(world, x + 0.5, FLOOR + 6, z + 0.5), TacticalRoutes.build(canvas, terrain, key, z));
    }
}
