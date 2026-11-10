package me.blockcraft.ttt.arena;

import org.bukkit.block.Block;

import java.util.List;

/**
 * What a map offers TTT 2: its chores, the wooden {@code doors} a sabotage shuts and locks (each by its lower half),
 * the {@code lights} a blackout puts out (lit blocks go unlit, lanterns and torches go dark until it ends) and the
 * {@code generator} where a blackout is mended.
 */
public record TaskLayout(List<Chore> chores, List<Block> doors, List<Block> lights, Block generator) {

    public TaskLayout {
        chores = List.copyOf(chores);
        doors = List.copyOf(doors);
        lights = List.copyOf(lights);
    }
}
