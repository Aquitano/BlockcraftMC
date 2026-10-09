package me.blockcraft.core.listener;

import me.blockcraft.common.moderation.Setting;
import me.blockcraft.common.moderation.SettingsRepository;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global mute and chat slow as the proxy last stored them in {@code bc_settings}, re-read on a timer off the chat
 * thread. A failed read keeps the last known values, so a database blip neither lifts a global mute nor stalls chat.
 */
public final class NetworkChat {

    private final SettingsRepository settings;
    private final Logger logger;
    private volatile boolean globalMute;
    private volatile Duration slowMode = Duration.ZERO;
    private volatile boolean failing;

    public NetworkChat(SettingsRepository settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    public void start(Plugin plugin, Duration refresh) {
        long ticks = Math.max(1, refresh.toMillis() / 50);
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::refresh, 0L, ticks);
    }

    public boolean globalMute() {
        return globalMute;
    }

    /** The least time between two messages of one player the proxy's /chatslow asks for; zero while it is off. */
    public Duration slowMode() {
        return slowMode;
    }

    private void refresh() {
        try {
            boolean mute = settings.isEnabled(Setting.GLOBAL_MUTE);
            var slow = Duration.ofSeconds(settings.get(Setting.CHAT_SLOW).map(Long::parseLong).orElse(0L));
            globalMute = mute;
            slowMode = slow;
            failing = false;
        } catch (RuntimeException e) {
            if (!failing) {
                logger.log(Level.WARNING, "Could not read global mute and chat slow, keeping the last known values", e);
            }
            failing = true;
        }
    }
}
