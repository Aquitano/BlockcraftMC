package me.blockcraft.zombies.game;

import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;

import java.util.List;

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

    /** Whether it dealt with the window's remaining {@code boards} itself this tick, instead of breaking one. */
    default boolean smash(Enemies.Enemy enemy, List<Block> boards, int tick) {
        return false;
    }

    /** Whether it moved on its own, so it does not run at the nearest fighter. */
    default boolean hunt(Enemies.Enemy enemy, Player target, int tick) {
        return false;
    }

    /** The damage a fighter's hit deals to it, to the head or to the body. */
    default double shot(Enemies.Enemy enemy, Player shooter, double damage, boolean head) {
        return damage;
    }

    default void struck(Enemies.Enemy enemy, Player player) { }

    default void died(Enemies.Enemy enemy) { }

    default void tick(int tick) { }

    default void clear() { }
}
