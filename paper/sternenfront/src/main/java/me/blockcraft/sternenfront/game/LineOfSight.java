package me.blockcraft.sternenfront.game;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.util.Vector;

/** Whether a wall stands between a point and another {@code offset} away; fluids and passable blocks stop nothing. */
public final class LineOfSight {

    private LineOfSight() {
    }

    public static boolean clear(Location from, Vector offset) {
        double distance = offset.length();
        return distance == 0 || from.getWorld().rayTraceBlocks(from, offset.clone().normalize(), distance, FluidCollisionMode.NEVER, true) == null;
    }
}
