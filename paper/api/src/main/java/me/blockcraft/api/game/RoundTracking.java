package me.blockcraft.api.game;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What happens to the round's players while it runs, for the {@link RoundSummary} to tell afterwards: the last hit
 * on each of them, which credits a kill; who took damage; and the counts behind the round's curiosity line.
 */
final class RoundTracking implements Listener {

    private static final long HEALTH_CHECK_TICKS = 20L;

    record Hit(UUID attacker, Material weapon, int distance, long at) { }

    static final class Attacks {
        private final long window;
        private final Map<UUID, Hit> hits = new HashMap<>();

        Attacks(Duration window) {
            this.window = window.toNanos();
        }

        void record(UUID victim, Hit hit) {
            hits.put(victim, hit);
        }

        Optional<Hit> take(UUID victim, long now) {
            var hit = hits.remove(victim);
            return hit != null && now - hit.at() <= window ? Optional.of(hit) : Optional.empty();
        }

        void forget(UUID player) {
            hits.remove(player);
            hits.values().removeIf(hit -> hit.attacker().equals(player));
        }

        void clear() {
            hits.clear();
        }
    }

    private final Plugin plugin;
    private final Attacks attacks;
    private final Curiosities curiosities;
    private final Set<UUID> active = new HashSet<>();
    private final Set<UUID> damaged = new HashSet<>();
    private BukkitTask healthCheck;

    RoundTracking(Plugin plugin, Duration creditWindow, Map<Curiosities.Kind, Integer> curiosityMinimums) {
        this.plugin = plugin;
        this.attacks = new Attacks(creditWindow);
        this.curiosities = new Curiosities(curiosityMinimums);
    }

    void begin(List<Player> players) {
        players.forEach(player -> active.add(player.getUniqueId()));
        healthCheck = Bukkit.getScheduler().runTaskTimer(plugin, this::checkHealth, HEALTH_CHECK_TICKS, HEALTH_CHECK_TICKS);
    }

    void forget(Player player) {
        active.remove(player.getUniqueId());
        attacks.forget(player.getUniqueId());
    }

    void nextGame() {
        attacks.clear();
        damaged.clear();
    }

    void stop() {
        if (healthCheck != null) {
            healthCheck.cancel();
            healthCheck = null;
        }
        curiosities.finish(System.nanoTime());
        active.clear();
        attacks.clear();
    }

    /** The hit that credits the victim's death: the last one within the window, by a player still in the round. */
    Optional<Hit> takeHit(Player victim) {
        return attacks.take(victim.getUniqueId(), System.nanoTime()).filter(hit -> active.contains(hit.attacker()));
    }

    boolean undamaged(Player player) {
        return !damaged.contains(player.getUniqueId());
    }

    void died(Player victim) {
        curiosities.health(victim.getUniqueId(), false, System.nanoTime());
    }

    void minedEmeralds(Player player, int amount) {
        if (playing(player)) {
            curiosities.record(player.getUniqueId(), Curiosities.Kind.EMERALDS, amount);
        }
    }

    Optional<Curiosities.Fact> curiosity(Predicate<UUID> eligible) {
        return curiosities.select(eligible);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && active.contains(player.getUniqueId()) && event.getFinalDamage() > 0) {
            damaged.add(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !active.contains(victim.getUniqueId())) {
            return;
        }
        var source = event.getDamager() instanceof Player direct ? direct : event.getDamageSource().getCausingEntity();
        if (!(source instanceof Player) && event.getDamager() instanceof Projectile projectile) {
            source = projectile.getShooter() instanceof Player shooter ? shooter : null;
        }
        if (!(source instanceof Player attacker) || attacker.equals(victim) || !active.contains(attacker.getUniqueId())) {
            return;
        }
        int distance = 0;
        if (event.getDamager() instanceof AbstractArrow arrow) {
            var origin = arrow.getOrigin();
            if (origin != null && origin.getWorld().equals(victim.getWorld())) {
                distance = (int) Math.round(origin.distance(victim.getLocation()));
            }
        }
        Material weapon = event.getDamager() instanceof Player ? attacker.getInventory().getItemInMainHand().getType() : Material.AIR;
        hit(victim, new Hit(attacker.getUniqueId(), weapon, distance, System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHook(PlayerFishEvent event) {
        var attacker = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() instanceof Player victim
                && !attacker.equals(victim) && active.contains(attacker.getUniqueId()) && active.contains(victim.getUniqueId())) {
            hit(victim, new Hit(attacker.getUniqueId(), Material.FISHING_ROD, 0, System.nanoTime()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (playing(event.getPlayer())) {
            curiosities.record(event.getPlayer().getUniqueId(), Curiosities.Kind.JUMPS, 1);
        }
    }

    private void hit(Player victim, Hit hit) {
        attacks.record(victim.getUniqueId(), hit);
        curiosities.record(hit.attacker(), Curiosities.Kind.BOW, hit.distance());
        if (hit.weapon() == Material.FISHING_ROD) {
            curiosities.record(hit.attacker(), Curiosities.Kind.ROD, 1);
        }
    }

    private void checkHealth() {
        long now = System.nanoTime();
        for (var id : active) {
            var player = Bukkit.getPlayer(id);
            if (player != null) {
                curiosities.health(id, playing(player) && !player.isDead() && player.getHealth() > 0
                        && player.getHealth() <= RoundSummary.HALF_A_HEART, now);
            }
        }
    }

    /** In the round and not watching it, which an eliminated player does. */
    private boolean playing(Player player) {
        return active.contains(player.getUniqueId()) && !Round.isWatching(player);
    }
}
