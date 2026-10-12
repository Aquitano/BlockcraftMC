package me.blockcraft.api.achievement;

import me.blockcraft.api.game.Stats;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What an achievement counts: a column of the game's stats table, the network level, or nothing at all for a
 * {@link #MOMENT}, which the code that sees the moment grants itself.
 */
public enum Metric {
    KILLS, DEATHS, WINS, LOSSES, POINTS, LEVEL, MOMENT;

    public long of(Stats stats, int level) {
        return switch (this) {
            case KILLS -> stats.kills();
            case DEATHS -> stats.deaths();
            case WINS -> stats.wins();
            case LOSSES -> stats.losses();
            case POINTS -> stats.points();
            case LEVEL -> level;
            case MOMENT -> 0;
        };
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Metric> parse(String key) {
        return Arrays.stream(values()).filter(metric -> metric.key().equalsIgnoreCase(key)).findFirst();
    }
}
