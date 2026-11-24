package me.blockcraft.ttt.task.game;

import me.blockcraft.api.item.Items;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.pack.MenuIcon;
import me.blockcraft.api.pack.UiFont;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Optional;

/**
 * A chore played in a chest menu at its station. The game calls {@link #solve} once the player won; closing the menu
 * before that leaves the chore open, to be started over at the station. While it is open the player sees nothing of
 * who comes near, which is the risk of doing it.
 * <p>
 * With the pack, the game is a picture of its station: {@code resourcepack/ChoreArt.java} draws the screen as the only
 * glyph of the font {@code blockcraft:chore/<key>} and each {@link #piece} as {@code items/chore/<key>/<part>.json}.
 * Without it, the pieces are the vanilla items they are built from.
 */
public abstract class ChoreGame extends Menu {

    private static final int OPENING_TICKS = 5;
    private static final char SCREEN = '\uE000';

    protected final Plugin plugin;
    protected final Messages messages;
    private final String key;
    private final Component title;
    private final Runnable solved;
    private boolean over;
    private int playedAt;

    protected ChoreGame(Plugin plugin, String key, int rows, Messages messages, Component title, Runnable solved) {
        super(rows, title);
        this.plugin = plugin;
        this.key = key;
        this.messages = messages;
        this.title = title;
        this.solved = solved;
        closeButton();
    }

    @Override
    protected final Optional<Component> screen() {
        return Optional.of(UiFont.overlaid(Key.key("blockcraft", "chore/" + key), SCREEN, title));
    }

    /** A {@code fallback} item that the pack draws as this game's {@code part}. */
    protected final Items.Builder piece(Material fallback, String part) {
        return MenuIcon.picture(Items.of(fallback), "chore/" + key + "/" + part);
    }

    /** Opens the game; {@link #started} runs once it shows. */
    public final void play(Player player) {
        playedAt = Bukkit.getCurrentTick();
        open(player, this::started);
    }

    /** Whether {@code player} is in this game or it is still opening for them, so that another click must not replace it. */
    public final boolean busy(Player player) {
        return !over && (player.getOpenInventory().getTopInventory().getHolder(false) == this
                || Bukkit.getCurrentTick() - playedAt < OPENING_TICKS);
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
