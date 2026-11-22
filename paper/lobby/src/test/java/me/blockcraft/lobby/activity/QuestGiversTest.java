package me.blockcraft.lobby.activity;

import me.blockcraft.common.config.YamlFile;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.Quest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestGiversTest {

    private static final Component CLAIM = Component.text("[claim]");

    /** The network's colour tags come from BlockcraftAPI, which the test does not load, so they stay in the text. */
    private static final String NETWORK_TAG = "<[a-z]+>";

    private final Messages messages = Messages.load(Path.of("/nonexistent-lobby-messages"),
            () -> getClass().getResourceAsStream("/messages.yml"));
    private final List<Quest> quests = Quest.fromYaml(YamlFile.section(YamlFile.loadWithDefaults(Path.of("/nonexistent-lobby-config"),
            () -> getClass().getResourceAsStream("/config.yml")), "quests"));

    @Test
    public void aGiverOffersTheRewardOnlyOnceTheTaskIsDoneAndThenOnlyThanks() {
        var quest = quests.getFirst();
        var open = lines(quest, new QuestGivers.Progress(1, 3, false));
        assertTrue(open.getLast(), open.getLast().contains("1/3"));
        assertFalse(String.join("\n", open).contains("[claim]"));

        var done = lines(quest, new QuestGivers.Progress(3, 3, false));
        assertEquals(open.size(), done.size());
        assertTrue(done.getLast(), done.getLast().contains("[claim]"));

        assertEquals(1, lines(quest, new QuestGivers.Progress(3, 3, true)).size());
    }

    @Test
    public void theBoardListsEveryQuestWithAButtonOnlyWhereOneIsReady() {
        var progress = quests.stream().map(quest -> new QuestGivers.Progress(1, 1, quest != quests.getFirst())).toList();
        var board = plain(QuestGivers.board(messages, quests, progress, quest -> CLAIM));
        assertEquals(quests.size() + 1, board.size());
        assertTrue(board.get(1), board.get(1).contains("[claim]"));
        board.subList(2, board.size()).forEach(line -> assertFalse(line, line.contains("[claim]")));
    }

    private List<String> lines(Quest quest, QuestGivers.Progress progress) {
        return plain(QuestGivers.lines(messages, quest, progress, ignored -> CLAIM));
    }

    private static List<String> plain(List<Component> lines) {
        var text = lines.stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line).replaceAll(NETWORK_TAG, "")).toList();
        text.forEach(line -> assertFalse(line, line.contains("missing")));
        return text;
    }
}
