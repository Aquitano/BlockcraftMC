package me.blockcraft.ttt.arena;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.function.UnaryOperator;

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

    /** The same chore on the blocks {@code to} maps its own to, as in a fresh copy of its map. */
    default Chore at(UnaryOperator<Block> to) {
        return switch (this) {
            case Hold hold -> new Hold(hold.key(), to.apply(hold.station()), hold.visible());
            case Fetch fetch -> new Fetch(fetch.key(), to.apply(fetch.station()), to.apply(fetch.destination()),
                    fetch.cargo(), fetch.visible());
            case Puzzle puzzle -> new Puzzle(puzzle.key(), puzzle.game(), to.apply(puzzle.station()), puzzle.visible());
        };
    }

    /** Stay near the station for a while after the click. */
    record Hold(String key, Block station, boolean visible) implements Chore {
    }

    /** Take {@code cargo} from the station to {@code destination} and right-click it there. */
    record Fetch(String key, Block station, Block destination, Material cargo, boolean visible) implements Chore {
    }

    /** Solve the short puzzle that opens at the station, the chore {@code game} of {@code task/game}. */
    record Puzzle(String key, String game, Block station, boolean visible) implements Chore {

        public Puzzle(String key, Block station, boolean visible) {
            this(key, key, station, visible);
        }
    }
}
