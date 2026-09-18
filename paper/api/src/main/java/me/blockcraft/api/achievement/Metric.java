package me.blockcraft.api.achievement;

import me.blockcraft.api.game.Stats;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** What an achievement counts: a column of the game's stats table, or the network level. */
public enum Metric {
    KILLS, DEATHS, WINS, LOSSES, POINTS, LEVEL;

    public long of(Stats stats, int level) {
        return switch (this) {
            case KILLS -> stats.kills();
            case DEATHS -> stats.deaths();
            case WINS -> stats.wins();
            case LOSSES -> stats.losses();
            case POINTS -> stats.points();
            case LEVEL -> level;
        };
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Metric> parse(String key) {
        return Arrays.stream(values()).filter(metric -> metric.key().equalsIgnoreCase(key)).findFirst();
    }
}
