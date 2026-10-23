package me.blockcraft.api.world;

import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.generator.ChunkGenerator;

import java.util.Objects;
import java.util.function.Predicate;

public final class Worlds {

    private Worlds() {
    }

    public static World createArena(NamespacedKey key) {
        var world = Objects.requireNonNull(WorldCreator.ofKey(key)
                .generator(new ChunkGenerator() { }).createWorld(), "Could not create arena " + key);
        world.setAutoSave(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        setTime(world, 6000);
        world.setStorm(false);
        world.setThundering(false);
        return world;
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
