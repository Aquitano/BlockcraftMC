package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * The waiting-lobby countdown every game shares: the {@link Arena} announces it in chat, the remaining seconds are
 * shown in a boss bar, the last five count down as a title and tick a note higher each second, and while fewer
 * than {@code players.min} are there the clock stays at its full value instead of running out. /start runs it down
 * to {@code countdown.start} seconds regardless, but a round never starts with fewer than {@link #ROUND_MINIMUM}.
 */
public final class LobbyCountdown implements Listener {

    /** Competitive rounds need two players; cooperative games can opt into a lower minimum. */
    public static final int ROUND_MINIMUM = 2;

    private static final Set<Integer> ANNOUNCE_AT = Set.of(60, 30, 15, 10, 5, 4, 3, 2, 1);
    private static final int AUDIBLE_FROM = 5;
    private static final Title.Times SECOND_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250));

    private final Plugin plugin;
    private final Countdown countdown;
    private final Round round;
    private final int seconds;
    private final int minPlayers;
    private final int roundMinimum;
    private final int startSeconds;
    private final IntConsumer onSecond;
    private final Runnable onStart;
    private boolean forced;
    private BossBar bar;

    public LobbyCountdown(Plugin plugin, Round round, GameConfig config, Runnable onStart) {
        this(plugin, round, config, remaining -> { }, onStart);
    }

    /** {@code onSecond} hears every second the clock actually runs down, not the ones it waits at full. */
    public LobbyCountdown(Plugin plugin, Round round, GameConfig config, IntConsumer onSecond, Runnable onStart) {
        this(plugin, round, config, ROUND_MINIMUM, onSecond, onStart);
    }

    public LobbyCountdown(Plugin plugin, Round round, GameConfig config, int roundMinimum, IntConsumer onSecond, Runnable onStart) {
        this.countdown = new Countdown(plugin);
        this.round = round;
        this.seconds = config.lobbySeconds();
        this.minPlayers = config.minPlayers();
        this.roundMinimum = Math.max(1, roundMinimum);
        this.startSeconds = config.startSeconds();
        this.onSecond = onSecond;
        this.onStart = onStart;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Starts once enough players are there; calling it on every join is how a game keeps it going. */
    public void start() {
        updateBar(countdown.isRunning() ? countdown.remaining() : seconds);
        if (round.playerCount() >= minPlayers) {
            begin();
        }
    }

    /** /start: the clock runs down to {@code countdown.start} seconds, and the requester hears whether it did. */
    public void forceStart(Player requester) {
        String outcome;
        if (round.phase() != GamePhase.LOBBY) {
            outcome = "start.running";
        } else if (round.playerCount() < roundMinimum) {
            outcome = "start.too-few";
        } else if (shorten(startSeconds)) {
            outcome = "start.shortened";
        } else {
            outcome = "start.too-late";
        }
        round.arena().tell(requester, outcome);
    }

    /** Runs the clock down to {@code toSeconds} whatever the player count says; false if it is already there. */
    public boolean shorten(int toSeconds) {
        if (countdown.isRunning() && countdown.remaining() <= toSeconds) {
            return false;
        }
        forced = true;
        updateBar(toSeconds);
        if (countdown.isRunning()) {
            countdown.remaining(toSeconds + 1);
        } else {
            countdown.start(toSeconds, this::tick, this::finish);
        }
        return true;
    }

    /** The round counts the leaver out during the quit; the waiting bar shows the new count a tick later. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (bar != null && !countdown.isRunning()) {
            Tasks.sync(plugin, () -> updateBar(seconds));
        }
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
        hideBar();
    }

    private void begin() {
        if (countdown.isRunning()) {
            return;
        }
        forced = false;
        updateBar(seconds);
        round.arena().announceStart(seconds);
        countdown.start(seconds, this::tick, this::finish);
    }

    private void tick(int remaining) {
        if (!forced && round.playerCount() < minPlayers) {
            countdown.remaining(seconds);
            updateBar(seconds);
            return;
        }
        updateBar(remaining);
        onSecond.accept(remaining);
        if (ANNOUNCE_AT.contains(remaining)) {
            round.arena().announceStart(remaining);
        }
        if (remaining > 0 && remaining <= AUDIBLE_FROM) {
            var title = Title.title(BlockcraftApi.get().messages().get("countdown.seconds", Formatter.number("seconds", remaining)),
                    Component.empty(), SECOND_TIMES);
            Bukkit.getOnlinePlayers().forEach(player -> BlockcraftApi.get().notifications().important(player, title));
            Sounds.countdownTick(remaining);
        }
    }

    private void updateBar(int remaining) {
        var messages = BlockcraftApi.get().messages();
        int missing = Math.max(0, minPlayers - round.playerCount());
        boolean waiting = !forced && missing > 0;
        var label = waiting
                ? messages.get("countdown.waiting", Formatter.number("players", missing))
                : messages.get("countdown.starting", Formatter.number("seconds", remaining));
        if (bar == null) {
            bar = BossBar.bossBar(label, 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        }
        bar.name(label).progress(waiting ? 1.0f : Math.clamp(remaining / (float) Math.max(seconds, 1), 0.0f, 1.0f));
        Bukkit.getServer().showBossBar(bar);
    }

    private void hideBar() {
        if (bar != null) {
            Bukkit.getServer().hideBossBar(bar);
            bar = null;
        }
    }

    /** A forced countdown may run out after players left; below the round's minimum it waits again. */
    private void finish() {
        forced = false;
        hideBar();
        if (round.playerCount() < roundMinimum) {
            round.arena().announceCancelled();
            start();
            return;
        }
        onStart.run();
    }
}
