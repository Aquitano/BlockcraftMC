package me.blockcraft.api.game;

import org.bukkit.configuration.ConfigurationSection;

/**
 * The keys every game's config.yml shares: how many players start the lobby countdown by themselves
 * ({@code players.min}), the lobby countdown, what /start shortens it to and the restart after the round
 * ({@code countdown.lobby}, {@code countdown.start}, {@code countdown.restart}), when an empty server restarts
 * ({@code idle-restart-minutes}), whether timed phases show a boss bar ({@code timer-bar}), whose head the global
 * stats wear ({@code global-stats-head}) and how long a round keeps the place of a player who dropped out
 * ({@code rejoin-seconds}, off at 0 and in games that keep no places).
 */
public record GameConfig(int minPlayers, int lobbySeconds, int startSeconds, int restartSeconds,
                         int idleRestartMinutes, boolean timerBar, String globalStatsHead, int rejoinSeconds) {

    public static GameConfig from(ConfigurationSection config) {
        return from(config, LobbyCountdown.ROUND_MINIMUM);
    }

    public static GameConfig from(ConfigurationSection config, int minimumPlayers) {
        return new GameConfig(
                Math.max(minimumPlayers, config.getInt("players.min", minimumPlayers)),
                config.getInt("countdown.lobby", 30),
                Math.max(1, config.getInt("countdown.start", 5)),
                config.getInt("countdown.restart", 10),
                Math.max(1, config.getInt("idle-restart-minutes", 50)),
                config.getBoolean("timer-bar", true),
                config.getString("global-stats-head", "Kevos"),
                Math.max(0, config.getInt("rejoin-seconds", 0)));
    }
}
