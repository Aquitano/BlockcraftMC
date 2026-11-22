package me.blockcraft.api.game;

import me.blockcraft.common.calendar.HourWindow;
import me.blockcraft.common.config.StrictConfig;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * What BlockcraftAPI's config.yml sets for the round of every game: the lead that makes an opponent a nemesis
 * ({@code nemesis}), how long a death collects respect ({@code respect}), and the words that call a killer a
 * cheater within how long of the kill ({@code hacker}).
 */
public record RoundConfig(int nemesisLead, Duration respectWindow, Duration accusationWindow, List<String> accusations,
                          Duration creditWindow, HourWindow nightOwl, Map<Curiosities.Kind, Integer> curiosityMinimums) {

    public static RoundConfig from(StrictConfig config) {
        return new RoundConfig(
                config.integer("nemesis.lead"),
                Duration.ofSeconds(config.integer("respect.seconds")),
                Duration.ofSeconds(config.integer("hacker.window-seconds")),
                config.texts("hacker.words"),
                Duration.ofSeconds(Math.max(0, config.integer("death-lines.credit-seconds"))),
                HourWindow.from(config.section("moments.night-owl")),
                Curiosities.minimums(config.section("curiosities")));
    }
}
