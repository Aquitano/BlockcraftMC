package me.blockcraft.rush.game;

import me.blockcraft.api.game.RoundScope;
import me.blockcraft.api.world.MapBundle;
import me.blockcraft.api.world.MapCopies;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.WorldTemplate;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.rush.config.SpawnPoint;
import me.blockcraft.rush.config.Spawns;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * The worlds rounds play over the server's life. Every round plays a fresh copy of the main world's map in a world of
 * its own, which goes with the round while the map is copied anew in the background for the next one; the lobby stays
 * where it is. Without a template to copy, the round plays the main world itself, whose ore stays where the round
 * seeded it, so the server restarts after that round.
 */
public final class ArenaCopies {

    private static final String KEY = "emeraldrush";

    private final Plugin plugin;
    private final MapCopies copies;
    private final boolean copied;

    public ArenaCopies(Plugin plugin, MapBundle map) {
        this.plugin = plugin;
        this.copies = new MapCopies(plugin, new TemplateDimensions(KEY, ArenaCopies::settle));
        var template = map.template().filter(WorldTemplate::exists);
        template.ifPresent(level -> copies.copyLevelInBackground(level, KEY));
        this.copied = template.isPresent();
    }

    public boolean copied() {
        return copied;
    }

    /**
     * Moves the round's spawns into its world and loads the chunks the search seeds ore into, so the start does not
     * wait for them.
     */
    void prepare(RoundScope scope, Spawns spawns, int radius) {
        load(scope).ifPresentOrElse(spawns::playIn, scope::restartAfter);
        spawns.get(SpawnPoint.SEARCH).ifPresent(centre -> keepLoaded(scope, centre, radius));
    }

    private Optional<World> load(RoundScope scope) {
        if (!copied) {
            return Optional.empty();
        }
        try {
            return Optional.of(scope.own(copies.load(KEY), this::recopy));
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not load the EmeraldRush map's copy; the round plays the main world", e);
            return Optional.empty();
        }
    }

    private static void keepLoaded(Plugin owner, Location centre, int radius) {
        var world = centre.getWorld();
        for (int x = (centre.getBlockX() - radius) >> 4; x <= (centre.getBlockX() + radius) >> 4; x++) {
            for (int z = (centre.getBlockZ() - radius) >> 4; z <= (centre.getBlockZ() + radius) >> 4; z++) {
                int chunkX = x;
                int chunkZ = z;
                world.getChunkAtAsync(chunkX, chunkZ).thenRun(() -> world.addPluginChunkTicket(chunkX, chunkZ, owner));
            }
        }
    }

    /** A copy discarded as the server stops is copied anew at the next start instead. */
    private void recopy(World world) {
        if (plugin.isEnabled()) {
            copies.recopy(world);
        }
    }

    /** The copy keeps the main world's rules; its difficulty, for one, decides whether health comes back by itself. */
    private static void settle(World copy) {
        var main = Bukkit.getWorlds().getFirst();
        copy.setDifficulty(main.getDifficulty());
        Registry.GAME_RULE.forEach(rule -> {
            if (copy.isEnabled(rule)) {
                mirror(rule, main, copy);
            }
        });
        Worlds.setTime(copy, main.getTime());
        copy.setStorm(main.hasStorm());
        copy.setThundering(main.isThundering());
    }

    private static <T> void mirror(GameRule<T> rule, World from, World to) {
        var value = from.getGameRuleValue(rule);
        if (value != null) {
            to.setGameRule(rule, value);
        }
    }
}
