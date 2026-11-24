package me.blockcraft.api.game;

import me.blockcraft.api.config.BundledDefaults;
import org.bukkit.configuration.ConfigurationSection;

/**
 * The keys every game's config.yml shares: how many players start the lobby countdown by themselves
 * ({@code players.min}), the lobby countdown, what /start shortens it to and the restart after the round
 * ({@code countdown.lobby}, {@code countdown.start}, {@code countdown.restart}), when an empty server restarts
 * ({@code idle-restart-minutes}), whether timed phases show a boss bar ({@code timer-bar}), whose head the global
 * stats wear ({@code global-stats-head}) and how long a round keeps the place of a player who dropped out
 * ({@code rejoin-seconds}, off at 0 and left out by games that keep no places), and how many rounds in a row a
 * {@link RoundHost} plays on the same server before it restarts ({@code rounds-in-place}, 1 or left out restarts
 * after every round) and what the lobby countdown runs down to for the players who stay for the next of them
 * ({@code countdown.reopen}).
 */
public record GameConfig(int minPlayers, int lobbySeconds, int startSeconds, int restartSeconds,
                         int idleRestartMinutes, boolean timerBar, String globalStatsHead, int rejoinSeconds,
                         int roundsInPlace, int reopenSeconds) {

    private static final int DEFAULT_REOPEN_SECONDS = 20;

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
                config.has("rejoin-seconds") ? Math.max(0, config.integer("rejoin-seconds")) : 0,
                config.has("rounds-in-place") ? Math.max(1, config.integer("rounds-in-place")) : 1,
                config.has("countdown.reopen") ? Math.max(1, config.integer("countdown.reopen")) : DEFAULT_REOPEN_SECONDS);
    }
}
