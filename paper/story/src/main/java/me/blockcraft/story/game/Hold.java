package me.blockcraft.story.game;

import me.blockcraft.story.quest.HoldObjective;
import org.betonquest.betonquest.api.QuestException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

/**
 * A spot the party holds together. Its time counts only while a member stands there, so the party cannot wait it out
 * elsewhere; when it is through, the objective completes for every member.
 */
final class Hold implements AutoCloseable {

    private final Plugin plugin;
    private final Supplier<List<Player>> members;
    private BukkitTask task;
    private int held;
    private int total;

    Hold(Plugin plugin, Supplier<List<Player>> members) {
        this.plugin = plugin;
        this.members = members;
    }

    boolean running() {
        return task != null;
    }

    double progress() {
        return total == 0 ? 0 : Math.min(1, (double) held / total);
    }

    /** Brings the hold to its end at once, as {@code /story skip} does. */
    void finishNow() {
        if (task != null) held = total;
    }

    /** Starts the hold; false when one already runs or the objective's settings do not parse. */
    boolean start(HoldObjective objective, DoubleConsumer progress, Runnable done) {
        if (task != null) return false;
        try {
            total = Math.max(20, objective.seconds() * 20);
            held = 0;
        } catch (QuestException invalid) {
            plugin.getSLF4JLogger().warn("The hold objective is misconfigured", invalid);
            return false;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (members.get().stream().anyMatch(member -> holding(objective, member))) held++;
            progress.accept(progress());
            objective.progress(held * 100 / total);
            if (held >= total) {
                close();
                done.run();
                objective.completeFor(members.get());
            }
        }, 1, 1);
        return true;
    }

    private static boolean holding(HoldObjective objective, Player player) {
        try {
            return objective.holding(player);
        } catch (QuestException invalid) {
            return false;
        }
    }

    @Override
    public void close() {
        if (task != null) task.cancel();
        task = null;
    }
}
