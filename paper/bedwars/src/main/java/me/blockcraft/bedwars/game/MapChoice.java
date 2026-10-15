package me.blockcraft.bedwars.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.lobby.MapVote;
import me.blockcraft.bedwars.lobby.VoteMenu;
import me.blockcraft.bedwars.map.ArenaMap;
import me.blockcraft.bedwars.map.ArenaWorlds;
import me.blockcraft.bedwars.map.MapPool;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Which map the round is played on. The lobby votes among a few maps of the pool until shortly before the start,
 * when the most voted one wins, unless someone forced one before. The chosen map is copied into place in the
 * background at once, so that the start only has to load it.
 */
final class MapChoice {

    private final Plugin plugin;
    private final BedWarsConfig config;
    private final Messages messages;
    private final MapPool pool;
    private final Random random = new Random();
    private MapVote vote;
    private List<ArenaMap> choices;
    private Optional<ArenaMap> chosen = Optional.empty();
    private boolean forced;
    private CompletableFuture<Void> copied = CompletableFuture.completedFuture(null);

    MapChoice(Plugin plugin, BedWarsConfig config, Messages messages, MapPool pool) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.pool = pool;
        redraw();
    }

    boolean hasMaps() {
        return !choices.isEmpty();
    }

    Optional<ArenaMap> chosen() {
        return chosen;
    }

    List<String> playable() {
        return pool.playable(config.teams()).stream().map(ArenaMap::key).toList();
    }

    Optional<ArenaMap> playable(String key) {
        return pool.playable(config.teams()).stream().filter(map -> map.key().equals(key)).findFirst();
    }

    void openMenu(Player player) {
        if (chosen.isPresent()) {
            player.sendMessage(messages.get("vote.over"));
            return;
        }
        new VoteMenu(messages, vote, choices, config.voteSlots(), this::vote).open(player);
    }

    void withdraw(Player player) {
        vote.withdraw(player.getUniqueId());
    }

    /** Closes the vote with its winner; false if the map was chosen already or the pool has none. */
    boolean decide() {
        if (chosen.isPresent()) {
            return false;
        }
        var winner = vote.winner(random).flatMap(key -> choices.stream().filter(map -> map.key().equals(key)).findFirst());
        winner.ifPresent(map -> choose(map, false));
        return winner.isPresent();
    }

    void force(ArenaMap map) {
        choose(map, true);
    }

    /** The lobby fell back below its minimum after the vote closed: unless a map was forced, it is held again. */
    boolean reopen() {
        if (chosen.isEmpty() || forced) {
            return false;
        }
        redraw();
        return true;
    }

    /** Waits for the chosen map's copy, which normally finished while the lobby counted down its last seconds. */
    ArenaMap awaitCopy() {
        copied.join();
        return chosen.orElseThrow();
    }

    /** A fresh draw of maps for a new vote. */
    void redraw() {
        chosen = Optional.empty();
        forced = false;
        var playable = pool.playable(config.teams());
        vote = MapVote.among(playable.stream().map(ArenaMap::key).toList(), config.voteSlots().size(), random);
        choices = vote.choices().stream()
                .map(key -> playable.stream().filter(map -> map.key().equals(key)).findFirst().orElseThrow())
                .toList();
    }

    private void vote(Player player, ArenaMap map) {
        if (chosen.isPresent()) {
            player.sendMessage(messages.get("vote.over"));
            return;
        }
        var previous = vote.cast(player.getUniqueId(), map.key());
        player.sendMessage(messages.get(previous.isPresent() ? "vote.changed" : "vote.cast", Placeholder.unparsed("map", map.name())));
    }

    private void choose(ArenaMap map, boolean forcedChoice) {
        chosen = Optional.of(map);
        forced = forcedChoice;
        messages.getLines("vote.result", Placeholder.unparsed("map", map.name())).forEach(Bukkit::broadcast);
        Sounds.NOTIFY.playToAll();
        var copy = new CompletableFuture<Void>();
        Tasks.async(plugin, () -> {
            try {
                ArenaWorlds.copy(pool, map.key());
                copy.complete(null);
            } catch (RuntimeException e) {
                copy.completeExceptionally(e);
            }
        });
        copied = copy;
    }
}
