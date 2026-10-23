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
    private static final int RETURN_COUNTDOWN_SECONDS = 3;
    private static final int MINIMUM_SECONDS = 7;

    private ServerRestart() {
    }

    /** {@code beforeLeaving} runs right before everyone is sent away, for what the round still has to say. */
    public static Countdown start(Plugin plugin, Arena arena, int seconds, Runnable beforeLeaving) {
        return start(plugin, arena, seconds, beforeLeaving, remaining -> { });
    }

    /** {@code returningIn} counts down to the lobby transfer, before the shutdown's final grace second. */
    public static Countdown start(Plugin plugin, Arena arena, int seconds, Runnable beforeLeaving, IntConsumer returningIn) {
        int duration = Math.max(MINIMUM_SECONDS, seconds);
        arena.announceRestart(duration);
        return Countdown.start(plugin, duration, remaining -> {
            if (remaining >= 1 && remaining <= ANNOUNCE_FROM) {
                arena.announceRestart(remaining);
            }
            int untilLobby = remaining - 1;
            if (untilLobby >= 1 && untilLobby <= RETURN_COUNTDOWN_SECONDS) {
                returningIn.accept(untilLobby);
            }
            if (remaining == 1) {
                beforeLeaving.run();
                Bukkit.getOnlinePlayers().forEach(player -> ProxyMessaging.hub(plugin, player));
            }
        }, Bukkit::shutdown);
    }

    /** Stops an empty server after {@code idle-restart-minutes}; with players online it looks again as long after. */
    public static void whenIdle(Plugin plugin, GameConfig config) {
        int seconds = config.idleRestartMinutes() * 60;
        whenIdle(plugin, seconds, seconds);
    }

    private static void whenIdle(Plugin plugin, int idleSeconds, int retrySeconds) {
        Tasks.later(plugin, idleSeconds * 20L, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                Bukkit.shutdown();
            } else {
                whenIdle(plugin, retrySeconds, retrySeconds);
            }
        });
    }
}
