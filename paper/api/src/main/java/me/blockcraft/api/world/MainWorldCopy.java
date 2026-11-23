package me.blockcraft.api.world;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.RoundScope;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Fresh copies of the server's main world for a game whose map is that world and whose play changes it, one for each
 * round. The map is copied in the background as the server starts and again whenever a round's copy is discarded,
 * and the copy keeps the main world's difficulty, game rules, time and weather. Without a template to copy, or when a
 * copy does not load, a round plays the main world itself and the server restarts after it.
 */
public final class MainWorldCopy {

    private final Plugin plugin;
    private final String key;
    private final MapCopies copies;
    private final boolean copied;

    /** On the main thread, while the plugin enables; {@code key} names the copy's world within the plugin's namespace. */
    public MainWorldCopy(Plugin plugin, String key) {
        this.plugin = plugin;
        this.key = key;
        this.copies = new MapCopies(plugin, new TemplateDimensions(key, MainWorldCopy::settle));
        var template = BlockcraftApi.get().map().template().filter(WorldTemplate::exists);
        template.ifPresent(level -> copies.copyLevelInBackground(level, key));
        this.copied = template.isPresent();
        if (!copied) {
            plugin.getSLF4JLogger().warn("No {} map template to copy; every round plays the main world and restarts the server", key);
        }
    }

    /** The round's own copy of the map, or empty when the round plays the main world and the server restarts after it. */
    public Optional<World> load(RoundScope scope) {
        if (!copied) {
            scope.restartAfter();
            return Optional.empty();
        }
        try {
            return Optional.of(scope.own(copies.load(key), this::recopy));
        } catch (RuntimeException e) {
            plugin.getSLF4JLogger().error("Could not load the {} map's copy; the round plays the main world", key, e);
            scope.restartAfter();
            return Optional.empty();
        }
    }

    /** A copy discarded as the server stops is copied anew at the next start instead. */
    private void recopy(World world) {
        if (plugin.isEnabled()) {
            copies.recopy(world);
        }
    }

    /** Its difficulty, for one, decides whether health comes back by itself, which is part of a game's rules. */
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
