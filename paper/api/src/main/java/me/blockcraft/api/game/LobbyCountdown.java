package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * The waiting-lobby countdown every game shares: the {@link Arena} announces it in chat, the remaining seconds are
 * the players' XP level, the last five count down as a title and tick a note higher each second, and while fewer
 * than {@code minPlayers} are there the clock stays at its full value instead of running out.
 */
public final class LobbyCountdown {

    private static final Set<Integer> ANNOUNCE_AT = Set.of(60, 30, 15, 10, 5, 4, 3, 2, 1);
    private static final int AUDIBLE_FROM = 5;
    private static final Title.Times SECOND_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250));
    private static final Title.Times GO_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300));

    private final Countdown countdown;
    private final Arena arena;
    private final int seconds;
    private final int minPlayers;
    private final IntSupplier players;
    private final Runnable onStart;
    private boolean forced;

    public LobbyCountdown(Plugin plugin, Arena arena, int seconds, int minPlayers, IntSupplier players, Runnable onStart) {
        this.countdown = new Countdown(plugin);
        this.arena = arena;
        this.seconds = seconds;
        this.minPlayers = minPlayers;
        this.players = players;
        this.onStart = onStart;
    }

    /** Starts once enough players are there; calling it on every join is how a game keeps it going. */
    public void start() {
        if (players.getAsInt() >= minPlayers) {
            begin();
        }
    }

    /** Runs the clock down to {@code toSeconds} whatever the player count says; false if it is already there. */
    public boolean shorten(int toSeconds) {
        begin();
        if (countdown.remaining() <= toSeconds) {
            return false;
        }
        forced = true;
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
        forced = false;
        countdown.cancel();
        Bukkit.getOnlinePlayers().forEach(player -> player.setLevel(0));
    }

    /**
     * The frozen seconds right after a round starts: a title counts them down for the players who are in the
     * round, then {@code onEnd} lets them move.
     */
    public static Countdown freeze(Plugin plugin, int seconds, Supplier<List<Player>> audience,
                                   IntFunction<Component> title, Runnable onEnd) {
        showSecond(audience.get(), title, seconds);
        return Countdown.start(plugin, seconds, remaining -> {
            if (remaining > 0) {
                showSecond(audience.get(), title, remaining);
            }
        }, () -> {
            var go = goTitle();
            audience.get().forEach(player -> {
                player.showTitle(go);
                Sounds.COUNTDOWN_GO.play(player);
            });
            onEnd.run();
        });
    }

    private static void showSecond(List<Player> audience, IntFunction<Component> title, int remaining) {
        var shown = Title.title(title.apply(remaining), Component.empty(), SECOND_TIMES);
        audience.forEach(player -> {
            player.showTitle(shown);
            Sounds.countdownTick(player, remaining);
        });
    }

    private static Title goTitle() {
        return Title.title(BlockcraftApi.get().messages().get("countdown.go"), Component.empty(), GO_TIMES);
    }

    private void begin() {
        if (countdown.isRunning()) {
            return;
        }
        forced = false;
        arena.announceStart(seconds);
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
            arena.announceStart(remaining);
        }
        if (remaining > 0 && remaining <= AUDIBLE_FROM) {
            var title = Title.title(BlockcraftApi.get().messages().get("countdown.seconds", Formatter.number("seconds", remaining)),
                    Component.empty(), SECOND_TIMES);
            Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(title));
            Sounds.countdownTick(remaining);
        }
    }

    private void finish() {
        var go = goTitle();
        Bukkit.getOnlinePlayers().forEach(player -> player.showTitle(go));
        Sounds.COUNTDOWN_GO.playToAll();
        onStart.run();
    }
}
