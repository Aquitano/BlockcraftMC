package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Countdown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * How a round lets its players loose, the same in every game that does it the same way: {@link #freeze} holds them
 * at their spawn with a counting title first, {@link #go} lets them go at once; both end on BlockcraftAPI's "LOS!".
 */
public final class RoundStart {

    private static final Title.Times SECOND_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(250));
    private static final Title.Times GO_TIMES = Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(300));

    private RoundStart() {
    }

    /**
     * The frozen seconds right after a round starts: a title counts them down for the players in the round, with
     * {@code subtitle} under it, then {@link #go} and {@code onEnd} let them move.
     */
    public static Countdown freeze(Plugin plugin, int seconds, Supplier<List<Player>> audience, Component subtitle,
                                   Runnable onEnd) {
        showSecond(audience.get(), subtitle, seconds);
        return Countdown.start(plugin, seconds, remaining -> {
            if (remaining > 0) {
                showSecond(audience.get(), subtitle, remaining);
            }
        }, () -> {
            go(audience.get());
            onEnd.run();
        });
    }

    public static void go(List<Player> players) {
        var go = Title.title(BlockcraftApi.get().messages().get("countdown.go"), Component.empty(), GO_TIMES);
        players.forEach(player -> BlockcraftApi.get().notifications().important(player, go, Sounds.COUNTDOWN_GO));
    }

    private static void showSecond(List<Player> audience, Component subtitle, int remaining) {
        var shown = Title.title(BlockcraftApi.get().messages().get("countdown.seconds", Formatter.number("seconds", remaining)),
                subtitle, SECOND_TIMES);
        audience.forEach(player -> {
            BlockcraftApi.get().notifications().important(player, shown);
            Sounds.countdownTick(player, remaining);
        });
    }
}
