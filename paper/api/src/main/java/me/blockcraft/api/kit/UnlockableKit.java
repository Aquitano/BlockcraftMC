package me.blockcraft.api.kit;

import org.bukkit.Material;

/** A kit a player picks in a game's lobby; its name and lore are {@code kit.<key>.*} in the game's messages.yml. */
public interface UnlockableKit {

    String key();

    Material icon();

    /** What the bc_unlocks rows of all of this game's kits start with. */
    String itemPrefix();

    /** The row in bc_unlocks that says a player bought this kit. */
    default String item() {
        return itemPrefix() + key();
    }
}
