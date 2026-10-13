package me.blockcraft.api.game;

import me.blockcraft.common.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class DeathLines implements Listener {

    public record Death(Player killer, Component line) { }

    public record Hit(UUID attacker, Material weapon, int distance, long at) { }

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

    private final Messages messages;
    private final Attacks attacks;
    private final Set<UUID> active = new HashSet<>();
    private final Consumer<Hit> observer;

    public DeathLines(Messages messages, Duration creditWindow, Consumer<Hit> observer) {
        this.messages = messages;
        this.attacks = new Attacks(creditWindow);
        this.observer = observer;
    }

    public void begin(List<Player> players) {
        stop();
        players.forEach(player -> active.add(player.getUniqueId()));
    }

    public void forget(Player player) {
        active.remove(player.getUniqueId());
        attacks.forget(player.getUniqueId());
    }

    public void stop() {
        active.clear();
        attacks.clear();
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
        record(victim.getUniqueId(), new Hit(attacker.getUniqueId(), weapon,
                distance, System.nanoTime()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHook(PlayerFishEvent event) {
        var attacker = event.getPlayer();
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY && event.getCaught() instanceof Player victim
                && !attacker.equals(victim) && active.contains(attacker.getUniqueId()) && active.contains(victim.getUniqueId())) {
            record(victim.getUniqueId(), new Hit(attacker.getUniqueId(), Material.FISHING_ROD, 0, System.nanoTime()));
        }
    }

    private void record(UUID victim, Hit hit) {
        attacks.record(victim, hit);
        observer.accept(hit);
    }

    public Death resolve(PlayerDeathEvent event) {
        var victim = event.getPlayer();
        var hit = attacks.take(victim.getUniqueId(), System.nanoTime());
        var killer = hit.map(Hit::attacker).map(Bukkit::getPlayer)
                .filter(player -> active.contains(player.getUniqueId())).orElse(null);
        String cause = event.getDamageSource().getDamageType().getKey().getKey();
        String key = switch (cause) {
            case "fall" -> "fall";
            case "out_of_world" -> "void";
            case "lava" -> "lava";
            case "in_fire", "on_fire", "hot_floor" -> "fire";
            case "drown" -> "drowning";
            case "explosion", "player_explosion" -> "explosion";
            case "arrow" -> killer == null ? "generic" : "bow";
            case "player_attack", "trident" -> killer == null ? "generic" : "melee";
            default -> killer == null ? "generic" : "melee";
        };
        var name = Placeholder.unparsed("player", victim.getName());
        var attacker = Placeholder.unparsed("killer", killer == null ? "" : killer.getName());
        var line = messages.pick("death-lines." + key, name, attacker,
                Formatter.number("distance", hit.map(Hit::distance).orElse(0)));
        if (killer != null && !key.equals("bow") && !key.equals("melee")) {
            line = line.append(messages.get("death-lines.assisted", attacker));
        }
        if (killer != null && key.equals("melee") && hit.isPresent()) {
            String weapon = "death-lines.weapons." + hit.get().weapon().name().toLowerCase(Locale.ROOT);
            if (messages.has(weapon)) {
                line = line.append(messages.get("death-lines.weapon", Placeholder.component("weapon", messages.get(weapon))));
            }
        }
        return new Death(killer, line);
    }
}
