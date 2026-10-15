package me.blockcraft.api.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Every kill of a round, counted in {@code bc_rivalries} by account, and the nemesis (Erzfeind) the counts make. When
 * the round starts, each player whose nemesis plays too hears of it, and the first kill of that nemesis in the round
 * is their revenge. Nothing is said about a pair in which either wears a nick, because a score would tie the nick to
 * its account; the kill is counted all the same.
 */
final class Rivalries {

    private final Plugin plugin;
    private final BlockcraftApi api;
    private final Messages messages;
    private final RivalryRepository repository;
    private final int lead;
    private final Map<UUID, UUID> nemeses = new HashMap<>();
    private final List<Runnable> afterLoad = new ArrayList<>();
    private Standings standings;
    private boolean loading;

    Rivalries(Plugin plugin, BlockcraftApi api, Messages messages, RivalryRepository repository, int lead) {
        this.plugin = plugin;
        this.api = api;
        this.messages = messages;
        this.repository = repository;
        this.lead = lead;
    }

    void load(List<Player> players) {
        loading = true;
        var ids = players.stream().map(Player::getUniqueId).toList();
        Tasks.async(plugin, () -> new Standings(repository.among(ids)), loaded -> {
            standings = loaded;
            for (var player : players) {
                loaded.nemesis(player.getUniqueId(), ids, lead).ifPresent(nemesis -> {
                    nemeses.put(player.getUniqueId(), nemesis);
                    announce(player, Bukkit.getPlayer(nemesis), loaded.between(player.getUniqueId(), nemesis));
                });
            }
            finishLoading();
        }, this::finishLoading);
    }

    private void finishLoading() {
        loading = false;
        var waiting = List.copyOf(afterLoad);
        afterLoad.clear();
        waiting.forEach(Runnable::run);
    }

    void kill(Player killer, Player victim, Consumer<Standings.Standing> revenge) {
        if (loading) {
            afterLoad.add(() -> kill(killer, victim, revenge));
            return;
        }
        UUID killerId = killer.getUniqueId();
        UUID victimId = victim.getUniqueId();
        if (killerId.equals(victimId)) {
            return;
        }
        Tasks.async(plugin, () -> repository.add(killerId, victimId));
        if (standings == null) {
            return;
        }
        standings.count(killerId, victimId);
        if (!victimId.equals(nemeses.get(killerId)) || masked(killer, victim)) {
            return;
        }
        nemeses.remove(killerId);
        revenge.accept(standings.between(killerId, victimId));
    }

    private void announce(Player player, Player nemesis, Standings.Standing standing) {
        if (!player.isOnline() || nemesis == null || masked(player, nemesis)) {
            return;
        }
        player.sendMessage(messages.get("nemesis.present", Placeholder.unparsed("nemesis", nemesis.getName()),
                Formatter.number("won", standing.won()), Formatter.number("lost", standing.lost())));
        Sounds.NOTIFY.play(player);
    }

    private boolean masked(Player player, Player rival) {
        return api.nickRank(player).isPresent() || api.nickRank(rival).isPresent();
    }
}
