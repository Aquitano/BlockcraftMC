package me.blockcraft.api.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The waiting-lobby countdown every game shares: the remaining seconds are the players' XP level, the last
 * seconds tick audibly, and while fewer than {@code minPlayers} are there the clock stays at its full value
 * instead of running out.
 */
public final class LobbyCountdown {

    private static final Set<Integer> ANNOUNCE_AT = Set.of(60, 30, 15, 10, 5, 4, 3, 2, 1);
    private static final int AUDIBLE_FROM = 5;

    private final Countdown countdown;
    private final int seconds;
    private final int minPlayers;
    private final IntSupplier players;
    private final IntConsumer announce;
    private final Runnable onStart;
    private boolean forced;

    public LobbyCountdown(Plugin plugin, int seconds, int minPlayers, IntSupplier players, IntConsumer announce, Runnable onStart) {
        this.countdown = new Countdown(plugin);
        this.seconds = seconds;
        this.minPlayers = minPlayers;
        this.players = players;
        this.announce = announce;
        this.onStart = onStart;
    }

    /** Starts once enough players are there; calling it on every join is how a game keeps it going. */
    public void start() {
        if (players.getAsInt() >= minPlayers) {
            begin(false);
        }
    }

    /** Runs the clock down to {@code toSeconds} whatever the player count says; false if it is already there. */
    public boolean shorten(int toSeconds) {
        begin(true);
        forced = true;
        if (countdown.remaining() <= toSeconds) {
            return false;
        }
        countdown.remaining(toSeconds + 1);
        return true;
    }

    public boolean isRunning() {
        return countdown.isRunning();
    }

    public int remaining() {
        return countdown.remaining();
    }

    public void cancel() {
        countdown.cancel();
        Bukkit.getOnlinePlayers().forEach(player -> player.setLevel(0));
    }

    /**
     * The frozen seconds right after a round starts: a title counts them down for the players who are in the
     * round, then {@code onEnd} lets them move.
     */
    public static Countdown freeze(Plugin plugin, int seconds, Supplier<List<Player>> audience,
                                   IntFunction<Component> title, Runnable onEnd) {
        return Countdown.start(plugin, seconds, remaining -> {
            if (remaining > 0) {
                var shown = Title.title(title.apply(remaining), Component.empty());
                audience.get().forEach(player -> {
                    player.showTitle(shown);
                    Sounds.COUNTDOWN_TICK.play(player);
                });
            }
        }, () -> {
            audience.get().forEach(Sounds.COUNTDOWN_GO::play);
            onEnd.run();
        });
    }

    private void begin(boolean forced) {
        if (countdown.isRunning()) {
            return;
        }
        this.forced = forced;
        announce.accept(seconds);
        countdown.start(seconds, this::tick, this::finish);
    }

    private void tick(int remaining) {
        if (!forced && players.getAsInt() < minPlayers) {
            countdown.remaining(seconds);
            Bukkit.getOnlinePlayers().forEach(player -> player.setLevel(seconds));
            return;
        }
        Bukkit.getOnlinePlayers().forEach(player -> player.setLevel(remaining));
        if (ANNOUNCE_AT.contains(remaining)) {
            announce.accept(remaining);
        }
        if (remaining > 0 && remaining <= AUDIBLE_FROM) {
            Sounds.COUNTDOWN_TICK.playToAll();
        }
    }

    private void finish() {
        Sounds.COUNTDOWN_GO.playToAll();
        onStart.run();
    }
}
