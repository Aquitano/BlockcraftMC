package me.blockcraft.api.nick;

import me.blockcraft.api.game.Stats;
import me.blockcraft.api.rank.NickRanks;
import me.blockcraft.common.elo.EloRating;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Random;

/**
 * The numbers others see for a nicked player: stats, a ladder rating and a level drawn from the nick's name, so
 * they read as an ordinary account and stay the same for as long as the nick is worn. The real rows are untouched;
 * a nicked player still sees their own true numbers where only they look.
 */
public final class NickStats {

    /** A closed range a disguise draws from; {@code nick-stats.<key>: {min, max}} in config.yml. */
    public record Range(double min, double max) {

        public static Range from(Map<String, Object> section, String key, double min, double max) {
            var range = section.get(key) instanceof Map<?, ?> values ? values : Map.of();
            return new Range(number(range, "min", min), number(range, "max", max));
        }

        double draw(Random random) {
            return min + random.nextDouble() * (max - min);
        }

        private static double number(Map<?, ?> section, String key, double fallback) {
            return section.get(key) instanceof Number value ? value.doubleValue() : fallback;
        }
    }

    public record Ranges(Range rounds, Range winRate, Range kdr, Range rating, Range level) {

        public static Ranges from(Map<String, Object> section) {
            return new Ranges(
                    Range.from(section, "rounds", 20, 200),
                    Range.from(section, "win-rate", 0.05, 0.3),
                    Range.from(section, "kdr", 0.6, 1.6),
                    Range.from(section, "rating", 900, 1250),
                    Range.from(section, "level", 3, 12));
        }
    }

    public record Disguise(Stats stats, EloRating rating, int level) {
    }

    private static final int KILL_COINS = 5;
    private static final int WIN_COINS = 15;
    private static final double EXTRA_DEATHS_PER_ROUND = 0.2;

    private final NickRanks nicks;
    private final Ranges ranges;

    public NickStats(NickRanks nicks, Ranges ranges) {
        this.nicks = nicks;
        this.ranges = ranges;
    }

    /** What a player of that name would plausibly have: most rounds lost, a death for nearly every loss. */
    public Disguise disguise(String nick) {
        var random = new Random(nick.hashCode());
        int rounds = (int) Math.round(ranges.rounds().draw(random));
        int wins = (int) Math.round(rounds * ranges.winRate().draw(random));
        int losses = rounds - wins;
        int deaths = losses + random.nextInt((int) (rounds * EXTRA_DEATHS_PER_ROUND) + 1);
        int kills = (int) Math.round(deaths * ranges.kdr().draw(random));
        var stats = new Stats(kills, deaths, wins, losses, kills * KILL_COINS + wins * WIN_COINS);
        var rating = new EloRating((int) Math.round(ranges.rating().draw(random)), wins, losses);
        return new Disguise(stats, rating, (int) Math.round(ranges.level().draw(random)));
    }

    public Stats stats(Player target, Stats real) {
        return nicked(target) ? disguise(target.getName()).stats() : real;
    }

    public EloRating rating(Player target, EloRating real) {
        return nicked(target) ? disguise(target.getName()).rating() : real;
    }

    public int level(Player target, int real) {
        return nicked(target) ? disguise(target.getName()).level() : real;
    }

    private boolean nicked(Player target) {
        return nicks.of(target).isPresent();
    }
}
