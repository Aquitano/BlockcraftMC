package me.blockcraft.signs.sign;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;

/** A sign that shows one server group and sends clicking players to it. */
public record StatusSign(Location location, String group) {

    /** Standing and wall signs; the renderer paints the block they rest on, which a hanging sign does not have. */
    public static boolean isSignType(Material type) {
        return Tag.STANDING_SIGNS.isTagged(type) || Tag.WALL_SIGNS.isTagged(type);
    }
}
