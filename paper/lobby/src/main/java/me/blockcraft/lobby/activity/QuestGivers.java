package me.blockcraft.lobby.activity;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.display.Figures;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.common.shop.UnlockRewards;
import me.blockcraft.lobby.ChatTalk;
import me.blockcraft.lobby.SpotEntities;
import me.blockcraft.lobby.config.Quest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Figures at the map's {@code quest-<key>} spots, each with one quest from the {@code quests} section, and the quest
 * board, which lists them all. Talking to one reads the player's progress and prints it in chat; a finished quest gets
 * a button that pays its reward once, written with its unlock {@code lobby.quest.<key>} in one go so it cannot be
 * claimed twice.
 */
public final class QuestGivers implements AutoCloseable {

    /** How far a player is with a quest: {@code have} of {@code need}, and whether its reward is already theirs. */
    public record Progress(int have, int need, boolean claimed) {

        public boolean done() {
            return have >= need;
        }
    }

    private static final String PARKOUR_FINISHED = "SELECT best_millis FROM bc_parkour WHERE player_uuid = ? AND best_millis IS NOT NULL";
    private static final String BOARD = "quest-board";

    private final Locations<String> spots;
    private final List<Quest> quests;
    private final Messages messages;
    private final ChatTalk talk;
    private final UnlockRewards rewards;
    private final SpotEntities.Group<Quest> givers;

    public QuestGivers(SpotEntities entities, ChatTalk talk, Locations<String> spots, List<Quest> quests, Messages messages) {
        this.spots = spots;
        this.quests = quests;
        this.messages = messages;
        this.talk = talk;
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
        givers = entities.group(this::talk);
        refresh();
    }

    public static String spot(Quest quest) {
        return "quest-" + quest.key();
    }

    public List<String> spots() {
        return quests.stream().map(QuestGivers::spot).toList();
    }

    public void refresh() {
        close();
        for (var quest : quests) {
            spots.get(spot(quest)).ifPresent(at -> {
                var skin = quest.skin().isBlank() ? Mannequin.defaultProfile()
                        : ResolvableProfile.resolvableProfile().name(quest.skin()).build();
                givers.add(quest, at, Figures.spawn(at, skin, messages.get("quests." + quest.key() + ".name")));
            });
        }
    }

    public void openBoard(Player player) {
        if (!talk.ready(player, BOARD)) {
            return;
        }
        Sounds.MENU_OPEN.play(player);
        var id = player.getUniqueId();
        BlockcraftApi.get().playerTasks().latest(player, () -> quests.stream().map(quest -> progress(id, quest)).toList(),
                progress -> board(messages, quests, progress, quest -> claimButton(player, quest)).forEach(player::sendMessage));
    }

    private void talk(Player player, Quest quest) {
        if (!talk.ready(player, quest)) {
            return;
        }
        Sounds.SHOP_TALK.play(player);
        var id = player.getUniqueId();
        BlockcraftApi.get().playerTasks().latest(player, () -> progress(id, quest),
                progress -> lines(messages, quest, progress, done -> claimButton(player, done)).forEach(player::sendMessage));
    }

    /** What a giver says: their words, the task and how far the player has come, or once it is paid, only thank you. */
    static List<Component> lines(Messages messages, Quest quest, Progress progress, Function<Quest, Component> claim) {
        var name = messages.get("quests." + quest.key() + ".name");
        if (progress.claimed()) {
            return List.of(ChatTalk.says(messages, name, messages.get("quests.thanks")));
        }
        var lines = new ArrayList<Component>();
        messages.getLines("quests." + quest.key() + ".talk").forEach(line -> lines.add(ChatTalk.says(messages, name, line)));
        lines.add(messages.get("quests.task-line", task(messages, quest)));
        lines.add(messages.get("quests.status-line", Placeholder.component("status", status(messages, quest, progress, claim))));
        return lines;
    }

    /** A heading and one line per quest, {@code progress} in the same order, each with its button once it can be claimed. */
    static List<Component> board(Messages messages, List<Quest> quests, List<Progress> progress, Function<Quest, Component> claim) {
        var lines = new ArrayList<Component>();
        lines.add(messages.get("quests.board-title"));
        for (int index = 0; index < quests.size(); index++) {
            var quest = quests.get(index);
            var status = status(messages, quest, progress.get(index), claim);
            lines.add(messages.get("quests.board-line", Placeholder.component("name", messages.get("quests." + quest.key() + ".name")),
                    task(messages, quest), Placeholder.component("status", status)));
        }
        return lines;
    }

    private static Component status(Messages messages, Quest quest, Progress progress, Function<Quest, Component> claim) {
        if (progress.claimed()) {
            return messages.get("quests.status-claimed");
        }
        if (progress.done()) {
            return messages.get("quests.status-done", Placeholder.component("claim", claim.apply(quest)));
        }
        return messages.get("quests.status-open", Formatter.number("have", progress.have()), Formatter.number("need", progress.need()));
    }

    private static TagResolver task(Messages messages, Quest quest) {
        return Placeholder.component("task", messages.get("quests." + quest.key() + ".task"));
    }

    private Component claimButton(Player player, Quest quest) {
        var coins = Formatter.number("coins", quest.reward());
        return talk.once(player, messages.get("quests.claim", coins), messages.get("quests.claim-hover", coins),
                claimer -> claim(claimer, quest));
    }

    /** Blocking; reads what the quest counts and whether its reward was claimed. */
    private Progress progress(UUID player, Quest quest) {
        var api = BlockcraftApi.get();
        boolean claimed = api.unlocks().owns(player, quest.item());
        return switch (quest.task()) {
            case Quest.Task.Notes notes -> new Progress(
                    Math.min(notes.count(), api.unlocks().owned(player, LoreNotes.ITEM_PREFIX).size()), notes.count(), claimed);
            case Quest.Task.Parkour ignored -> new Progress(
                    api.database().queryOne(PARKOUR_FINISHED, row -> 1, player.toString()).orElse(0), 1, claimed);
            case Quest.Task.Play play -> {
                var stats = new StatsRepository(api.database(), play.table()).get(player);
                yield new Progress(stats.wins() + stats.losses() > 0 ? 1 : 0, 1, claimed);
            }
        };
    }

    /** Checks again before paying, since the player's state may have changed since the button was printed. */
    private void claim(Player player, Quest quest) {
        var id = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        BlockcraftApi.get().playerTasks().write(player, () -> {
            var progress = progress(id, quest);
            return progress.done() && rewards.grant(id, name, quest.item(), quest.reward());
        }, paid -> {
            if (paid) {
                Sounds.PURCHASE.play(player);
                player.sendMessage(messages.get("quests.claimed", Formatter.number("coins", quest.reward())));
            } else {
                Sounds.MENU_DENY.play(player);
                player.sendMessage(messages.get("quests.claim-failed"));
            }
        });
    }

    @Override
    public void close() {
        givers.close();
    }
}
