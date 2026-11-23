package me.blockcraft.zombies.game;

import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;

/** Draws enemies as their {@link CreatureBody} models. Without BetterModel, as on a bare test server, they stay bare mobs. */
interface Bodies {

    Bodies BARE = new Bodies() {
        @Override
        public boolean draw(Mob mob, String model, double scale) {
            return false;
        }

        @Override
        public void play(Mob mob, String animation) { }

        @Override
        public void clear() { }
    };

    static Bodies forServer() {
        return Bukkit.getPluginManager().isPluginEnabled("BetterModel") ? new BetterModelBodies() : BARE;
    }

    /** Whether the mob now wears the model; it does not when the model is missing. */
    boolean draw(Mob mob, String model, double scale);

    /** Plays one of the rig's clips once, such as {@code attack}, {@code death} or an ability's own clip. */
    void play(Mob mob, String animation);

    void clear();
}
