package me.blockcraft.lobby.daily;

import me.blockcraft.api.dialogue.Dialogue;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the courier says in the dialogue panel: today's reward with a choice to take it, or when the next one comes. A
 * conversation that ends on {@link #CLAIM} took it.
 */
public final class DailyTalk {

    public static final String CLAIM = "offer";

    private static final String SPEAKER = "daily.name";

    private DailyTalk() {
    }

    public static Dialogue dialogue(DailyRewards.Today today, boolean upcoming) {
        if (today.claimed()) {
            return new Dialogue("claimed", Map.of("claimed", line("daily.talk.claimed", "", List.of())));
        }
        var choices = new ArrayList<Dialogue.Choice>();
        choices.add(new Dialogue.Choice("daily.talk.claim", ""));
        if (upcoming) {
            choices.add(new Dialogue.Choice("daily.talk.ask-upcoming", "upcoming"));
        }
        choices.add(new Dialogue.Choice("daily.talk.later", "later"));
        return new Dialogue(CLAIM, Map.of(
                CLAIM, line("daily.talk.offer", "", choices),
                "upcoming", line("daily.talk.upcoming", CLAIM, List.of()),
                "later", line("daily.talk.later-reply", "", List.of())));
    }

    public static Messages text(Messages messages, DailyRewards rewards, DailyRewards.Today today, Duration untilNext) {
        var days = new ArrayList<Component>();
        for (int day = today.streak() + 1; day <= today.streak() + rewards.previewDays(); day++) {
            days.add(messages.get("daily.talk.upcoming-day", Formatter.number("day", day), Formatter.number("coins", rewards.coins(day))));
        }
        return messages.with(Formatter.number("streak", today.streak()), Formatter.number("coins", today.coins()),
                Formatter.number("hours", untilNext.toHours()), Formatter.number("minutes", untilNext.toMinutesPart()),
                Placeholder.component("days", Component.join(JoinConfiguration.commas(true), days)));
    }

    private static Dialogue.Line line(String text, String next, List<Dialogue.Choice> choices) {
        return new Dialogue.Line(SPEAKER, text, Dialogue.Portrait.COURIER, next, choices);
    }
}
