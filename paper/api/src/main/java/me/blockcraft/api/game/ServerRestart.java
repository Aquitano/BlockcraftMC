package me.blockcraft.api.game;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * The end of a round: announces the countdown, sends everyone back to a lobby a second before the end so nobody
 * is kicked, then stops the server for the orchestrator to bring it back fresh.
 */
public final class ServerRestart {

    private static final int ANNOUNCE_FROM = 5;

    private ServerRestart() {
    }

    /** {@code beforeLeaving} runs right before everyone is sent away, for what the round still has to say. */
    public static Countdown start(Plugin plugin, Arena arena, int seconds, Runnable beforeLeaving) {
        arena.announceRestart(seconds);
        return Countdown.start(plugin, seconds, remaining -> {
            if (remaining >= 1 && remaining <= ANNOUNCE_FROM) {
                arena.announceRestart(remaining);
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
