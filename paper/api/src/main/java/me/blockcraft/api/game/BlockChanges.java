package me.blockcraft.api.game;

import me.blockcraft.api.world.BlocksChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Blocks a game sets itself, without physics, announced through {@link BlocksChangedEvent} so that whoever follows
 * the world, such as the replay recorder, sees them too.
 */
public final class BlockChanges {

    private BlockChanges() {
    }

    public static void set(Block block, BlockData data) {
        block.setBlockData(data, false);
        announce(List.of(block));
    }

    public static void set(Block block, Material type) {
        set(List.of(block), type);
    }

    public static void set(Collection<Block> blocks, Material type) {
        blocks.forEach(block -> block.setType(type, false));
        announce(blocks);
    }

    public static void set(Map<Block, BlockData> blocks) {
        blocks.forEach((block, data) -> block.setBlockData(data, false));
        announce(blocks.keySet());
    }

    /** For blocks changed some other way, such as a {@code BlockState} put back. */
    public static void announce(Collection<Block> blocks) {
        if (!blocks.isEmpty()) {
            Bukkit.getPluginManager().callEvent(new BlocksChangedEvent(blocks));
        }
    }
}
