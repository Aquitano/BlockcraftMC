package me.blockcraft.ttt.arena;

import org.bukkit.block.Block;

import java.util.List;
import java.util.Optional;

/**
 * The traitor tester: who stands in {@code zone} is tested when the {@code button} is pressed; the lamps show the
 * result. A map may give it a {@link Trap}.
 */
public record TesterRoom(Box zone, Block button, List<Block> entrance, List<Block> lamps, Optional<Trap> trap) {

    /** The traitor trap: its hidden {@code button} drops the room's {@code floor} into the pit below for a moment. */
    public record Trap(Block button, List<Block> floor) {
    }
}
