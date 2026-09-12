package me.blockcraft.api.world;

import org.bukkit.World;

/**
 * Paper 26.2 throws on setTime in worlds without a world clock (the nether). There is no public "has clock"
 * check, so isFixedTime stands in for it; the two coincide for the vanilla dimensions.
 */
public final class Worlds {

    private Worlds() {
    }

    public static void setTime(World world, long ticks) {
        if (!world.isFixedTime()) {
            world.setTime(ticks);
        }
    }

    public static void clearWeather(World world) {
        world.setThundering(false);
        world.setStorm(false);
    }
}
