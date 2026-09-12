package me.blockcraft.duell.menu;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.GameItems;
import me.blockcraft.duell.game.TopList;
import me.blockcraft.duell.storage.Stats;
import me.blockcraft.duell.storage.StatsRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

public final class StatsMenus {

    private record Loaded(Stats viewer, Stats opponent) {
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Duel duel;
    private final StatsRepository stats;
    private final GameItems items;
    private final TopList topList;
    private final String globalHead;

    public StatsMenus(Plugin plugin, Messages messages, Duel duel, StatsRepository stats, GameItems items,
                      TopList topList, String globalHead) {
        this.plugin = plugin;
        this.messages = messages;
        this.duel = duel;
        this.stats = stats;
        this.items = items;
        this.topList = topList;
        this.globalHead = globalHead;
    }

    public void open(Player viewer) {
        Optional<Player> opponent = duel.isParticipant(viewer) ? duel.opponentsOf(viewer).findFirst() : Optional.empty();
        var opponentId = opponent.map(Player::getUniqueId);
        Tasks.async(plugin,
                () -> new Loaded(stats.get(viewer.getUniqueId()), opponentId.map(stats::get).orElse(Stats.EMPTY)),
                loaded -> {
                    if (viewer.isOnline()) {
                        new StatsMenu(messages, items, topList, globalHead, viewer, loaded.viewer(), opponent, loaded.opponent())
                                .open(viewer);
                    }
                });
    }
}
