package me.blockcraft.duell.listener;

import me.blockcraft.api.game.PlayerReset;
import me.blockcraft.api.task.Tasks;
import me.blockcraft.duell.config.SumoConfig;
import me.blockcraft.duell.game.Duel;
import me.blockcraft.duell.game.DuelMode;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SumoListener implements Listener {

    private final Plugin plugin;
    private final Duel duel;
    private final SumoConfig config;
    private final Map<UUID, Integer> lastHits = new HashMap<>();

    public SumoListener(Plugin plugin, Duel duel, SumoConfig config) {
        this.plugin = plugin;
        this.duel = duel;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!duel.isPlaying(DuelMode.SUMO) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent hit) || !(hit.getDamager() instanceof Player attacker)
                || hit.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || attacker.equals(victim)
                || !duel.sumoActive(attacker) || !duel.sumoActive(victim)
                || !attacker.getInventory().getItemInMainHand().isEmpty()) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        var previous = lastHits.get(victim.getUniqueId());
        if (previous != null && tick - previous < config.hitDelayTicks()) {
            return;
        }
        lastHits.put(victim.getUniqueId(), tick);
        knockback(victim, attacker, config.knockbackStrength());
    }

    static void knockback(Player victim, Player attacker, double strength) {
        var source = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (source.lengthSquared() < 0.0001) {
            source = attacker.getLocation().getDirection().setY(0).multiply(-1);
        }
        if (source.lengthSquared() > 0) {
            victim.knockback(strength, source.getX(), source.getZ());
            // Cancelled damage skips Paper's player velocity sync.
            victim.setVelocity(victim.getVelocity());
            victim.playHurtAnimation(attacker.getYaw());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition() && duel.sumoActive(event.getPlayer()) && duel.sumoLost(event.getTo())) {
            duel.sumoFall(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (duel.isPlaying(DuelMode.SUMO)) {
            duel.handleDeath(event);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        var player = event.getPlayer();
        if (!duel.isMode(DuelMode.SUMO) || !duel.isParticipant(player)) {
            return;
        }
        duel.participantSpawn(player).ifPresent(event::setRespawnLocation);
        Tasks.sync(plugin, () -> {
            if (player.isOnline() && duel.isParticipant(player)) {
                PlayerReset.reset(player, duel.isIngame() ? GameMode.SURVIVAL : GameMode.ADVENTURE);
                player.setVelocity(new Vector());
            }
        });
    }
}
