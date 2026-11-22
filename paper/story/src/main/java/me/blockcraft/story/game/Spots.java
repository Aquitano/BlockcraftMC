package me.blockcraft.story.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/** Positions written as {@code "x y z"} or {@code "x y z yaw pitch"}, as the Sternenfront arena files write them. */
public final class Spots {

    private Spots() {
    }

    public static Location parse(World world, String text) {
        var parts = text.trim().split("\\s+");
        if (parts.length != 3 && parts.length != 5) {
            throw new IllegalArgumentException("Expected x y z [yaw pitch]: " + text);
        }
        var at = new Location(world, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
        if (parts.length == 5) {
            at.setYaw(Float.parseFloat(parts[3]));
            at.setPitch(Float.parseFloat(parts[4]));
        }
        return at;
    }

    public static Location at(World world, ConfigurationSection section, String key) {
        var text = section.getString(key);
        if (text == null) {
            throw new IllegalArgumentException("Missing position " + section.getCurrentPath() + "." + key);
        }
        return parse(world, text);
    }

    public static List<Location> all(World world, ConfigurationSection section, String key) {
        return section.getStringList(key).stream().map(text -> parse(world, text)).toList();
    }
}
