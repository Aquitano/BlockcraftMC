package me.blockcraft.ttt.task.game;

import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * A chore played in a chest menu at its station. The game calls {@link #solve} once the player won; closing the menu
 * before that leaves the chore open, to be started over at the station. While it is open the player sees nothing of
 * who comes near, which is the risk of doing it.
 */
public abstract class ChoreGame extends Menu {

    protected final Plugin plugin;
    protected final Messages messages;
    private final Runnable solved;
    private boolean over;

    protected ChoreGame(Plugin plugin, int rows, Messages messages, Component title, Runnable solved) {
        super(rows, title);
        this.plugin = plugin;
        this.messages = messages;
        this.solved = solved;
        closeButton();
    }

    /** Opens the game; {@link #started} runs once it shows. */
    public final void play(Player player) {
        open(player, this::started);
    }

    /** Where a game that moves on its own starts moving, now that {@code player} sees it. */
    protected void started(Player player) {
    }

    /** Ends the game won: the menu closes and the chore is done, once however often it is called. */
    protected final void solve(Player player) {
        if (over) {
            return;
        }
        over = true;
        close(player);
        solved.run();
    }

    protected final boolean over() {
        return over;
    }

    /** Runs {@code step} every {@code ticks} while {@code player} has this game open and it is not won. */
    protected final void repeat(Player player, long ticks, Runnable step) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (over || !player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) != ChoreGame.this) {
                    cancel();
                    return;
                }
                step.run();
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }
}
