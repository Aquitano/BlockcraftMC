package me.blockcraft.zombies.arena;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.MapVote;
import me.blockcraft.api.world.MapRotation;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Which arena the round is played in. With several map templates each is copied into a world of its own at the start,
 * the lobby votes among them, and the chosen one loads once the vote closes and is prepared when the round starts.
 * With one the round plays the main world, as do /setup and /map save in either case.
 */
public final class ArenaChoice {

    public static final String FILE = "arena.yml";
    private static final String NAMESPACE = "zombies";

    private final Plugin plugin;
    private final ArenaMap main;
    private final MapRotation rotation;
    private final MapVote vote;
    private Optional<ArenaMap> chosen = Optional.empty();
    private boolean prepared;

    public ArenaChoice(Plugin plugin, Messages messages, ArenaMap main) {
        this.plugin = plugin;
        this.main = main;
        this.rotation = new MapRotation(BlockcraftApi.get().map().rotation(), NAMESPACE, Worlds::settle);
        var choices = new ArrayList<MapVote.Choice>();
        if (rotation.rotates()) {
            for (var map : rotation.maps()) {
                try {
                    rotation.copy(map);
                    choices.add(new MapVote.Choice(map.key(), map.name(), map.builders()));
                } catch (RuntimeException e) {
                    plugin.getSLF4JLogger().warn("Leaving the Zombies map {} out of the vote: {}", map.key(), e.getMessage());
                }
            }
        }
        this.vote = new MapVote(messages, choices.isEmpty() ? List.of(new MapVote.Choice(NAMESPACE, main.name(), List.of())) : choices,
                Material.ZOMBIE_HEAD);
    }

    /** The main world's arena, which /setup edits and the lobby without a shared waiting island stands in. */
    public ArenaMap main() {
        return main;
    }

    public MapVote vote() {
        return vote;
    }

    /** Whether the round leaves the main world for a map the lobby votes on. */
    public boolean rotates() {
        return vote.offered();
    }

    /** The arena chosen so far: the vote's once it closed, the main world's without a vote. */
    public Optional<ArenaMap> chosen() {
        return rotates() ? chosen : Optional.of(main);
    }

    /**
     * Closes the vote and loads its map's world, whose chunks then load in the background, unless that happened
     * already; the main world's arena without a vote.
     */
    public ArenaMap decide() {
        if (!rotates()) {
            return main;
        }
        if (chosen.isEmpty()) {
            chosen = Optional.of(load(vote.decide()));
        }
        return chosen.orElseThrow();
    }

    /** {@link #decide()}, with the arena prepared for its round: done at the start, once its chunks had time to load. */
    public ArenaMap prepared() {
        var arena = decide();
        if (arena != main && !prepared) {
            prepared = true;
            if (!arena.prepare()) {
                plugin.getSLF4JLogger().error("The Zombies map {} is not ready; the round plays {} instead", arena.name(), main.name());
                main.keepLoaded(plugin);
                chosen = Optional.of(main);
            }
        }
        return chosen().orElseThrow();
    }

    private ArenaMap load(MapVote.Choice choice) {
        var map = rotation.map(choice.key()).orElseThrow();
        try {
            var arena = new ArenaMap(rotation.file(map, plugin, FILE), rotation.load(map));
            arena.keepLoaded(plugin);
            return arena;
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not load the Zombies map {}; the round plays {} instead", map.key(), main.name(), e);
            main.keepLoaded(plugin);
            return main;
        }
    }
}
