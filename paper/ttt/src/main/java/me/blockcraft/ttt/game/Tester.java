package me.blockcraft.ttt.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.game.BlockChanges;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.arena.TesterRoom;
import me.blockcraft.ttt.config.TttConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The traitor tester: whoever presses the button from inside shuts the doorway with glass, and a few seconds later
 * the lamps turn red if a traitor stands in the room, lime otherwise. Everyone hears who went in, and the tester
 * rests a while after every run. Lamps and doorway go back to what the map had there, so a map's own lamps may be
 * any block. Where the map has a traitor trap, its floor drops away once per round, for a moment, under whoever
 * stands in the room. A label over the doorway shows everyone where it is, through the town's walls.
 */
public final class Tester {

    private static final double LABEL_LIFT = 1.4;
    private static final float LABEL_VIEW_RANGE = 1f;

    private final Plugin plugin;
    private final TesterRoom room;
    private final TttConfig.Tester config;
    private final Messages messages;
    private final Supplier<List<Player>> alive;
    private final Predicate<Player> showsAsTraitor;
    private final Map<Block, BlockData> atRest = new HashMap<>();
    private final Map<Block, BlockData> floor = new HashMap<>();
    private Optional<TextDisplay> label = Optional.empty();
    private long readyAt;
    private boolean running;
    private Optional<UUID> trapper = Optional.empty();
    private boolean trapOpen;

    public Tester(Plugin plugin, TesterRoom room, TttConfig.Tester config, Messages messages,
                  Supplier<List<Player>> alive, Predicate<Player> showsAsTraitor) {
        this.plugin = plugin;
        this.room = room;
        this.config = config;
        this.messages = messages;
        this.alive = alive;
        this.showsAsTraitor = showsAsTraitor;
        room.lamps().forEach(block -> atRest.put(block, block.getBlockData()));
        room.entrance().forEach(block -> atRest.put(block, block.getBlockData()));
        room.trap().ifPresent(trap -> trap.floor().forEach(block -> floor.put(block, block.getBlockData())));
    }

    /** Puts the label over the doorway for the round. */
    public void showLabel() {
        label.ifPresent(TextDisplay::remove);
        label = Optional.of(label(room, messages));
    }

    private static TextDisplay label(TesterRoom room, Messages messages) {
        var doorway = room.entrance().isEmpty() ? List.of(room.button()) : room.entrance();
        var at = doorway.getFirst().getLocation().zero();
        doorway.forEach(block -> at.add(block.getX() + 0.5, 0, block.getZ() + 0.5));
        at.multiply(1.0 / doorway.size());
        at.setY(doorway.stream().mapToInt(Block::getY).max().orElseThrow() + LABEL_LIFT);
        return at.getWorld().spawn(at, TextDisplay.class, entity -> {
            entity.setPersistent(false);
            entity.text(messages.get("tester.label"));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setBrightness(new Display.Brightness(15, 15));
            entity.setViewRange(LABEL_VIEW_RANGE);
        });
    }

    public boolean isButton(Block block) {
        return block.equals(room.button());
    }

    public Location buttonLocation() {
        return room.button().getLocation().add(0.5, 0.5, 0.5);
    }

    public boolean isTrapButton(Block block) {
        return room.trap().filter(trap -> trap.button().equals(block)).isPresent();
    }

    public boolean trapUsed() {
        return trapper.isPresent();
    }

    public Optional<UUID> trapper(Location location, String damageType) {
        if (!List.of("lava", "in_fire", "on_fire").contains(damageType)) {
            return Optional.empty();
        }
        return trapper.filter(ignored -> floor.keySet().stream().anyMatch(block -> block.getWorld().equals(location.getWorld())
                && block.getX() == location.getBlockX() && block.getZ() == location.getBlockZ() && location.getY() < block.getY()));
    }

    /** Drops the floor and closes it again after a moment. */
    public void springTrap(UUID player) {
        trapper = Optional.of(player);
        trapOpen = true;
        BlockChanges.set(floor.keySet(), Material.AIR);
        Tasks.later(plugin, config.trapSeconds() * 20L, this::closeTrap);
    }

    /** At the round's end: lamps, doorway and floor go back to the map's blocks at once. */
    public void stop() {
        reset();
        closeTrap();
        trapper = Optional.empty();
        label.ifPresent(TextDisplay::remove);
        label = Optional.empty();
    }

    public void press(Player presser) {
        if (!room.zone().contains(presser.getLocation())) {
            BlockcraftApi.get().notifications().feedback(presser, messages.get("tester.outside"));
            return;
        }
        long now = System.nanoTime();
        if (running || now - readyAt < 0) {
            long seconds = Math.max(1, Duration.ofNanos(readyAt - now).toSeconds());
            BlockcraftApi.get().notifications().feedback(presser, messages.get("tester.cooldown", Formatter.number("seconds", seconds)));
            Sounds.MENU_DENY.play(presser);
            return;
        }
        running = true;
        readyAt = now + Duration.ofSeconds(config.cooldownSeconds()).toNanos();
        Bukkit.broadcast(messages.get("tester.entered", Placeholder.unparsed("player", presser.getName())));
        BlockChanges.set(room.entrance(), Material.GLASS);
        Tasks.later(plugin, config.delaySeconds() * 20L, this::reveal);
        Tasks.later(plugin, (config.delaySeconds() + config.showSeconds()) * 20L, this::reset);
    }

    private void reveal() {
        if (!running) {
            return;
        }
        var inside = alive.get().stream().filter(player -> room.zone().contains(player.getLocation())).toList();
        // Counted rather than matched, so that every traitor inside uses up their spoofer, not just the first.
        boolean traitor = inside.stream().filter(showsAsTraitor).count() > 0;
        var lamp = traitor ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        BlockChanges.set(room.lamps(), lamp);
        inside.forEach(player -> (traitor ? Sounds.NOTE_BASS : Sounds.NOTE_PLING).play(player));
    }

    private void reset() {
        if (!running) {
            return;
        }
        running = false;
        BlockChanges.set(atRest);
    }

    private void closeTrap() {
        if (!trapOpen) {
            return;
        }
        trapOpen = false;
        BlockChanges.set(floor);
    }
}
