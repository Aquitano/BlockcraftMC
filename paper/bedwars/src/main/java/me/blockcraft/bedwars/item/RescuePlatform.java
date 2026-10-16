package me.blockcraft.bedwars.item;

import me.blockcraft.bedwars.team.TeamColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** A disc of the team's glass right under the feet of a player in mid-air. The glass breaks like any placed block. */
final class RescuePlatform {

    private final List<Vector> disc;
    private final Predicate<Block> replaceable;
    private final Consumer<Block> placed;

    RescuePlatform(double radius, Predicate<Block> replaceable, Consumer<Block> placed) {
        this.replaceable = replaceable;
        this.placed = placed;
        var offsets = new ArrayList<Vector>();
        for (int x = (int) Math.ceil(0.5 - radius); x < Math.floor(0.5 + radius); x++) {
            for (int z = (int) Math.ceil(0.5 - radius); z < Math.floor(0.5 + radius); z++) {
                if (x * x + z * z <= radius * radius) {
                    offsets.add(new Vector(x, -1, z));
                }
            }
        }
        this.disc = List.copyOf(offsets);
    }

    /** False when the player is not in the air. */
    boolean spread(Player player, TeamColor team) {
        var feet = player.getLocation().getBlock();
        if (!feet.getRelative(BlockFace.DOWN).isEmpty()) {
            return false;
        }
        for (var offset : disc) {
            var block = feet.getRelative(offset.getBlockX(), offset.getBlockY(), offset.getBlockZ());
            if (block.isEmpty() || replaceable.test(block)) {
                block.setType(team.glass());
                placed.accept(block);
            }
        }
        return true;
    }
}
