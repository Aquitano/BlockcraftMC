package me.blockcraft.bedwars.game;

import me.blockcraft.api.game.Ballot;
import me.blockcraft.api.game.VoteMenu;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.bedwars.config.BedWarsConfig;
import me.blockcraft.bedwars.map.ArenaMap;
import me.blockcraft.bedwars.map.ArenaWorlds;
import me.blockcraft.bedwars.map.MapPool;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Which map the round is played on. The lobby votes among a few maps drawn from the pool, or leaves it to chance,
 * until shortly before the start, when the vote decides, unless someone forced a map before. The chosen map is copied
 * into place in the background at once, so that the start only has to load it.
 */
final class MapChoice {

    private final Plugin plugin;
    private final BedWarsConfig config;
    private final Messages messages;
    private final MapPool pool;
    private final Random random = new Random();
    private Ballot<String> ballot;
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
        return choices.stream().anyMatch(map -> ArenaWorlds.loaded(map.key()).isEmpty());
    }

    Optional<ArenaMap> chosen() {
        return chosen;
    }

    List<String> playable() {
        return available().stream().map(ArenaMap::key).toList();
    }

    Optional<ArenaMap> playable(String key) {
        return available().stream().filter(map -> map.key().equals(key)).findFirst();
    }

    void openMenu(Player player) {
        if (chosen.isPresent()) {
            VoteMenu.closed(player, messages);
            return;
        }
        var options = choices.stream().filter(map -> ArenaWorlds.loaded(map.key()).isEmpty())
                .map(map -> new VoteMenu.Option(Optional.of(map.key()),
                        messages.get("vote.map", Placeholder.unparsed("map", map.name())), new ItemStack(map.icon()),
                        builders(map), ballot.votes(Optional.of(map.key())))).toList();
        new VoteMenu(messages.get("vote.title"), options, Optional.of(ballot.votes(Optional.empty())),
                ballot.choiceOf(player.getUniqueId()), Optional.empty(), this::vote).open(player);
    }

    void withdraw(Player player) {
        ballot.withdraw(player.getUniqueId());
    }

    /** Closes the vote with its winner; false if the map was chosen already or the pool has none. */
    boolean decide() {
        if (chosen.isPresent()) {
            return false;
        }
        var candidates = choices.stream().filter(map -> ArenaWorlds.loaded(map.key()).isEmpty())
                .map(ArenaMap::key).toList();
        if (candidates.isEmpty()) {
            redraw();
            candidates = choices.stream().map(ArenaMap::key).toList();
        }
        var winner = ballot.winner(candidates, random).flatMap(this::choice);
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
        var map = chosen.orElseThrow();
        if (ArenaWorlds.loaded(map.key()).isPresent()) {
            throw new IllegalStateException("The map is already loaded: " + map.key());
        }
        return map;
    }

    /** A fresh draw of maps for a new vote; a menu still showing the old draw closes. */
    void redraw() {
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getOpenInventory().getTopInventory().getHolder(false) instanceof VoteMenu)
                .forEach(Menu::close);
        chosen = Optional.empty();
        forced = false;
        var drawn = new ArrayList<>(available());
        Collections.shuffle(drawn, random);
        choices = List.copyOf(drawn.subList(0, Math.min(config.voteMaps(), drawn.size())));
        ballot = new Ballot<>();
    }

    /** {@code key} empty is a vote to leave the map to chance. */
    private void vote(Player player, Optional<String> key) {
        Menu.close(player);
        if (chosen.isPresent() || (key.isPresent() && choice(key.get()).isEmpty())) {
            VoteMenu.closed(player, messages);
            return;
        }
        var option = key.flatMap(this::choice).<Component>map(map -> Component.text(map.name()))
                .orElseGet(() -> messages.get("vote.chance"));
        boolean changed = ballot.cast(player.getUniqueId(), key).isPresent();
        VoteMenu.confirm(player, messages, option, changed);
    }

    private Optional<ArenaMap> choice(String key) {
        return choices.stream().filter(map -> map.key().equals(key) && ArenaWorlds.loaded(key).isEmpty()).findFirst();
    }

    private List<ArenaMap> available() {
        return pool.playable(config.teams()).stream().filter(map -> ArenaWorlds.loaded(map.key()).isEmpty()).toList();
    }

    private List<Component> builders(ArenaMap map) {
        return map.builders().isEmpty()
                ? List.of()
                : List.of(messages.get("vote.builders", Placeholder.unparsed("builders", String.join(", ", map.builders()))));
    }

    private void choose(ArenaMap map, boolean forcedChoice) {
        if (ArenaWorlds.loaded(map.key()).isPresent()) {
            throw new IllegalStateException("The map is already loaded: " + map.key());
        }
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
