package me.blockcraft.api.world;

import org.bukkit.World;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;

import java.util.function.Predicate;

public final class Worlds {

    private Worlds() {
    }

    /**
     * Paper 26.2 throws on setTime in worlds without a world clock (the nether). There is no public "has clock"
     * check, so isFixedTime stands in for it; the two coincide for the vanilla dimensions.
     */
    public static void setTime(World world, long ticks) {
        if (!world.isFixedTime()) {
            world.setTime(ticks);
        }
    }

    public static void clearWeather(World world) {
        world.setThundering(false);
        world.setStorm(false);
    }

    /**
     * Removes what a round leaves behind - dropped items, projectiles, orbs, primed TNT, falling blocks, effect
     * clouds and mobs that vanilla would despawn anyway. Map decoration (paintings, displays, boats, end crystals,
     * named or AI-less mobs) is kept, as is anything {@code keep} claims.
     */
    public static void removeLeftovers(World world, Predicate<Entity> keep) {
        for (Entity entity : world.getEntities()) {
            if (isLeftover(entity) && !keep.test(entity)) {
                entity.remove();
            }
        }
    }

    private static boolean isLeftover(Entity entity) {
        return entity instanceof Item
                || entity instanceof Projectile
                || entity instanceof ExperienceOrb
                || entity instanceof TNTPrimed
                || entity instanceof FallingBlock
                || entity instanceof AreaEffectCloud
                || (entity instanceof Mob mob && mob.getRemoveWhenFarAway());
    }
}
