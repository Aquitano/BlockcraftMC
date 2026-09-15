package me.blockcraft.api.game;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.IntConsumer;

/**
 * The end of a round: announces the countdown, sends everyone back to a lobby a second before the end so nobody
 * is kicked, then stops the server for the orchestrator to bring it back fresh.
 */
public final class ServerRestart {

    private static final int ANNOUNCE_FROM = 5;

    private ServerRestart() {
    }

    public static Countdown start(Plugin plugin, Arena arena, int seconds) {
        return start(plugin, arena, seconds, remaining -> {
        });
    }

    /** {@code onTick} gets every remaining second for what the game itself shows alongside the announcements. */
    public static Countdown start(Plugin plugin, Arena arena, int seconds, IntConsumer onTick) {
        arena.announceRestart(seconds);
        return Countdown.start(plugin, seconds, remaining -> {
            onTick.accept(remaining);
            if (remaining >= 1 && remaining <= ANNOUNCE_FROM) {
                arena.announceRestart(remaining);
            }
            if (remaining == 1) {
                Bukkit.getOnlinePlayers().forEach(player -> ProxyMessaging.hub(plugin, player));
            }
        }, Bukkit::shutdown);
    }

    /** Stops an empty server after the idle period; with players online it looks again after the retry period. */
    public static void whenIdle(Plugin plugin, int idleSeconds, int retrySeconds) {
        Tasks.later(plugin, idleSeconds * 20L, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                Bukkit.shutdown();
            } else {
                whenIdle(plugin, retrySeconds, retrySeconds);
            }
        });
    }
}
