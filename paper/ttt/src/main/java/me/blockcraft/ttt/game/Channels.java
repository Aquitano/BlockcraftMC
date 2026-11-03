package me.blockcraft.ttt.game;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Work a player does by staying near a spot for a few seconds: a chore, a repair, a revive. Walking off, being hit or
 * dying breaks it off; the action bar shows how far it is. A player does one thing at a time. Reach counts from the
 * eyes, as a click does, so a station overhead such as a hanging bell stays within it.
 */
public final class Channels {

    private static final long STEP = 2L;
    private static final int CELLS = 10;

    private static final class Channel {
        private final Location anchor;
        private final Component label;
        private final int total;
        private final Runnable onDone;
        private int done;

        private Channel(Location anchor, Component label, int total, Runnable onDone) {
            this.anchor = anchor;
            this.label = label;
            this.total = total;
            this.onDone = onDone;
        }
    }

    private final Plugin plugin;
    private final Messages messages;
    private final Supplier<List<Player>> alive;
    private final double reach;
    private final Map<UUID, Channel> channels = new HashMap<>();
    private BukkitTask task;

    public Channels(Plugin plugin, Messages messages, Supplier<List<Player>> alive, double reach) {
        this.plugin = plugin;
        this.messages = messages;
        this.alive = alive;
        this.reach = reach;
    }

    /** Starts {@code label} for {@code seconds} beside {@code anchor}, in place of what the player was doing. */
    public void start(Player player, Location anchor, int seconds, Component label, Runnable onDone) {
        channels.put(player.getUniqueId(), new Channel(anchor.clone(), label, (int) (seconds * 20 / STEP), onDone));
        if (task == null) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::step, STEP, STEP);
        }
    }

    public boolean busy(Player player) {
        return channels.containsKey(player.getUniqueId());
    }

    /** A hit breaks off whatever the player was doing. */
    public void interrupt(Player player) {
        var channel = channels.remove(player.getUniqueId());
        if (channel != null) {
            player.sendMessage(messages.get("channel.interrupted", Placeholder.component("work", channel.label)));
            Sounds.MENU_DENY.play(player);
        }
    }

    public void clear() {
        channels.clear();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void step() {
        var living = alive.get();
        channels.entrySet().removeIf(entry -> {
            var player = plugin.getServer().getPlayer(entry.getKey());
            var channel = entry.getValue();
            if (player == null || !living.contains(player)) {
                return true;
            }
            if (!player.getWorld().equals(channel.anchor.getWorld())
                    || player.getEyeLocation().distanceSquared(channel.anchor) > reach * reach) {
                player.sendMessage(messages.get("channel.left", Placeholder.component("work", channel.label)));
                Sounds.MENU_DENY.play(player);
                return true;
            }
            channel.done++;
            int filled = Math.min(CELLS, channel.done * CELLS / channel.total);
            BlockcraftApi.get().notifications().status(player, messages.get("channel.progress",
                    Placeholder.component("work", channel.label),
                    Placeholder.unparsed("done", "|".repeat(filled)),
                    Placeholder.unparsed("rest", "|".repeat(CELLS - filled))));
            if (channel.done < channel.total) {
                return false;
            }
            plugin.getServer().getScheduler().runTask(plugin, channel.onDone);
            return true;
        });
    }
}
