package me.blockcraft.api.game;

import me.blockcraft.api.proxy.ProxyMessaging;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.api.task.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.IntConsumer;

/**
 * The end of a round: counts down, sends everyone back to a lobby a second before the end so nobody is kicked,
 * then stops the server for the orchestrator to bring it back fresh.
 */
public final class ServerRestart {

    private ServerRestart() {
    }

    public static Countdown start(Plugin plugin, int seconds, IntConsumer onTick) {
        return Countdown.start(plugin, seconds, remaining -> {
            onTick.accept(remaining);
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
