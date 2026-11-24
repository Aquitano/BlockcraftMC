package me.blockcraft.api.game;

import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The few seconds after a spawn in which other players cannot hurt or knock back a player. Their hits are cancelled
 * before any game rule or the round's kill credit sees them; a projectile counts as its shooter's hit. The protection
 * runs out after the game's duration, or the moment the protected player lands a hit on someone else.
 */
public final class SpawnProtection implements Listener {

    private final Map<UUID, Long> protectedUntil = new HashMap<>();

    public void protect(Player player, Duration duration) {
        protectedUntil.put(player.getUniqueId(), System.nanoTime() + duration.toNanos());
    }

    public boolean isProtected(Player player) {
        var until = protectedUntil.get(player.getUniqueId());
        return until != null && System.nanoTime() - until < 0;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && attacker(event, victim).isPresent() && isProtected(victim)) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Projectile projectile) {
                projectile.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLandedHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            attacker(event, victim).ifPresent(attacker -> protectedUntil.remove(attacker.getUniqueId()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        protectedUntil.remove(event.getPlayer().getUniqueId());
    }

    private static Optional<Player> attacker(EntityDamageByEntityEvent event, Player victim) {
        return event.getDamageSource().getCausingEntity() instanceof Player attacker && !attacker.equals(victim)
                ? Optional.of(attacker) : Optional.empty();
    }
}
