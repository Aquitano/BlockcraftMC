package me.blockcraft.lobby.config;

import me.blockcraft.common.config.YamlFile;

import java.time.Duration;
import java.util.Map;

public record ActivitiesConfig(Duration konamiGap, Duration fireworkCooldown, Duration sleepAfter,
                               Duration chickenInterval, int goldenEggOdds, Duration eggLifetime,
                               double pondRadius, int goldenCarpOdds, int checkpoints, double fallBelow,
                               int perseveranceFalls, int easterSpots, int easterEggs, int adventCoins,
                               int halloweenCoins, String halloweenPhrase, Duration pumpkinDuration,
                               Duration sleepWarningAfter, Duration sleepDurationAfter) {

    public static ActivitiesConfig fromYaml(Map<String, Object> root) {
        var activities = YamlFile.section(root, "activities");
        return new ActivitiesConfig(seconds(activities, "konami-gap-seconds", 3),
                seconds(activities, "firework-cooldown-seconds", 30), seconds(activities, "sleep-after-seconds", 600),
                seconds(activities, "chicken-interval-seconds", 180), number(activities, "golden-egg-odds", 200),
                seconds(activities, "egg-lifetime-seconds", 20), number(activities, "pond-radius", 20),
                number(activities, "golden-carp-odds", 500), Math.min(32, nonnegative(activities, "parkour-checkpoints", 7)),
                number(activities, "parkour-fall-below", 4), number(activities, "perseverance-falls", 100),
                Math.min(100, number(activities, "easter-spots", 20)), Math.min(Math.min(100, number(activities, "easter-spots", 20)), number(activities, "easter-eggs", 10)),
                nonnegative(activities, "advent-coins", 25), nonnegative(activities, "halloween-coins", 5),
                String.valueOf(activities.getOrDefault("halloween-phrase", "Süßes oder Saures")),
                seconds(activities, "pumpkin-seconds", 300), seconds(activities, "sleep-warning-seconds", 1200),
                Duration.ofHours(number(activities, "sleep-duration-hours", 1)));
    }

    private static int number(Map<String, Object> values, String key, int fallback) {
        return values.get(key) instanceof Number number ? Math.clamp(number.intValue(), 1, 86400) : fallback;
    }

    private static int nonnegative(Map<String, Object> values, String key, int fallback) {
        return values.get(key) instanceof Number number ? Math.clamp(number.intValue(), 0, 86400) : fallback;
    }

    private static Duration seconds(Map<String, Object> values, String key, int fallback) {
        return Duration.ofSeconds(number(values, key, fallback));
    }
}
