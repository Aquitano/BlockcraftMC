package me.blockcraft.archive.exhibit;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.api.world.TemplateDimensions;
import me.blockcraft.api.world.Worlds;
import me.blockcraft.archive.note.Note;
import me.blockcraft.archive.note.NoteRepository;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * The exhibits' worlds while someone visits them. The first visitor's trip copies the world out of the pool in the
 * background and loads it as {@code exhibit:<key>}; whoever asks meanwhile waits for the same copy. Its notes hang in
 * it as text displays while it is loaded, and a world nobody has been in for {@code unloadAfter} is dropped again.
 */
public final class ExhibitWorlds {

    private static final long SWEEP_TICKS = 20L * 10;
    private static final int NOTE_LINE_WIDTH = 180;

    private record Trip(Consumer<World> arrive, Runnable failed) {
    }

    private final Plugin plugin;
    private final ExhibitPool pool;
    private final NoteRepository notes;
    private final Messages messages;
    private final Duration unloadAfter;
    private final TemplateDimensions dimensions = new TemplateDimensions("exhibit", ExhibitWorlds::settle);
    private final Map<String, List<Trip>> opening = new HashMap<>();
    private final Set<UUID> travelling = new HashSet<>();
    private final Map<String, Instant> emptySince = new HashMap<>();
    private final Map<Long, TextDisplay> displays = new HashMap<>();

    public ExhibitWorlds(Plugin plugin, ExhibitPool pool, NoteRepository notes, Messages messages, Duration unloadAfter) {
        this.plugin = plugin;
        this.pool = pool;
        this.notes = notes;
        this.messages = messages;
        this.unloadAfter = unloadAfter;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, SWEEP_TICKS, SWEEP_TICKS);
    }

    /** The exhibit whose world this is, if it is one. */
    public Optional<Exhibit> exhibitOf(World world) {
        return dimensions.nameOf(world).flatMap(pool::exhibit);
    }

    /** Takes the player to {@code destination} in the exhibit, loading its world first, and tells them {@code arrived}. */
    public void travel(Player player, Exhibit exhibit, Function<World, Location> destination, Component arrived) {
        var id = player.getUniqueId();
        if (!travelling.add(id)) {
            return;
        }
        if (dimensions.loaded(exhibit.key()).isEmpty()) {
            player.sendMessage(messages.get("travel.loading", Placeholder.unparsed("exhibit", exhibit.name())));
        }
        Runnable failed = () -> {
            travelling.remove(id);
            if (player.isOnline()) {
                Sounds.MENU_DENY.play(player);
                player.sendMessage(messages.get("travel.failed", Placeholder.unparsed("exhibit", exhibit.name())));
            }
        };
        open(exhibit, world -> {
            if (!player.isOnline()) {
                travelling.remove(id);
                return;
            }
            try {
                player.teleportAsync(destination.apply(world)).whenComplete((teleported, failure) -> Tasks.sync(plugin, () -> {
                    travelling.remove(id);
                    if (!player.isOnline()) {
                        return;
                    }
                    if (failure != null || !Boolean.TRUE.equals(teleported)) {
                        failed.run();
                    } else {
                        Sounds.TELEPORT.play(player);
                        player.sendMessage(arrived);
                    }
                }));
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not travel to the exhibit " + exhibit.key(), e);
                failed.run();
            }
        }, failed);
    }

    /**
     * Hands the exhibit's world to {@code arrive}, at once when it is loaded and otherwise once it is; {@code failed}
     * runs instead when it could not be copied or loaded. Both run on the main thread.
     */
    private void open(Exhibit exhibit, Consumer<World> arrive, Runnable failed) {
        var key = exhibit.key();
        var loaded = dimensions.loaded(key);
        if (loaded.isPresent()) {
            arrive.accept(loaded.get());
            return;
        }
        var waiting = opening.get(key);
        if (waiting != null) {
            waiting.add(new Trip(arrive, failed));
            return;
        }
        opening.put(key, new ArrayList<>(List.of(new Trip(arrive, failed))));
        Tasks.async(plugin, () -> {
            dimensions.copy(pool.world(key), key);
            return key;
        }, copied -> {
            var trips = opening.remove(key);
            World world;
            try {
                world = dimensions.load(key);
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Could not load the exhibit " + key, e);
                trips.forEach(trip -> trip.failed().run());
                return;
            }
            showNotes(world, key);
            trips.forEach(trip -> trip.arrive().accept(world));
        }, () -> opening.remove(key).forEach(trip -> trip.failed().run()));
    }

    /** Where a visitor of {@code exhibit} lands: its arrival, else the world's own spawn. */
    public Location arrival(Exhibit exhibit, World world) {
        return exhibit.arrival().map(point -> point.in(world)).orElseGet(world::getSpawnLocation);
    }

    public void show(Note note) {
        dimensions.loaded(note.exhibit()).ifPresent(world -> display(world, note));
    }

    public void hide(Note note) {
        Optional.ofNullable(displays.remove(note.id())).ifPresent(TextDisplay::remove);
    }

    private void showNotes(World world, String key) {
        Tasks.async(plugin, () -> notes.list(key), list -> {
            if (dimensions.loaded(key).filter(world::equals).isPresent()) {
                list.forEach(note -> display(world, note));
            }
        });
    }

    private void display(World world, Note note) {
        hide(note);
        var text = messages.get("note.display", Placeholder.unparsed("text", note.text()),
                Placeholder.unparsed("author", note.author().name()),
                Formatter.date("date", note.createdAt().atZone(ZoneId.systemDefault())));
        displays.put(note.id(), world.spawn(note.display(world), TextDisplay.class, entity -> {
            entity.text(text);
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setLineWidth(NOTE_LINE_WIDTH);
            entity.setPersistent(false);
            entity.setShadowed(true);
        }));
    }

    private void sweep() {
        var now = Instant.now();
        for (var world : List.copyOf(Bukkit.getWorlds())) {
            var key = dimensions.nameOf(world);
            if (key.isEmpty()) {
                continue;
            }
            if (!world.getPlayers().isEmpty()) {
                emptySince.remove(key.get());
            } else if (Duration.between(emptySince.computeIfAbsent(key.get(), ignored -> now), now).compareTo(unloadAfter) >= 0) {
                emptySince.remove(key.get());
                displays.values().removeIf(display -> display.getWorld().equals(world));
                Bukkit.unloadWorld(world, false);
            }
        }
    }

    /** An exhibit is looked at, not lived in: it stays at noon, dry, peaceful and still. */
    private static void settle(World world) {
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWNER_BLOCKS_WORK, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        Worlds.setTime(world, 6000);
        world.setStorm(false);
        world.setThundering(false);
    }
}
