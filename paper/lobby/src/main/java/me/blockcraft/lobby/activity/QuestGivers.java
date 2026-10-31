package me.blockcraft.lobby.activity;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.ChunkTickets;
import me.blockcraft.common.shop.UnlockRewards;
import me.blockcraft.lobby.config.Quest;
import me.blockcraft.lobby.menu.QuestDialog;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Figures at the map's {@code quest-<key>} spots, each with one quest from the {@code quests} section. Talking to one
 * reads the player's progress and shows it in a {@link QuestDialog}; a finished quest pays its reward once, written
 * with its unlock {@code lobby.quest.<key>} in one go so it cannot be claimed twice.
 */
public final class QuestGivers implements Listener, AutoCloseable {

    private static final String PARKOUR_FINISHED = "SELECT best_millis FROM bc_parkour WHERE player_uuid = ? AND best_millis IS NOT NULL";

    private final Locations<String> spots;
    private final List<Quest> quests;
    private final Messages messages;
    private final UnlockRewards rewards;
    private final Map<Mannequin, Quest> givers = new HashMap<>();
    private final ChunkTickets.Owner held;

    public QuestGivers(ChunkTickets chunks, Locations<String> spots, List<Quest> quests, Messages messages) {
        held = chunks.owner();
        this.spots = spots;
        this.quests = quests;
        this.messages = messages;
        this.rewards = new UnlockRewards(BlockcraftApi.get().database());
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
                var chunk = at.getChunk();
                held.add(chunk);
                var skin = quest.skin().isBlank() ? Mannequin.defaultProfile()
                        : ResolvableProfile.resolvableProfile().name(quest.skin()).build();
                givers.put(Figures.spawn(at, skin, messages.get("quests." + quest.key() + ".name")), quest);
            });
        }
    }

    @EventHandler
    public void onTalk(PlayerInteractEntityEvent event) {
        quest(event.getRightClicked()).ifPresent(quest -> {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                talk(event.getPlayer(), quest);
            }
        });
    }

    @EventHandler
    public void onHit(PrePlayerAttackEntityEvent event) {
        quest(event.getAttacked()).ifPresent(quest -> {
            event.setCancelled(true);
            talk(event.getPlayer(), quest);
        });
    }

    private Optional<Quest> quest(Entity entity) {
        return entity instanceof Mannequin figure ? Optional.ofNullable(givers.get(figure)) : Optional.empty();
    }

    private void talk(Player player, Quest quest) {
        Sounds.SHOP_TALK.play(player);
        var id = player.getUniqueId();
        BlockcraftApi.get().playerTasks().latest(player, () -> progress(id, quest),
                progress -> new QuestDialog(messages, quest, progress, claimer -> claim(claimer, quest)).open(player));
    }

    /** Blocking; reads what the quest counts and whether its reward was claimed. */
    private QuestDialog.Progress progress(UUID player, Quest quest) {
        var api = BlockcraftApi.get();
        boolean claimed = api.unlocks().owns(player, quest.item());
        return switch (quest.task()) {
            case Quest.Task.Notes notes -> new QuestDialog.Progress(
                    Math.min(notes.count(), api.unlocks().owned(player, LoreNotes.ITEM_PREFIX).size()), notes.count(), claimed);
            case Quest.Task.Parkour ignored -> new QuestDialog.Progress(
                    api.database().queryOne(PARKOUR_FINISHED, row -> 1, player.toString()).orElse(0), 1, claimed);
            case Quest.Task.Play play -> {
                var stats = new StatsRepository(api.database(), play.table()).get(player);
                yield new QuestDialog.Progress(stats.wins() + stats.losses() > 0 ? 1 : 0, 1, claimed);
            }
        };
    }

    /** Checks again before paying, since the dialog may have stood open while the player's state changed. */
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
        givers.keySet().forEach(Entity::remove);
        givers.clear();
        held.clear();
    }
}
