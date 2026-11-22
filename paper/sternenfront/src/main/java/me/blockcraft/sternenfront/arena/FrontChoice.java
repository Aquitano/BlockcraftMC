package me.blockcraft.sternenfront.arena;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.MapVote;
import me.blockcraft.api.world.MapRotation;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Which front the round is fought on. With several map templates each is copied into a world of its own at the start,
 * the lobby votes among them, and the chosen one loads once the vote closes, before the round starts. With one the
 * round plays the main world, which also holds the lobby when there is no shared waiting island.
 */
public final class FrontChoice {

    private static final String NAMESPACE = "sternenfront";

    private final Plugin plugin;
    private final FrontMap main;
    private final MapRotation rotation;
    private final MapVote vote;
    private Optional<FrontMap> chosen = Optional.empty();
    private Function<String, Path> files;

    public FrontChoice(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        var api = BlockcraftApi.get();
        var world = plugin.getServer().getWorlds().getFirst();
        settle(world);
        this.main = ArenaFile.load(world, api.map().file(plugin, ArenaFile.FILE));
        this.files = name -> api.map().file(plugin, name);
        this.rotation = new MapRotation(api.map().rotation(), NAMESPACE, FrontChoice::settle);
        var choices = new ArrayList<MapVote.Choice>();
        if (rotation.rotates()) {
            for (var map : rotation.maps()) {
                try {
                    rotation.copy(map);
                    choices.add(new MapVote.Choice(map.key(), map.name(), map.builders()));
                } catch (RuntimeException e) {
                    plugin.getSLF4JLogger().warn("Leaving the Sternenfront map {} out of the vote: {}", map.key(), e.getMessage());
                }
            }
        }
        this.vote = new MapVote(messages, choices.isEmpty() ? List.of(new MapVote.Choice(NAMESPACE, main.name(), List.of())) : choices,
                Material.END_CRYSTAL);
        dress(main, !vote.offered());
    }

    /** The main world's map, where the lobby stands without a shared waiting island. */
    public FrontMap main() {
        return main;
    }

    public MapVote vote() {
        return vote;
    }

    /** The map chosen so far: the vote's once it closed, the main world's without a vote. */
    public Optional<FrontMap> chosen() {
        return vote.offered() ? chosen : Optional.of(main);
    }

    /** A map-owned file of the chosen map, {@code blockcraft/Sternenfront/<name>}. */
    public Path file(String name) {
        return files.apply(name);
    }

    /** Closes the vote and loads its map, unless that happened already; the main world's map without a vote. */
    public FrontMap decide() {
        if (!vote.offered()) {
            return main;
        }
        if (chosen.isEmpty()) {
            chosen = Optional.of(load(vote.decide()));
        }
        return chosen.orElseThrow();
    }

    private FrontMap load(MapVote.Choice choice) {
        var map = rotation.map(choice.key()).orElseThrow();
        try {
            var front = ArenaFile.load(rotation.load(map), rotation.file(map, plugin, ArenaFile.FILE));
            dress(front, true);
            files = name -> rotation.file(map, plugin, name);
            return front;
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not load the Sternenfront map {}; the round plays {} instead", map.key(), main.name(), e);
            dress(main, true);
            return main;
        }
    }

    /** Hurt players heal through the round's own regeneration and the medics, not through hunger. */
    private static void settle(World world) {
        Worlds.settle(world);
        world.setDifficulty(Difficulty.NORMAL);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
    }

    /**
     * The map's weather and time, and its border, which can include distant scenery since the map's own cliffs are the
     * playable edge. A map the round plays stays in memory whole.
     */
    private void dress(FrontMap map, boolean played) {
        var world = map.world();
        if (map.rain()) {
            world.setStorm(true);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }
        map.time().ifPresent(ticks -> Worlds.setTime(world, ticks));
        if (played) {
            keepLoaded(map);
        }
        var area = map.playArea();
        var border = world.getWorldBorder();
        border.setCenter(area.getCenterX(), area.getCenterZ());
        border.setSize(map.worldBorderSize());
    }

    /** The whole basin stays in memory: bots walk it and posts recolour it wherever the players are. */
    private void keepLoaded(FrontMap map) {
        var area = map.playArea();
        for (int x = (int) area.getMinX() >> 4; x <= (int) area.getMaxX() >> 4; x++) {
            for (int z = (int) area.getMinZ() >> 4; z <= (int) area.getMaxZ() >> 4; z++) {
                map.world().addPluginChunkTicket(x, z, plugin);
            }
        }
    }
}
