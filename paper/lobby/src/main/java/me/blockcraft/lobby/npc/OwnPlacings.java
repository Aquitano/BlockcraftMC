package me.blockcraft.lobby.npc;

import me.blockcraft.api.game.Placing;
import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Under each leaderboard a line only the viewer sees: where they stand on it themselves. The line appears while the
 * viewer is within {@code radius} of the board and goes when they leave; their place is read once per board and
 * {@code fresh}, so walking past many boards costs a query each at most that often.
 */
public final class OwnPlacings implements Listener, AutoCloseable {

    private static final long CHECK_TICKS = 20L;
    private static final double BELOW_BOARD = 1.9;
    private static final double LEAVE_MARGIN = 4.0;

    private record Key(UUID player, String board) {
    }

    private record Known(Optional<Placing> placing, long readAt) {
    }

    private final Plugin plugin;
    private final Leaderboards leaderboards;
    private final Messages messages;
    private final double radius;
    private final long freshMillis;
    private final Map<Key, TextDisplay> shown = new HashMap<>();
    private final Map<Key, Known> known = new HashMap<>();
    private final Set<Key> reading = new HashSet<>();
    private final BukkitTask check;

    public OwnPlacings(Plugin plugin, Leaderboards leaderboards, Messages messages, double radius, Duration fresh) {
        this.plugin = plugin;
        this.leaderboards = leaderboards;
        this.messages = messages;
        this.radius = radius;
        this.freshMillis = fresh.toMillis();
        this.check = Bukkit.getScheduler().runTaskTimer(plugin, this::check, CHECK_TICKS, CHECK_TICKS);
    }

    private void check() {
        var boards = new HashMap<String, Location>();
        for (var world : Bukkit.getWorlds()) {
            for (var display : world.getEntitiesByClass(TextDisplay.class)) {
                Holograms.nameOf(display).filter(leaderboards.names()::contains).ifPresent(name -> boards.put(name, display.getLocation()));
            }
        }
        long now = System.currentTimeMillis();
        shown.entrySet().removeIf(entry -> {
            var player = Bukkit.getPlayer(entry.getKey().player());
            var board = boards.get(entry.getKey().board());
            boolean gone = player == null || board == null || !near(player, board, radius + LEAVE_MARGIN);
            if (gone) {
                entry.getValue().remove();
            }
            return gone;
        });
        for (var player : Bukkit.getOnlinePlayers()) {
            boards.forEach((name, board) -> {
                var key = new Key(player.getUniqueId(), name);
                if (!near(player, board, radius)) {
                    return;
                }
                var cached = known.get(key);
                if (cached == null || now - cached.readAt() > freshMillis) {
                    read(key);
                }
                if (cached != null) {
                    show(player, key, board, cached.placing());
                }
            });
        }
    }

    private void read(Key key) {
        if (!reading.add(key)) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<Placing> placing;
            try {
                placing = leaderboards.placing(key.board(), key.player());
            } catch (RuntimeException e) {
                plugin.getSLF4JLogger().warn("Could not read {}'s place on {}", key.player(), key.board(), e);
                placing = Optional.empty();
            }
            var read = new Known(placing, System.currentTimeMillis());
            Bukkit.getScheduler().runTask(plugin, () -> {
                reading.remove(key);
                if (Bukkit.getPlayer(key.player()) != null) {
                    known.put(key, read);
                }
            });
        });
    }

    private void show(Player player, Key key, Location board, Optional<Placing> placing) {
        var text = line(placing);
        var existing = shown.get(key);
        if (existing != null && existing.isValid()) {
            existing.text(text);
            return;
        }
        var display = Holograms.line(board.clone().subtract(0, BELOW_BOARD, 0), text);
        display.setPersistent(false);
        display.setVisibleByDefault(false);
        player.showEntity(plugin, display);
        shown.put(key, display);
    }

    private Component line(Optional<Placing> placing) {
        return placing.map(place -> messages.get("holo.leaderboard.own",
                        Formatter.number("rank", place.rank()), Formatter.number("value", place.value())))
                .orElseGet(() -> messages.get("holo.leaderboard.own-none"));
    }

    private static boolean near(Player player, Location board, double distance) {
        return player.getWorld().equals(board.getWorld()) && player.getLocation().distanceSquared(board) <= distance * distance;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        shown.entrySet().removeIf(entry -> {
            if (entry.getKey().player().equals(id)) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
        known.keySet().removeIf(key -> key.player().equals(id));
    }

    @Override
    public void close() {
        check.cancel();
        shown.values().forEach(TextDisplay::remove);
        shown.clear();
    }
}
