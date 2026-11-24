package me.blockcraft.api.world;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;

/** A spot on a map kept without its world, because the world is only loaded while someone needs it. */
public record Point(double x, double y, double z, float yaw, float pitch) {

    public static Point of(Location location) {
        return new Point(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location in(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public static Point parse(Map<?, ?> values) {
        return new Point(number(values, "x"), number(values, "y"), number(values, "z"),
                (float) optional(values, "yaw"), (float) optional(values, "pitch"));
    }

    public Map<String, Object> serialize() {
        var values = new LinkedHashMap<String, Object>();
        values.put("x", x);
        values.put("y", y);
        values.put("z", z);
        if (yaw != 0.0f) {
            values.put("yaw", (double) yaw);
        }
        if (pitch != 0.0f) {
            values.put("pitch", (double) pitch);
        }
        return values;
    }

    public static double number(Map<?, ?> values, String key) {
        if (values.get(key) instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException("missing number '" + key + "' in " + values);
    }

    private static double optional(Map<?, ?> values, String key) {
        return values.get(key) instanceof Number number ? number.doubleValue() : 0.0;
    }
}
