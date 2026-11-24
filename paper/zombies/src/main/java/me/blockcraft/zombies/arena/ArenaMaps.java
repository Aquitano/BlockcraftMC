package me.blockcraft.zombies.arena;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.MapVote;
import me.blockcraft.api.game.RoundScope;
import me.blockcraft.api.world.MapCopies;
import me.blockcraft.api.world.MapRotation;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.WorldTemplate;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.common.message.Messages;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * The arenas Zombies plays over the server's whole life. Every round plays a fresh copy of a map's template in a world
 * of its own, which goes with the round while the map is copied anew in the background for a later one. With several
 * templates the lobby votes among them; with one the round plays a copy of the main world's. Without a template to
 * copy, the round plays the main world itself, which /setup and /map save edit in either case.
 */
public final class ArenaMaps {

    public static final String FILE = "arena.yml";
    private static final String NAMESPACE = "zombies";

    private final Plugin plugin;
    private final ArenaMap main;
    private final MapRotation rotation;
    private final MapCopies copies;
    private final List<MapRotation.Entry> maps;
    private final MapVote vote;

    /** {@code main} is the main world's arena, prepared already. */
    public ArenaMaps(Plugin plugin, Messages messages, ArenaMap main) {
        this.plugin = plugin;
        this.main = main;
        var bundle = BlockcraftApi.get().map();
        this.rotation = new MapRotation(bundle.rotation(), NAMESPACE, Worlds::settle);
        this.copies = new MapCopies(plugin, new TemplateDimensions(NAMESPACE, Worlds::settle));
        this.maps = rotation.rotates() ? rotation.maps() : bundle.template().filter(WorldTemplate::exists).stream()
                .map(template -> new MapRotation.Entry(NAMESPACE, main.name(), List.of(), template)).toList();
        maps.forEach(map -> copies.copyLevelInBackground(map.template(), map.key()));
        this.vote = new MapVote(messages, maps.isEmpty()
                ? List.of(new MapVote.Choice(NAMESPACE, main.name(), List.of()))
                : maps.stream().map(map -> new MapVote.Choice(map.key(), map.name(), map.builders())).toList(), Material.ZOMBIE_HEAD);
    }

    /** The main world's arena, which /setup edits and the lobby without a shared waiting island stands in. */
    public ArenaMap main() {
        return main;
    }

    public MapVote vote() {
        return vote;
    }

    /** Whether rounds play copies, which leave the main world as it was; without them every round restarts the server. */
    public boolean copied() {
        return !maps.isEmpty();
    }

    /** Whether the map {@code choice}'s copy for the next round is still being made. */
    boolean copying(MapVote.Choice choice) {
        return maps.stream().anyMatch(map -> map.key().equals(choice.key())) && copies.copying(choice.key());
    }

    /**
     * The world of the map {@code choice} for the round in {@code scope}, whose chunks then load in the background;
     * the main world's arena when there is no copy or it does not load.
     */
    ArenaMap load(MapVote.Choice choice, RoundScope scope) {
        var map = maps.stream().filter(each -> each.key().equals(choice.key())).findFirst();
        if (map.isEmpty()) {
            return mainFor(scope);
        }
        try {
            var world = scope.own(copies.load(map.get().key()), this::recopy);
            var arena = new ArenaMap(rotation.file(map.get(), plugin, FILE), world);
            arena.keepLoaded(scope);
            return arena;
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not load the Zombies map {}; the round plays {} instead", map.get().key(), main.name(), e);
            return mainFor(scope);
        }
    }

    /**
     * The main world's arena for one round. It puts back what the round changed when it closes, but the server
     * restarts after it all the same, since nothing proves that restore complete.
     */
    ArenaMap mainFor(RoundScope scope) {
        var arena = main.fresh();
        arena.keepLoaded(scope);
        scope.restartAfter();
        scope.onClose(arena::restore);
        return arena;
    }

    /** A copy discarded as the server stops is copied anew at the next start instead. */
    private void recopy(World world) {
        if (plugin.isEnabled()) {
            copies.recopy(world);
        }
    }
}
