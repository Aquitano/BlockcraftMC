package me.blockcraft.ttt.game;

import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.message.Messages;
import me.blockcraft.ttt.arena.TesterRoom;
import me.blockcraft.ttt.config.TttConfig;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The traitor tester: whoever presses the button from inside shuts the doorway with glass, and a few seconds later
 * the lamps turn red if a traitor stands in the room, lime otherwise. Everyone hears who went in, and the tester
 * rests a while after every run.
 */
public final class Tester {

    private final Plugin plugin;
    private final TesterRoom room;
    private final TttConfig.Tester config;
    private final Messages messages;
    private final Supplier<List<Player>> alive;
    private final Predicate<Player> showsAsTraitor;
    private long readyAt;
    private boolean running;

    public Tester(Plugin plugin, TesterRoom room, TttConfig.Tester config, Messages messages,
                  Supplier<List<Player>> alive, Predicate<Player> showsAsTraitor) {
        this.plugin = plugin;
        this.room = room;
        this.config = config;
        this.messages = messages;
        this.alive = alive;
        this.showsAsTraitor = showsAsTraitor;
    }

    public boolean isButton(Block block) {
        return block.equals(room.button());
    }

    public void press(Player presser) {
        if (!room.zone().contains(presser.getLocation())) {
            return;
        }
        long now = System.nanoTime();
        if (running || now - readyAt < 0) {
            long seconds = Math.max(1, Duration.ofNanos(readyAt - now).toSeconds());
            presser.sendMessage(messages.get("tester.cooldown", Formatter.number("seconds", seconds)));
            Sounds.MENU_DENY.play(presser);
            return;
        }
        running = true;
        readyAt = now + Duration.ofSeconds(config.cooldownSeconds()).toNanos();
        Bukkit.broadcast(messages.get("tester.entered", Placeholder.unparsed("player", presser.getName())));
        room.entrance().forEach(block -> block.setType(Material.GLASS, false));
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
        room.lamps().forEach(block -> block.setType(lamp, false));
        inside.forEach(player -> (traitor ? Sounds.NOTE_BASS : Sounds.NOTE_PLING).play(player));
    }

    public void reset() {
        if (!running) {
            return;
        }
        running = false;
        room.lamps().forEach(block -> block.setType(Material.REDSTONE_LAMP, false));
        room.entrance().forEach(block -> block.setType(Material.AIR, false));
    }
}
