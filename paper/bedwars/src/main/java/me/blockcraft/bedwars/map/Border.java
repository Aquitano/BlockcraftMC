package me.blockcraft.bedwars.map;

import java.util.LinkedHashMap;
import java.util.Map;

/** The columns players may build in, both corners included. */
public record Border(int minX, int maxX, int minZ, int maxZ) {

    public static Border between(int x1, int z1, int x2, int z2) {
        return new Border(Math.min(x1, x2), Math.max(x1, x2), Math.min(z1, z2), Math.max(z1, z2));
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    static Border parse(Map<?, ?> values) {
        return between((int) Point.number(values, "x1"), (int) Point.number(values, "z1"),
                (int) Point.number(values, "x2"), (int) Point.number(values, "z2"));
    }

    Map<String, Object> serialize() {
        var values = new LinkedHashMap<String, Object>();
        values.put("x1", minX);
        values.put("z1", minZ);
        values.put("x2", maxX);
        values.put("z2", maxZ);
        return values;
    }
}
