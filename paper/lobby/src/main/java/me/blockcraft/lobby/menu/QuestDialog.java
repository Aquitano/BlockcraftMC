package me.blockcraft.lobby.menu;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.dialog.Dialogs;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.Quest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * What a quest giver says: their words, the task, how far the player has come, and once it is done a button that
 * claims the reward. A claimed quest only says thank you.
 */
public final class QuestDialog {

    /** How far a player is with a quest: {@code have} of {@code need}, and whether its reward is already theirs. */
    public record Progress(int have, int need, boolean claimed) {

        public boolean done() {
            return have >= need;
        }
    }

    private final Messages messages;
    private final Quest quest;
    private final Progress progress;
    private final Consumer<Player> claim;

    public QuestDialog(Messages messages, Quest quest, Progress progress, Consumer<Player> claim) {
        this.messages = messages;
        this.quest = quest;
        this.progress = progress;
        this.claim = claim;
    }

    public void open(Player player) {
        String prefix = "quests." + quest.key() + ".";
        var body = new ArrayList<DialogBody>();
        messages.getLines(prefix + "talk").forEach(line -> body.add(DialogBody.plainMessage(line)));
        body.add(DialogBody.plainMessage(Component.empty()));
        body.add(DialogBody.plainMessage(messages.get(prefix + "task")));
        body.add(DialogBody.plainMessage(status()));
        var buttons = new ArrayList<ActionButton>();
        if (progress.done() && !progress.claimed()) {
            buttons.add(Dialogs.button(messages.get("quests.claim", Formatter.number("coins", quest.reward())), claim));
        }
        var dialog = Dialogs.create(messages.get(prefix + "title"), body, List.of(),
                Dialogs.actions(buttons, Dialogs.button(BlockcraftApi.get().messages().get("menu.close"))));
        Dialogs.show(player, dialog);
    }

    private Component status() {
        if (progress.claimed()) {
            return messages.get("quests.status-claimed");
        }
        if (progress.done()) {
            return messages.get("quests.status-done", Formatter.number("coins", quest.reward()));
        }
        return messages.get("quests.status-open", Formatter.number("have", progress.have()), Formatter.number("need", progress.need()));
    }
}
