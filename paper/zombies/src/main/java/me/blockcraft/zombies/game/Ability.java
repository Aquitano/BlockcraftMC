package me.blockcraft.zombies.game;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;

/** What a special kind does beyond chasing and biting. {@link Enemies} calls each hook for the enemy's kind. */
interface Ability {

    Ability NONE = new Ability() { };

    default void equip(Mob mob, EntityEquipment equipment) { }

    /** How many of the kind come through a window at once, each one enemy of the wave. */
    default int pack() {
        return 1;
    }

    /** Whether it stands still this tick, neither breaking boards nor hunting. */
    default boolean holds(Enemies.Enemy enemy) {
        return false;
    }

    /** Whether it moved on its own, so it does not run at the nearest fighter. */
    default boolean hunt(Enemies.Enemy enemy, Player target, int tick) {
        return false;
    }

    /** The damage a fighter's hit deals to it. */
    default double shot(Enemies.Enemy enemy, Player shooter, double damage) {
        return damage;
    }

    default void struck(Enemies.Enemy enemy, Player player) { }

    default void died(Enemies.Enemy enemy) { }

    default void tick(int tick) { }

    default void clear() { }
}
