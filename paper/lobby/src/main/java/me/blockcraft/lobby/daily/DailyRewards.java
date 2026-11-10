package me.blockcraft.lobby.daily;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The daily reward from the {@code daily-rewards} section: one claim per day of the network's calendar, a claim on the
 * day after the last one raises the streak and a missed day starts it again at 1. {@code coins} pays day {@code n} of
 * a streak, and its last entry every day past its end.
 */
public record DailyRewards(List<Integer> coins, int previewDays, String skin) {

    private static final List<Integer> DEFAULT_COINS = List.of(10, 15, 20, 25, 30, 40, 60);

    /** A player's last claim: the streak day it paid and the day it was made. */
    public record Claimed(int streak, LocalDate day) {
    }

    /** One player's reward today: the streak day it counts as, what it pays, and whether it was already taken. */
    public record Today(int streak, int coins, boolean claimed) {
    }

    public DailyRewards {
        coins = coins.isEmpty() ? DEFAULT_COINS : coins.stream().map(amount -> Math.max(0, amount)).toList();
        previewDays = Math.clamp(previewDays, 0, 7);
    }

    public static DailyRewards fromYaml(Map<String, Object> section) {
        var coins = section.get("coins") instanceof List<?> list
                ? list.stream().filter(Number.class::isInstance).map(amount -> ((Number) amount).intValue()).toList()
                : List.<Integer>of();
        return new DailyRewards(coins,
                section.get("preview-days") instanceof Number days ? days.intValue() : 3,
                section.get("skin") instanceof String skin ? skin : "");
    }

    /** A last claim dated today or later counts as today's, so a clock that moved back never pays a second time. */
    public Today today(Optional<Claimed> last, LocalDate today) {
        if (last.isPresent() && !last.get().day().isBefore(today)) {
            return new Today(last.get().streak(), coins(last.get().streak()), true);
        }
        int streak = last.filter(claimed -> claimed.day().plusDays(1).equals(today)).map(claimed -> claimed.streak() + 1).orElse(1);
        return new Today(streak, coins(streak), false);
    }

    public int coins(int streakDay) {
        return coins.get(Math.clamp(streakDay, 1, coins.size()) - 1);
    }
}
