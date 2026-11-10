package me.blockcraft.ttt.arena;

import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * A task of TTT 2 at one spot of the map, started with a right-click on its {@code station}. Its name, where it is
 * and what it carries are {@code tasks.chores.<key>.name}, {@code .where}, {@code .destination} and {@code .cargo}
 * in messages.yml. A
 * {@code visible} chore shows everyone near the station that it was done, which only an innocent's real chore does.
 */
public sealed interface Chore {

    String key();

    Block station();

    boolean visible();

    /** Stay near the station for a while after the click. */
    record Hold(String key, Block station, boolean visible) implements Chore {
    }

    /** Take {@code cargo} from the station to {@code destination} and right-click it there. */
    record Fetch(String key, Block station, Block destination, Material cargo, boolean visible) implements Chore {
    }

    /** Solve the short puzzle that opens at the station. */
    record Puzzle(String key, Block station, boolean visible) implements Chore {
    }
}
