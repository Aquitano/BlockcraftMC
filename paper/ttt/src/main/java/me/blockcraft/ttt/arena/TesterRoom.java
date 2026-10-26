package me.blockcraft.ttt.arena;

import org.bukkit.block.Block;

import java.util.List;

/** The traitor tester: who stands in {@code zone} is tested when the {@code button} is pressed; the lamps show the result. */
public record TesterRoom(Box zone, Block button, List<Block> entrance, List<Block> lamps) {
}
