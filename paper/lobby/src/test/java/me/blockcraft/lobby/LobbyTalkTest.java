package me.blockcraft.lobby;

import me.blockcraft.api.dialogue.Dialogue;
import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.Quest;
import me.blockcraft.lobby.daily.DailyRewards;
import me.blockcraft.lobby.daily.DailyTalk;
import me.blockcraft.lobby.menu.QuestDialog;
import me.blockcraft.lobby.menu.QuestTalk;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class LobbyTalkTest {

    @Test
    public void everyConversationIsValidAndNamesOnlyBundledMessages() {
        var messages = Messages.load(Path.of("/nonexistent-lobby-messages"), () -> getClass().getResourceAsStream("/messages.yml"));
        var config = YamlFile.loadWithDefaults(Path.of("/nonexistent-lobby-config"), () -> getClass().getResourceAsStream("/config.yml"));
        var dialogues = new ArrayList<Dialogue>();
        for (boolean claimed : List.of(true, false)) {
            for (boolean upcoming : List.of(true, false)) {
                dialogues.add(DailyTalk.dialogue(new DailyRewards.Today(3, 20, claimed), upcoming));
            }
        }
        for (var quest : Quest.fromYaml(YamlFile.section(config, "quests"))) {
            for (var progress : List.of(new QuestDialog.Progress(0, 3, false), new QuestDialog.Progress(3, 3, false),
                    new QuestDialog.Progress(3, 3, true))) {
                dialogues.add(QuestTalk.dialogue(quest, progress));
            }
        }
        for (var dialogue : dialogues) {
            assertTrue(dialogue.problems().toString(), dialogue.problems().isEmpty());
            for (var line : dialogue.lines().values()) {
                assertTrue(line.speaker(), messages.has(line.speaker()));
                assertTrue(line.text(), messages.has(line.text()));
                line.choices().forEach(choice -> assertTrue(choice.text(), messages.has(choice.text())));
            }
        }
    }
}
