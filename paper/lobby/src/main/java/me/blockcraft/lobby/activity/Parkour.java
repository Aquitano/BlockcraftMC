package me.blockcraft.lobby.activity;

import me.blockcraft.api.BlockcraftApi;
import me.blockcraft.api.config.Locations;
import me.blockcraft.api.sound.Sounds;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.common.db.Database;
import me.blockcraft.common.message.Messages;
import me.blockcraft.lobby.config.ActivitiesConfig;
import me.blockcraft.lobby.npc.Holograms;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Parkour implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final Locations<String> locations;
    private final ActivitiesConfig config;
    private final Messages messages;
    private final Database db;
    private final Map<UUID, ParkourRun> runs = new HashMap<>();
    private final Set<UUID> returning = new HashSet<>();
    private TextDisplay leaderboard;
    private boolean reading;

    public Parkour(Plugin plugin, Locations<String> locations, ActivitiesConfig config, Messages messages) {
        this.plugin = plugin;
        this.locations = locations;
        this.config = config;
        this.messages = messages;
        db = BlockcraftApi.get().database();
        db.execute("CREATE TABLE IF NOT EXISTS bc_parkour (player_uuid CHAR(36) NOT NULL PRIMARY KEY, "
                + "name VARCHAR(16) NOT NULL, best_millis BIGINT NULL, falls INT NOT NULL DEFAULT 0)");
        db.execute("CREATE INDEX IF NOT EXISTS idx_bc_parkour_best ON bc_parkour (best_millis, player_uuid)");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L, 1200L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || event instanceof PlayerTeleportEvent) {
            return;
        }
        var player = event.getPlayer();
        var id = player.getUniqueId();
        if (BlockcraftApi.get().buildMode().isEnabled(player)) {
            cancel(player);
            return;
        }
        var run = runs.get(id);
        if (run != null) {
            var checkpoint = locations.get(run.checkpoint() == 0 ? "parkour-start" : "checkpoint-" + run.checkpoint());
            if (checkpoint.filter(point -> point.getWorld().equals(event.getTo().getWorld())
                    && event.getTo().getY() < point.getY() - config.fallBelow()).isPresent()) {
                run.fall();
                event.setCancelled(true);
                returning.add(id);
                player.teleport(checkpoint.orElseThrow());
                returning.remove(id);
                var name = BlockcraftApi.get().realName(player);
                Tasks.async(plugin, () -> {
                    db.update("INSERT INTO bc_parkour (player_uuid, name, falls) VALUES (?, ?, 1) "
                            + "ON DUPLICATE KEY UPDATE name = VALUES(name), falls = falls + 1", id.toString(), name);
                    return db.queryOne("SELECT falls FROM bc_parkour WHERE player_uuid = ?", row -> row.getInt(1), id.toString()).orElse(0);
                }, falls -> {
                    if (player.isOnline() && falls >= config.perseveranceFalls()) {
                        BlockcraftApi.get().achievements().grant(player, "network", "ausdauer");
                    }
                });
                Sounds.TELEPORT.play(player);
                return;
            }
        }
        if (!Tag.PRESSURE_PLATES.isTagged(event.getTo().getBlock().getType())) {
            return;
        }
        if (at("parkour-start", event.getTo())) {
            if (!ready()) {
                player.sendMessage(messages.get("parkour.unavailable"));
                return;
            }
            if (run == null && !player.isFlying() && !player.isGliding() && !player.isInsideVehicle()) {
                runs.put(id, new ParkourRun(System.nanoTime()));
                BlockcraftApi.get().flight().set(player, false);
                player.setAllowFlight(false);
                player.sendMessage(messages.get("parkour.start"));
                Sounds.COUNTDOWN_GO.play(player);
            }
        } else if (run != null && at("checkpoint-" + (run.checkpoint() + 1), event.getTo())
                && run.checkpoint() < config.checkpoints()) {
            run.reach(run.checkpoint() + 1);
            Sounds.NOTIFY.play(player);
        } else if (run != null && at("parkour-finish", event.getTo()) && run.checkpoint() == config.checkpoints()) {
            finish(player, run);
        }
    }

    private void finish(Player player, ParkourRun run) {
        runs.remove(player.getUniqueId());
        long time = run.millis(System.nanoTime());
        var id = player.getUniqueId();
        var name = BlockcraftApi.get().realName(player);
        Tasks.async(plugin, () -> {
            db.update("INSERT INTO bc_parkour (player_uuid, name, best_millis) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), best_millis = LEAST(COALESCE(best_millis, VALUES(best_millis)), VALUES(best_millis))",
                    id.toString(), name, time);
            return db.queryOne("SELECT best_millis FROM bc_parkour WHERE player_uuid = ?", row -> row.getLong(1), id.toString()).orElse(time);
        }, best -> {
            BlockcraftApi.get().achievements().grant(player, "network", "parkour-finish");
            if (player.isOnline()) {
                player.sendMessage(messages.get("parkour.finish", Placeholder.unparsed("time", ParkourRun.time(time)),
                        Placeholder.unparsed("best", ParkourRun.time(best))));
                Sounds.WIN.play(player);
            }
            refresh();
        }, () -> {
            if (player.isOnline()) {
                player.sendMessage(BlockcraftApi.get().messages().get("operation.failed"));
            }
        });
    }

    private boolean ready() {
        if (locations.get("parkour-finish").isEmpty()) {
            return false;
        }
        for (int i = 1; i <= config.checkpoints(); i++) {
            if (locations.get("checkpoint-" + i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean startsAt(Location location) {
        return at("parkour-start", location);
    }

    public boolean running(Player player) {
        return runs.containsKey(player.getUniqueId());
    }

    private boolean at(String key, Location location) {
        return locations.get(key).filter(point -> point.getWorld().equals(location.getWorld())
                && point.getBlockX() == location.getBlockX() && point.getBlockY() == location.getBlockY()
                && point.getBlockZ() == location.getBlockZ()).isPresent();
    }

    private void tick() {
        for (var id : Set.copyOf(runs.keySet())) {
            var player = Bukkit.getPlayer(id);
            if (player == null) {
                runs.remove(id);
                continue;
            }
            if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
                cancel(player);
                continue;
            }
            var run = runs.get(id);
            BlockcraftApi.get().notifications().status(player, messages.get("parkour.status",
                    Formatter.number("checkpoint", run.checkpoint()), Formatter.number("total", config.checkpoints()),
                    Placeholder.unparsed("time", ParkourRun.time(run.millis(System.nanoTime())))));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!returning.contains(event.getPlayer().getUniqueId())) {
            cancel(event.getPlayer());
        }
    }

    @EventHandler
    public void onFlight(PlayerToggleFlightEvent event) {
        if (running(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runs.remove(event.getPlayer().getUniqueId());
    }

    private void cancel(Player player) {
        if (runs.remove(player.getUniqueId()) != null) {
            player.sendMessage(messages.get("parkour.cancelled"));
        }
    }

    public void refresh() {
        locations.get("parkour-finish-sign").ifPresent(point -> {
            if (point.getBlock().getState() instanceof Sign sign) {
                var lines = messages.getLines("parkour.finish-sign");
                for (var side : Side.values()) {
                    for (int index = 0; index < 4; index++) {
                        sign.getSide(side).line(index, index < lines.size() ? lines.get(index) : Component.empty());
                    }
                }
                sign.update(false, false);
            }
        });
        var location = locations.get("parkour-top");
        if (reading || location.isEmpty()) {
            return;
        }
        reading = true;
        Tasks.async(plugin, () -> db.queryList("SELECT player_uuid, name, best_millis FROM bc_parkour "
                + "WHERE best_millis IS NOT NULL ORDER BY best_millis, player_uuid LIMIT 10",
                row -> Map.entry(row.getString("name"), row.getLong("best_millis"))), rows -> {
            reading = false;
            var lines = new ArrayList<Component>();
            lines.add(messages.get("parkour.top"));
            if (rows.isEmpty()) {
                lines.add(messages.get("parkour.top-empty"));
            }
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.get(i);
                lines.add(messages.get("parkour.entry", Formatter.number("rank", i + 1),
                        Placeholder.unparsed("player", row.getKey()), Placeholder.unparsed("time", ParkourRun.time(row.getValue()))));
            }
            if (leaderboard != null) {
                leaderboard.remove();
            }
            leaderboard = Holograms.line(location.orElseThrow(), Component.join(JoinConfiguration.newlines(), lines));
            leaderboard.setPersistent(false);
        }, () -> reading = false);
    }

    @Override
    public void close() {
        if (leaderboard != null) {
            leaderboard.remove();
        }
        runs.clear();
    }
}
