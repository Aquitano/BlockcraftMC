package me.blockcraft.duell.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.Stats;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.GameItems;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class StatsMenus {

    private record Loaded(Stats viewer, Stats opponent) {
    }

    private final Messages messages;
    private final Duel duel;
    private final StatsRepository stats;
    private final GameItems items;
    private final TopList topList;
    private final String globalHead;

    public StatsMenus(Messages messages, Duel duel, StatsRepository stats, GameItems items, TopList topList, String globalHead) {
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
        BlockcraftApi.get().playerTasks().latest(viewer,
                () -> new Loaded(stats.get(viewer.getUniqueId()), opponentId.map(stats::get).orElse(Stats.EMPTY)),
                loaded -> new StatsMenu(messages, items, topList, globalHead, viewer, loaded.viewer(), opponent, loaded.opponent())
                        .open(viewer));
    }
}
