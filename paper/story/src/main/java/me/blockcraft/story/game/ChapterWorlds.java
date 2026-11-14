package me.blockcraft.story.game;

import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.Worlds;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The worlds a mission's chapters play in. A chapter without a {@code world} plays in the server's own; one with a
 * {@code world} plays in a copy of the dimension template {@code <world>} in the game's chapter worlds, loaded as
 * {@code story:<world>}. Those are {@code BLOCKCRAFT_STORY_WORLDS}, or {@code <map>/blockcraft/<game>/worlds} in the
 * map bundle of the server's own world. A copy is made off the main thread and loaded on it, so the next chapter's world can be made
 * ready while the party still plays the one before.
 */
public final class ChapterWorlds implements AutoCloseable {

    private final Plugin plugin;
    private final World main;
    private final Path templates;
    private final TemplateDimensions dimensions = new TemplateDimensions("story", ChapterWorlds::settle);
    private final Map<String, CompletableFuture<World>> prepared = new HashMap<>();

    public ChapterWorlds(Plugin plugin, World main, Path templates) {
        this.plugin = plugin;
        this.main = main;
        this.templates = templates;
    }

    /** Every story world stands still but for fire, which would spread to blocks the round's end does not put back. */
    public static void settle(World world) {
        Worlds.settle(world);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
    }

    public World main() {
        return main;
    }

    /** Refuses a chapter whose world has no template, so a mission that could not be played never starts. */
    public void check(Chapters.Chapter chapter) {
        chapter.world().ifPresent(name -> {
            if (!TemplateDimensions.isValidName(name)) throw new IllegalArgumentException("Chapter " + chapter.name() + "'s world " + name + " may only hold a-z, 0-9, _, . and -");
            if (!Files.isDirectory(templates.resolve(name).resolve("region"))) {
                throw new IllegalArgumentException("Chapter " + chapter.name() + "'s world has no template at " + templates.resolve(name));
            }
        });
    }

    /** The chapter's world, loaded; it completes on the main thread, at once for the server's own world. */
    public CompletableFuture<World> prepare(Chapters.Chapter chapter) {
        if (chapter.world().isEmpty()) return CompletableFuture.completedFuture(main);
        var name = chapter.world().get();
        return prepared.computeIfAbsent(name, ignored -> {
            var loading = new CompletableFuture<World>();
            Tasks.async(plugin, () -> {
                dimensions.copy(templates.resolve(name), name);
                return name;
            }, copied -> {
                try {
                    loading.complete(dimensions.load(copied));
                } catch (RuntimeException failure) {
                    loading.completeExceptionally(failure);
                }
            }, () -> loading.completeExceptionally(new IllegalStateException("Could not copy the template of " + name)));
            return loading;
        });
    }

    /** Unloads a chapter world nobody stands in any more; the server's own world stays. */
    public void release(World world) {
        if (world.equals(main) || !world.getPlayers().isEmpty()) return;
        dimensions.nameOf(world).ifPresent(prepared::remove);
        Bukkit.unloadWorld(world, false);
    }

    @Override
    public void close() {
        prepared.values().forEach(loading -> loading.thenAccept(world -> {
            if (!world.equals(main)) Bukkit.unloadWorld(world, false);
        }));
        prepared.clear();
    }
}
