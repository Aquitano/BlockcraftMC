package me.blockcraft.api.game;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The keys every game's config.yml shares: how many players start the lobby countdown by themselves
 * ({@code players.min}), the lobby countdown, what /start shortens it to and the restart after the round
 * ({@code countdown.lobby}, {@code countdown.start}, {@code countdown.restart}), when an empty server restarts
 * ({@code idle-restart-minutes}), whether timed phases show a boss bar ({@code timer-bar}), whose head the global
 * stats wear ({@code global-stats-head}) and how long a round keeps the place of a player who dropped out
 * ({@code rejoin-seconds}, off at 0 and left out by games that keep no places).
 */
public record GameConfig(int minPlayers, int lobbySeconds, int startSeconds, int restartSeconds,
                         int idleRestartMinutes, boolean timerBar, String globalStatsHead, int rejoinSeconds) {

    public static GameConfig from(ConfigurationSection config) {
        return from(config, LobbyCountdown.ROUND_MINIMUM);
    }

    public static GameConfig from(ConfigurationSection section, int minimumPlayers) {
        var config = BundledDefaults.strict(section);
        return new GameConfig(
                Math.max(minimumPlayers, config.integer("players.min")),
                config.integer("countdown.lobby"),
                Math.max(1, config.integer("countdown.start")),
                config.integer("countdown.restart"),
                Math.max(1, config.integer("idle-restart-minutes")),
                config.flag("timer-bar"),
                config.text("global-stats-head"),
                config.has("rejoin-seconds") ? Math.max(0, config.integer("rejoin-seconds")) : 0);
    }
}
