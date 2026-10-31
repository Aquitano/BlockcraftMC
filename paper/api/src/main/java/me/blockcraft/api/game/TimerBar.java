package me.blockcraft.api.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * A boss bar over one timed phase of a round: the label says what is running out, the bar empties while it does.
 * The label is a message key of the game, which gets the remaining seconds as {@code <seconds>} and as
 * {@code <time>} in minutes and seconds. {@link Round#clock} shows it, unless the game turned timer bars off.
 */
public final class TimerBar {

    private static final BossBar.Color COLOR = BossBar.Color.YELLOW;
    private static final BossBar.Overlay OVERLAY = BossBar.Overlay.NOTCHED_10;
    private static final int SECONDS_PER_MINUTE = 60;

    private final Messages messages;
    private final String key;
    private final int seconds;
    private final BossBar bar;

    public TimerBar(Messages messages, String key, int seconds) {
        this.messages = messages;
        this.key = key;
        this.seconds = Math.max(seconds, 1);
        this.bar = BossBar.bossBar(label(seconds), BossBar.MAX_PROGRESS, COLOR, OVERLAY);
    }

    public int seconds() {
        return seconds;
    }

    /** Shown to everyone online again on every second, so a spectator who joins mid-phase sees it too. */
    public void update(int remaining) {
        bar.name(label(remaining)).progress(Math.clamp(remaining / (float) seconds, BossBar.MIN_PROGRESS, BossBar.MAX_PROGRESS));
        Bukkit.getServer().showBossBar(bar);
    }

    public void hide() {
        Bukkit.getServer().hideBossBar(bar);
    }

    public void hide(Player player) {
        player.hideBossBar(bar);
    }

    private Component label(int remaining) {
        return messages.get(key, Formatter.number("seconds", remaining), Placeholder.unparsed("time", clock(remaining)));
    }

    /** The time left as {@code m:ss}, the way every round shows it, on the boss bar and the sidebar alike. */
    public static String clock(int remaining) {
        return "%d:%02d".formatted(remaining / SECONDS_PER_MINUTE, remaining % SECONDS_PER_MINUTE);
    }
}
