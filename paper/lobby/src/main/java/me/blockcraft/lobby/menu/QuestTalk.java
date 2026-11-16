package me.blockcraft.lobby.menu;

import me.blockcraft.api.dialogue.Dialogue;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.Quest;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import java.util.List;
import java.util.Map;

/**
 * What a quest giver says in the dialogue panel: their words, the task and how far the player has come, with a choice
 * to take the reward once it is done. A conversation that ends on {@link #CLAIM} took it. A claimed quest only says
 * thank you.
 */
public final class QuestTalk {

    public static final String CLAIM = "reward";

    private QuestTalk() {
    }

    public static Dialogue dialogue(Quest quest, QuestDialog.Progress progress) {
        String prefix = "quests." + quest.key() + ".";
        if (progress.claimed()) {
            return new Dialogue("thanks", Map.of("thanks", line(prefix, "quests.status-claimed", "", List.of())));
        }
        if (!progress.done()) {
            return new Dialogue("talk", Map.of(
                    "talk", line(prefix, prefix + "talk", "progress", List.of()),
                    "progress", line(prefix, "quests.talk.progress", "", List.of())));
        }
        return new Dialogue("talk", Map.of(
                "talk", line(prefix, prefix + "talk", CLAIM, List.of()),
                CLAIM, line(prefix, "quests.talk.progress", "", List.of(
                        new Dialogue.Choice("quests.claim", ""), new Dialogue.Choice("quests.talk.later", "later"))),
                "later", line(prefix, "quests.talk.later-reply", "", List.of())));
    }

    public static Messages text(Messages messages, Quest quest, QuestDialog.Progress progress) {
        return messages.with(Formatter.number("coins", quest.reward()),
                Placeholder.component("task", messages.get("quests." + quest.key() + ".task")),
                Placeholder.component("status", QuestDialog.status(messages, quest, progress)));
    }

    private static Dialogue.Line line(String prefix, String text, String next, List<Dialogue.Choice> choices) {
        return new Dialogue.Line(prefix + "name", text, Dialogue.Portrait.LOCAL, next, choices);
    }
}
