package me.blockcraft.replay.playback;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/** Plays recorded mobs with the models their game drew them with, on a server that has BetterModel. */
interface BodyModels {

    BodyModels NONE = new BodyModels() { };

    static BodyModels ofThisServer() {
        return Bukkit.getPluginManager().isPluginEnabled("BetterModel") ? new BetterModelBodies() : NONE;
    }

    default void draw(Entity entity) { }

    default void moved(Entity entity, boolean moved) { }

    default void fired(Entity entity) { }

    /** BetterModel plays the death animation and closes the model by itself. */
    default void died(Entity entity) { }

    /** BetterModel does not close a model whose mob is removed alive. */
    default void gone(Entity entity) { }

    default void hold() { }
}
