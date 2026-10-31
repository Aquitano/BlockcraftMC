package me.blockcraft.api.game.rush;

import me.blockcraft.api.game.PlayerReset;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public final class RushSession implements AutoCloseable {

    public interface Events {
        void fell(Player player);
        void scoreChanged();
        void won(UUID player);
    }

    private final Plugin plugin;
    private final RushConfig config;
    private final RushArena arena;
    private final RushKit kit;
    private final List<UUID> participants;
    private final RushMatch match;
    private final Events events;
    private final RushRules rules;
    private BukkitTask voidCheck;
    private BukkitTask reset;
    private boolean activated;
    private boolean closed;

    public RushSession(Plugin plugin, RushConfig config, RushArena arena, RushKit kit,
                       List<UUID> participants, Events events) {
        this.plugin = plugin;
        this.config = config;
        this.arena = arena;
        this.kit = kit;
        this.participants = List.copyOf(participants);
        this.events = events;
        match = new RushMatch(config.scoreTarget());
        if (!match.start(this.participants)) {
            throw new IllegalArgumentException("A Rush session needs two distinct participants");
        }
        arena.reset();
        rules = new RushRules(this, arena);
        Bukkit.getPluginManager().registerEvents(rules, plugin);
        players().forEach(this::respawn);
    }

    public void activate() {
        if (closed || activated) {
            return;
        }
        activated = true;
        voidCheck = Bukkit.getScheduler().runTaskTimer(plugin, this::checkVoid, 1L, 1L);
    }

    public int score(UUID participant) {
        int slot = match.slot(participant);
        return slot < 0 ? 0 : match.score(slot);
    }

    boolean scoped(Player player) {
        return !closed && participants.contains(player.getUniqueId()) && arena.owns(player.getLocation());
    }

    boolean active(Player player) {
        return activated && scoped(player) && match.state() == RushMatch.State.ACTIVE;
    }

    int slot(Player player) {
        return match.slot(player.getUniqueId());
    }

    void bed(Player player, int owner) {
        if (!active(player) || !match.score(player.getUniqueId(), owner)) {
            return;
        }
        players().forEach(this::respawn);
        events.scoreChanged();
        if (closed) {
            return;
        }
        if (match.state() == RushMatch.State.FINISHED) {
            match.winner().ifPresent(events::won);
            return;
        }
        reset = Bukkit.getScheduler().runTask(plugin, () -> {
            reset = null;
            if (!closed) {
                arena.reset();
                match.ready();
            }
        });
    }

    void refill(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (active(player)) {
                kit.refill(player, slot(player));
            }
        });
    }

    void died(Player player) {
        if (!active(player)) {
            return;
        }
        events.fell(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!closed && player.isOnline() && player.isDead()) {
                player.spigot().respawn();
            }
        });
    }

    void respawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (!scoped(player)) {
            return;
        }
        event.setRespawnLocation(arena.spawn(slot(player)));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!closed && player.isOnline() && !player.isDead()) {
                respawn(player);
            }
        });
    }

    private List<Player> players() {
        return participants.stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull).toList();
    }

    private void respawn(Player player) {
        if (player.isDead()) {
            return;
        }
        PlayerReset.reset(player, GameMode.SURVIVAL);
        var spawn = arena.spawn(slot(player));
        player.teleport(spawn);
        player.setRespawnLocation(spawn, true);
        player.setVelocity(new Vector());
        player.setFallDistance(0);
        kit.give(player, slot(player));
    }

    private void checkVoid() {
        for (var player : players()) {
            if (!player.isDead() && scoped(player) && player.getY() <= config.arena().voidY() && active(player)) {
                events.fell(player);
                if (!closed) {
                    respawn(player);
                }
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (voidCheck != null) {
            voidCheck.cancel();
        }
        if (reset != null) {
            reset.cancel();
        }
        match.stop();
        HandlerList.unregisterAll(rules);
        arena.reset();
    }
}
