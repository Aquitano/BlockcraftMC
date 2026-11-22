package me.blockcraft.sternenfront.game;

import io.papermc.paper.math.Position;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The blocks a held trigger breaks. A client repeats a held left click only while it breaks a block, so a player with
 * an automatic blaster is told of unlit light blocks in the air around their eyes, which only they have. A light block
 * has a shape only for whoever holds a light item, which a player's blaster is; it has no collision, draws nothing,
 * cannot be broken and lights nothing at level 0. With the eyes inside one, every look meets it first, so the click
 * never reaches a fighter or a real block.
 * <p>
 * The cells next to the eyes' own are there before the player walks into them, because the client moves ahead of
 * what the server hears of it. Nothing is shown while the eyes are inside a real block's cell: the look would then
 * meet a neighbouring cell from outside, where the client draws its outline.
 */
public final class TriggerCells {

    public static final Material ITEM = Material.LIGHT;
    /** What the client plays, to itself alone, each time it begins breaking a cell. */
    public static final Sound HIT = Sound.BLOCK_STONE_HIT;
    private static final BlockData CELL = Material.LIGHT.createBlockData(data -> ((Light) data).setLevel(0));
    /** The server may change a block under a cell, and its update replaces the cell on the client. */
    private static final int REFRESH_TICKS = 20;

    private record Shown(Block centre, Set<Block> cells) { }

    private final Map<UUID, Shown> shown = new HashMap<>();

    public void show(Player player, long tick) {
        var centre = player.getEyeLocation().getBlock();
        var before = shown.get(player.getUniqueId());
        if (before != null && before.centre().equals(centre) && tick % REFRESH_TICKS != 0) {
            return;
        }
        if (!centre.getType().isAir()) {
            hide(player);
            return;
        }
        var cells = new HashSet<Block>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    var cell = centre.getRelative(x, y, z);
                    if (cell.getType().isAir()) {
                        cells.add(cell);
                    }
                }
            }
        }
        var changes = new HashMap<Position, BlockData>();
        if (before != null) {
            before.cells().stream().filter(cell -> !cells.contains(cell)).forEach(cell -> changes.put(at(cell), cell.getBlockData()));
        }
        cells.forEach(cell -> changes.put(at(cell), CELL));
        player.sendMultiBlockChange(changes);
        shown.put(player.getUniqueId(), new Shown(centre, cells));
    }

    public void hide(Player player) {
        var before = shown.remove(player.getUniqueId());
        if (before != null) {
            var changes = new HashMap<Position, BlockData>();
            before.cells().forEach(cell -> changes.put(at(cell), cell.getBlockData()));
            player.sendMultiBlockChange(changes);
        }
    }

    public boolean covers(Player player, Block block) {
        var cells = shown.get(player.getUniqueId());
        return cells != null && cells.cells().contains(block);
    }

    public void forget(UUID player) {
        shown.remove(player);
    }

    public void clear() {
        shown.clear();
    }

    private static Position at(Block cell) {
        return Position.block(cell.getX(), cell.getY(), cell.getZ());
    }
}
