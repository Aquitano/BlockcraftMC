package me.blockcraft.sternenfront.game;

import me.blockcraft.api.scenery.FurnitureSolids;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Whether a wall stands between a point and another {@code offset} away; fluids and passable blocks stop nothing. */
public final class LineOfSight {

    private LineOfSight() {
    }

    public static boolean clear(Location from, Vector offset) {
        double distance = offset.length();
        return distance == 0 || FurnitureSolids.rayTrace(from, offset.clone().normalize(), distance) == null;
    }
}
