package me.blockcraft.bedwars.map;

import me.blockcraft.api.world.Point;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Where a team's bed stands: its foot block, and the side its head lies on. */
public record BedSpot(int x, int y, int z, BlockFace facing) {

    public Block foot(World world) {
        return world.getBlockAt(x, y, z);
    }

    static BedSpot parse(Map<?, ?> values) {
        if (!(values.get("facing") instanceof String facing)) {
            throw new IllegalArgumentException("missing 'facing' in " + values);
        }
        return new BedSpot((int) Point.number(values, "x"), (int) Point.number(values, "y"), (int) Point.number(values, "z"),
                BlockFace.valueOf(facing.toUpperCase(Locale.ROOT)));
    }

    Map<String, Object> serialize() {
        var values = new LinkedHashMap<String, Object>();
        values.put("x", x);
        values.put("y", y);
        values.put("z", z);
        values.put("facing", facing.name().toLowerCase(Locale.ROOT));
        return values;
    }
}
