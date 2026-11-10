package me.blockcraft.bedwars.lobby;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The lobby's jump and run: stepping on the start plate starts a clock in the action bar, reaching the finish
 * stops it, and the best time of this stay in the lobby is kept. Both plates are marked by a turning ring.
 */
public final class JumpAndRun implements Listener {

    /** Squared distance, in any direction, from the middle of a plate that counts as standing on it. */
    private static final double ON_PLATE = 0.5;
    private static final long RESTART_GAP_MILLIS = 1000;
    private static final double RING_RADIUS = Math.sqrt(2) / 2;
    private static final int RING_STEPS = 30;
    private static final int RESET_SLOT = 2;

    private final Plugin plugin;
    private final Location start;
    private final Location finish;
    private final Messages messages;
    private final Function<Player, ItemStack> resetItem;
    private final Map<UUID, Long> running = new HashMap<>();
    private final Map<UUID, Long> records = new HashMap<>();
    private BukkitTask task;
    private int ringStep;

    public JumpAndRun(Plugin plugin, Location start, Location finish, Messages messages, Function<Player, ItemStack> resetItem) {
        this.plugin = plugin;
        this.start = start;
        this.finish = finish;
        this.messages = messages;
        this.resetItem = resetItem;
    }

    public void open() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    public void close() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        running.clear();
    }

    public void toStart(Player player) {
        player.teleport(start);
    }

    public void forget(Player player) {
        running.remove(player.getUniqueId());
        records.remove(player.getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (task == null || !event.hasChangedPosition()) {
            return;
        }
        var player = event.getPlayer();
        var to = event.getTo();
        if (to.getWorld().equals(start.getWorld()) && to.distanceSquared(start) <= ON_PLATE) {
            var started = running.get(player.getUniqueId());
            if (started == null || System.currentTimeMillis() - started >= RESTART_GAP_MILLIS) {
                running.put(player.getUniqueId(), System.currentTimeMillis());
                player.getInventory().setItem(RESET_SLOT, resetItem.apply(player));
                Sounds.NOTE_PLING.play(player);
            }
        } else if (to.getWorld().equals(finish.getWorld()) && to.distanceSquared(finish) < ON_PLATE) {
            var started = running.remove(player.getUniqueId());
            if (started != null) {
                finish(player, System.currentTimeMillis() - started);
            }
        }
    }

    private void finish(Player player, long millis) {
        Sounds.LEVEL_UP.play(player);
        player.sendMessage(messages.get("lobby.jump-and-run.finished", Placeholder.unparsed("time", seconds(millis))));
        var record = records.get(player.getUniqueId());
        if (record == null || millis < record) {
            records.put(player.getUniqueId(), millis);
            player.sendMessage(messages.get("lobby.jump-and-run.record", Placeholder.unparsed("time", seconds(millis))));
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        running.forEach((id, started) -> {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                BlockcraftApi.get().notifications().status(player,
                        messages.get("lobby.jump-and-run.clock", Placeholder.unparsed("time", seconds(now - started))));
            }
        });
        ringStep = (ringStep + 1) % RING_STEPS;
        ring(start);
        ring(finish);
    }

    private void ring(Location plate) {
        double angle = 2 * Math.PI * ringStep / RING_STEPS;
        var point = plate.clone().add(Math.cos(angle) * RING_RADIUS, 0, Math.sin(angle) * RING_RADIUS);
        plate.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0, 0, 0, 0);
    }

    private static String seconds(long millis) {
        return String.format(Locale.GERMANY, "%.2f", millis / 1000.0);
    }
}
