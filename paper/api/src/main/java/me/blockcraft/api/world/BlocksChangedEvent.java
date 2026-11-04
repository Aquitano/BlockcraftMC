package me.blockcraft.api.world;

import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collection;
import java.util.List;

/**
 * Fired on the main thread by a plugin that changed blocks itself, with {@code setBlockData} or {@code setType}:
 * Bukkit announces those to nobody, so whoever follows the world, such as the replay recorder, hears it here.
 */
public final class BlocksChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final List<Block> blocks;

    public BlocksChangedEvent(Collection<Block> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    public List<Block> blocks() {
        return blocks;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
