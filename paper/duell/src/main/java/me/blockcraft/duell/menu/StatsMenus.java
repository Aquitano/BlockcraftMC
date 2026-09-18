package me.blockcraft.duell.menu;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.elo.Elo;
import me.blockcraft.api.game.Stats;
import me.blockcraft.api.game.StatsRepository;
import me.blockcraft.api.game.TopList;
import me.blockcraft.common.elo.EloRating;
import me.blockcraft.common.message.Messages;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.GameItems;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;

import java.util.Optional;

public final class StatsMenus {

    private record Loaded(Stats viewer, EloRating viewerRating, Stats opponent, EloRating opponentRating) {
    }

    private final Messages messages;
    private final Duel duel;
    private final StatsRepository stats;
    private final Elo elo;
    private final GameItems items;
    private final TopList topList;
    private final String globalHead;

    public StatsMenus(Messages messages, Duel duel, StatsRepository stats, Elo elo, GameItems items, TopList topList, String globalHead) {
        this.messages = messages;
        this.duel = duel;
        this.stats = stats;
        this.elo = elo;
        this.items = items;
        this.topList = topList;
        this.globalHead = globalHead;
    }

    public void open(Player viewer) {
        Optional<Player> opponent = duel.isParticipant(viewer) ? duel.opponentsOf(viewer).findFirst() : Optional.empty();
        var viewerId = viewer.getUniqueId();
        var opponentId = opponent.map(Player::getUniqueId);
        BlockcraftApi.get().playerTasks().latest(viewer,
                () -> new Loaded(stats.get(viewerId), elo.rating(viewerId),
                        opponentId.map(stats::get).orElse(Stats.EMPTY), opponentId.map(elo::rating).orElse(elo.fresh())),
                loaded -> new StatsMenu(messages, items, topList, globalHead,
                        viewer, placeholders(loaded.viewer(), loaded.viewerRating()),
                        opponent, opponent.map(other -> shown(other, loaded)).orElseGet(TagResolver::empty))
                        .open(viewer));
    }

    /** The opponent's numbers as everyone sees them, which for a nicked opponent are the nick's. */
    private TagResolver shown(Player opponent, Loaded loaded) {
        var nickStats = BlockcraftApi.get().nickStats();
        return placeholders(nickStats.stats(opponent, loaded.opponent()), nickStats.rating(opponent, loaded.opponentRating()));
    }

    private TagResolver placeholders(Stats stats, EloRating rating) {
        return TagResolver.resolver(stats.placeholders(), elo.placeholders(rating));
    }
}
