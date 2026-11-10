package me.blockcraft.duell.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.elo.Elo;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.elo.EloRating;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Ratings for opted-in series, with their final settlement off the main thread. */
public final class DuelRatings {

    private final Plugin plugin;
    private final Messages messages;
    private final Elo elo;
    private java.util.function.BooleanSupplier display = () -> false;
    private final Map<UUID, EloRating> ratings = new HashMap<>();

    public DuelRatings(Plugin plugin, Messages messages, Elo elo) {
        this.plugin = plugin;
        this.messages = messages;
        this.elo = elo;
    }

    public void displayWhen(java.util.function.BooleanSupplier display) {
        this.display = display;
    }

    public void load(Player player, Runnable then) {
        var uuid = player.getUniqueId();
        Tasks.async(plugin, () -> elo.rating(uuid), rating -> {
            if (!player.isOnline()) {
                return;
            }
            remember(player, rating);
            then.run();
        });
    }

    public void forget(Player player) {
        ratings.remove(player.getUniqueId());
    }

    /** {@code <rating>} and {@code <group>} as the sidebar shows them: a nick wears its own, the start rating until the load finished. */
    public TagResolver placeholders(Player player) {
        return elo.placeholders(shown(player));
    }

    /** A nick change swaps the numbers the tab list shows, and the name they hang on. */
    public void refreshTab(Player player) {
        if (ratings.containsKey(player.getUniqueId())) {
            showInTab(player);
        }
    }

    public void settle(DuelRating rating, Player winner, Player loser, Runnable then) {
        if (rating != DuelRating.RATED) {
            return;
        }
        var api = BlockcraftApi.get();
        var winnerId = winner.getUniqueId();
        var winnerName = api.realName(winner);
        var loserId = loser.getUniqueId();
        var loserName = api.realName(loser);
        Tasks.async(plugin, () -> elo.settle(winnerId, winnerName, loserId, loserName), match -> {
            tell(winner, match.winner(), match.delta(), match.winnerBefore());
            tell(loser, match.loser(), -match.delta(), match.loserBefore());
            then.run();
        });
    }

    private void tell(Player player, EloRating rating, int delta, int before) {
        if (!player.isOnline()) {
            return;
        }
        remember(player, rating);
        var placeholders = elo.placeholders(rating);
        player.sendMessage(messages.get("elo.changed", placeholders, Placeholder.unparsed("delta", (delta > 0 ? "+" : "") + delta)));
        var from = elo.group(before);
        var to = elo.group(rating.rating());
        if (to.equals(from)) {
            return;
        }
        boolean promoted = to.minRating() > from.minRating();
        player.sendMessage(messages.get(promoted ? "elo.promoted" : "elo.demoted", placeholders));
        BlockcraftApi.get().notifications().rewardSound(player, promoted ? Sounds.LEVEL_UP : Sounds.LOSE);
    }

    private void remember(Player player, EloRating rating) {
        ratings.put(player.getUniqueId(), rating);
        showInTab(player);
    }

    /** The tab list team carries the rank; the group goes into the name the team wraps. */
    private void showInTab(Player player) {
        if (display.getAsBoolean()) {
            player.playerListName(messages.get("tab.name", elo.placeholders(shown(player)), Placeholder.unparsed("name", player.getName())));
        }
    }

    private EloRating shown(Player player) {
        var real = ratings.getOrDefault(player.getUniqueId(), elo.fresh());
        return BlockcraftApi.get().nickStats().rating(player, real);
    }
}
