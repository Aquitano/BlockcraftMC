package me.blockcraft.sternenfront.game;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.sternenfront.game.RoundTally.Deed;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** A fighter's rank across rounds, from every battle point they have ever earned, and their best round. */
final class Career {

    private record Standing(long points, int bestRound) { }

    private final Plugin plugin;
    private final Messages messages;
    private final Database db;
    private final List<Long> ranks;

    Career(Plugin plugin, Messages messages, Database db, List<Long> ranks) {
        this.plugin = plugin;
        this.messages = messages;
        this.db = db;
        this.ranks = ranks;
    }

    void createTable() {
        db.execute("CREATE TABLE IF NOT EXISTS bc_sternenfront_career (player_uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                + "name VARCHAR(16) NOT NULL, points BIGINT NOT NULL, best_round INT NOT NULL)");
    }

    /** The highest rank that {@code points} reach. */
    static int rankOf(long points, List<Long> ranks) {
        int rank = 0;
        for (int index = 0; index < ranks.size(); index++) {
            if (points >= ranks.get(index)) {
                rank = index;
            }
        }
        return rank;
    }

    private Standing read(UUID player) {
        return db.queryOne("SELECT points, best_round FROM bc_sternenfront_career WHERE player_uuid = ?",
                row -> new Standing(row.getLong("points"), row.getInt("best_round")), player.toString()).orElse(new Standing(0, 0));
    }

    private void add(UUID player, String name, int points) {
        db.update("INSERT INTO bc_sternenfront_career (player_uuid, name, points, best_round) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name), points = points + VALUES(points), "
                + "best_round = GREATEST(best_round, VALUES(best_round))", player.toString(), name, points, points);
    }

    void greet(Player player) {
        var id = player.getUniqueId();
        Tasks.async(plugin, () -> read(id), standing -> {
            if (player.isOnline()) {
                player.sendMessage(standing(standing.points()));
            }
        }, () -> { });
    }

    /** Banks the round for everyone who fought in it and tells those still here what it came to. */
    void close(Map<UUID, RoundTally.Line> lines) {
        lines.forEach((id, line) -> {
            var name = Bukkit.getOfflinePlayer(id).getName();
            if (name != null && line.points() > 0) {
                Tasks.async(plugin, () -> {
                    var before = read(id);
                    add(id, name, line.points());
                    return before;
                }, before -> Optional.ofNullable(Bukkit.getPlayer(id)).ifPresent(player -> report(player, line, before)), () -> { });
            }
        });
    }

    private void report(Player player, RoundTally.Line line, Standing before) {
        player.sendMessage(messages.get("career.battle", Formatter.number("points", line.points()),
                Formatter.number("kills", line.count(Deed.KILL)), Formatter.number("headshots", line.count(Deed.HEADSHOT)),
                Formatter.number("captures", line.count(Deed.CAPTURE)), Formatter.number("orders", line.count(Deed.ORDER))));
        if (before.bestRound() > 0 && line.points() > before.bestRound()) {
            player.sendMessage(messages.get("career.record", Formatter.number("best", before.bestRound())));
        }
        long total = before.points() + line.points();
        if (rankOf(total, ranks) > rankOf(before.points(), ranks)) {
            player.sendMessage(messages.get("career.promoted", Placeholder.component("rank", rankName(total))));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1);
        }
        player.sendMessage(standing(total));
    }

    private Component rankName(long points) {
        return messages.get("career.rank." + rankOf(points, ranks));
    }

    private Component standing(long points) {
        int rank = rankOf(points, ranks);
        return rank + 1 < ranks.size()
                ? messages.get("career.standing", Placeholder.component("rank", rankName(points)),
                Formatter.number("points", points), Formatter.number("next", ranks.get(rank + 1)))
                : messages.get("career.top", Placeholder.component("rank", rankName(points)), Formatter.number("points", points));
    }
}
