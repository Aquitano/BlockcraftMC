package me.blockcraft.lobby.config;

import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;

public record ActivitiesConfig(Duration konamiGap, Duration fireworkCooldown, Duration sleepAfter,
                               Duration chickenInterval, int goldenEggOdds, Duration eggLifetime,
                               double pondRadius, int goldenCarpOdds, int checkpoints, double fallBelow,
                               int perseveranceFalls, int easterSpots, int easterEggs, int adventCoins,
                               int halloweenCoins, String halloweenPhrase, Duration pumpkinDuration,
                               Duration sleepWarningAfter, Duration sleepDurationAfter, int christmasCoins,
                               int placeRadius) {

    /** {@code activities} of Lobby's config.yml. */
    public static ActivitiesConfig from(StrictConfig activities) {
        return new ActivitiesConfig(seconds(activities, "konami-gap-seconds"),
                seconds(activities, "firework-cooldown-seconds"), seconds(activities, "sleep-after-seconds"),
                seconds(activities, "chicken-interval-seconds"), number(activities, "golden-egg-odds"),
                seconds(activities, "egg-lifetime-seconds"), number(activities, "pond-radius"),
                number(activities, "golden-carp-odds"), Math.min(32, nonnegative(activities, "parkour-checkpoints")),
                number(activities, "parkour-fall-below"), number(activities, "perseverance-falls"),
                Math.min(100, number(activities, "easter-spots")), Math.min(Math.min(100, number(activities, "easter-spots")), number(activities, "easter-eggs")),
                nonnegative(activities, "advent-coins"), nonnegative(activities, "halloween-coins"),
                activities.text("halloween-phrase"),
                seconds(activities, "pumpkin-seconds"), seconds(activities, "sleep-warning-seconds"),
                Duration.ofHours(number(activities, "sleep-duration-hours")), nonnegative(activities, "christmas-coins"),
                Math.min(32, number(activities, "place-radius")));
    }

    private static int number(StrictConfig values, String key) {
        return Math.clamp(values.integer(key), 1, 86400);
    }

    private static int nonnegative(StrictConfig values, String key) {
        return Math.clamp(values.integer(key), 0, 86400);
    }

    private static Duration seconds(StrictConfig values, String key) {
        return Duration.ofSeconds(number(values, key));
    }
}
