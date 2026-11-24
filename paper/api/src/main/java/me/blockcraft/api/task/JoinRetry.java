package me.blockcraft.api.task;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;

/** The pauses before a failed join-time read is tried again, so a database blip does not cost a whole session. */
public final class JoinRetry {

    private static final List<Long> DELAY_TICKS = List.of(40L, 100L, 300L);

    private JoinRetry() {
    }

    /** Schedules {@code retry} for while the player is still online; false once every pause was used up. */
    public static boolean after(Plugin plugin, Player player, int failures, Runnable retry) {
        if (failures >= DELAY_TICKS.size()) {
            return false;
        }
        Tasks.later(plugin, DELAY_TICKS.get(failures), () -> {
            if (player.isOnline()) {
                retry.run();
            }
        });
        return true;
    }
}
