package me.blockcraft.sternenfront.arena;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.MapVote;
import me.blockcraft.api.game.RoundScope;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.api.world.MapCopies;
import me.blockcraft.api.world.MapRotation;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.WorldTemplate;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * The fronts Sternenfront fights on over the server's whole life. Every round plays a fresh copy of a map's template in
 * a world of its own, which goes with the round while the map is copied anew in the background for a later one, so no
 * post colour, wall, track or opened site reaches the next round. With several templates the lobby votes among them;
 * with one the round plays a copy of the main world's. The main world holds the lobby when there is no shared waiting
 * island. Without a template to copy, the round plays the main world itself and the server restarts after it.
 */
public final class FrontMaps {

    private static final String NAMESPACE = "sternenfront";

    /** A round's front and where its map-owned files lie. */
    record Front(FrontMap map, Function<String, Path> files) {
    }

    private final Plugin plugin;
    private final MapBundle bundle;
    private final FrontMap main;
    private final MapRotation rotation;
    private final MapCopies copies;
    private final List<MapRotation.Entry> maps;
    private final MapVote vote;

    public FrontMaps(Plugin plugin, Messages messages) {
        this.plugin = plugin;
        this.bundle = BlockcraftApi.get().map();
        var world = plugin.getServer().getWorlds().getFirst();
        settle(world);
        this.main = ArenaFile.load(world, bundle.file(plugin, ArenaFile.FILE));
        dress(main);
        this.rotation = new MapRotation(bundle.rotation(), NAMESPACE, FrontMaps::settle);
        this.copies = new MapCopies(plugin, new TemplateDimensions(NAMESPACE, FrontMaps::settle));
        this.maps = rotation.rotates() ? rotation.maps() : bundle.template().filter(WorldTemplate::exists).stream()
                .map(template -> new MapRotation.Entry(NAMESPACE, main.name(), List.of(), template)).toList();
        maps.forEach(map -> copies.copyLevelInBackground(map.template(), map.key()));
        this.vote = new MapVote(messages, maps.isEmpty()
                ? List.of(new MapVote.Choice(NAMESPACE, main.name(), List.of()))
                : maps.stream().map(map -> new MapVote.Choice(map.key(), map.name(), map.builders())).toList(), Material.END_CRYSTAL);
    }

    /** The main world's map, where the lobby stands without a shared waiting island. */
    public FrontMap main() {
        return main;
    }

    public MapVote vote() {
        return vote;
    }

    /** Whether rounds play copies, which leave the main world as it was; without them every round restarts the server. */
    public boolean copied() {
        return !maps.isEmpty();
    }

    /** A map-owned file of the main world's map, {@code blockcraft/Sternenfront/<name>}. */
    Path mainFile(String name) {
        return bundle.file(plugin, name);
    }

    /** Whether the map {@code choice}'s copy for the next round is still being made. */
    boolean copying(MapVote.Choice choice) {
        return maps.stream().anyMatch(map -> map.key().equals(choice.key())) && copies.copying(choice.key());
    }

    /**
     * The copy of the map {@code choice} for the round in {@code scope}, whose basin then loads in the background; the
     * main world's map when there is no copy or it does not load.
     */
    Front load(MapVote.Choice choice, RoundScope scope) {
        var map = maps.stream().filter(each -> each.key().equals(choice.key())).findFirst();
        if (map.isEmpty()) {
            return mainFor(scope);
        }
        try {
            var world = scope.own(copies.load(map.get().key()), this::recopy);
            Function<String, Path> files = rotation.rotates() ? name -> rotation.file(map.get(), plugin, name) : this::mainFile;
            var front = ArenaFile.load(world, files.apply(ArenaFile.FILE));
            dress(front);
            keepLoaded(front, scope);
            return new Front(front, files);
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not load the Sternenfront map {}; the round plays {} instead", map.get().key(), main.name(), e);
            return mainFor(scope);
        }
    }

    /** The main world's map for one round, which keeps the blocks the round changes, so the server restarts after it. */
    private Front mainFor(RoundScope scope) {
        scope.restartAfter();
        keepLoaded(main, scope);
        return new Front(main, this::mainFile);
    }

    /** A copy discarded as the server stops is copied anew at the next start instead. */
    private void recopy(World world) {
        if (plugin.isEnabled()) {
            copies.recopy(world);
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
     * playable edge.
     */
    private static void dress(FrontMap map) {
        var world = map.world();
        if (map.rain()) {
            world.setStorm(true);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }
        map.time().ifPresent(ticks -> Worlds.setTime(world, ticks));
        var area = map.playArea();
        var border = world.getWorldBorder();
        border.setCenter(area.getCenterX(), area.getCenterZ());
        border.setSize(map.worldBorderSize());
    }

    /**
     * The whole basin stays in memory for the round: bots walk it and posts recolour it wherever the players are. It
     * loads in the background, so a front chosen in the lobby does not stall the countdown.
     */
    private static void keepLoaded(FrontMap map, Plugin owner) {
        var area = map.playArea();
        var world = map.world();
        for (int x = (int) area.getMinX() >> 4; x <= (int) area.getMaxX() >> 4; x++) {
            for (int z = (int) area.getMinZ() >> 4; z <= (int) area.getMaxZ() >> 4; z++) {
                int cx = x;
                int cz = z;
                world.getChunkAtAsync(cx, cz).thenRun(() -> {
                    if (owner.isEnabled()) {
                        world.addPluginChunkTicket(cx, cz, owner);
                    }
                });
            }
        }
    }
}
