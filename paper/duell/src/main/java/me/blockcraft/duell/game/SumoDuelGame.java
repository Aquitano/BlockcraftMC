package me.blockcraft.duell.game;

import me.blockcraft.api.game.RoundStart;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.task.Countdown;
import me.blockcraft.duell.arena.SumoArena;
import net.kyori.adventure.text.minimessage.tag.resolver.Formatter;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class SumoDuelGame extends MapDuelGame {
    private final SumoArena arena;
    private final SumoMatch match;
    private final Map<UUID, Integer> lastHits = new HashMap<>();
    private Countdown reset;

    SumoDuelGame(DuelGames.Context context, SumoArena arena) {
        super(context);
        this.arena = arena;
        match = new SumoMatch(context.config().sumo().pointsToWin());
        match.start(context.participants());
        Bukkit.broadcast(context.messages().get("sumo.started", Formatter.number("target", context.config().sumo().pointsToWin())));
    }

    private boolean canPlay(Player player) {
        return active && participant(player) && arena.owns(player.getLocation()) && match.state() == SumoMatch.State.ACTIVE;
    }

    @Override
    public int progress(UUID participant) {
        return match.score(participant);
    }

    @Override
    public void spectate(Player player) {
        player.teleport(arena.spectatorSpawn());
    }

    @Override
    protected void died(PlayerDeathEvent event) {
        fall(event.getPlayer());
    }

    private void fall(Player player) {
        if (!canPlay(player) || !match.fall(player.getUniqueId())) {
            return;
        }
        context.summary().death(player);
        players().stream().filter(other -> !other.equals(player)).forEach(scorer -> {
                context.summary().awardKill(scorer, player, Defeat.POINT);
                Bukkit.broadcast(context.messages().get("sumo.point", Placeholder.unparsed("player", scorer.getName()),
                        Formatter.number("score", progress(scorer.getUniqueId())),
                        Formatter.number("target", context.config().sumo().pointsToWin())));
        });
        players().stream().filter(other -> !other.isDead()).forEach(other -> {
            equip(other);
            spawn(other).ifPresent(other::teleport);
        });
        if (match.state() == SumoMatch.State.FINISHED) {
            complete(match.winner());
            return;
        }
        context.events().changed();
        Runnable ready = () -> {
            if (!closed) {
                lastHits.clear();
                match.ready();
            }
        };
        if (context.config().sumo().resetSeconds() == 0) {
            RoundStart.go(players());
            ready.run();
        } else {
            reset = RoundStart.freeze(context.plugin(), context.config().sumo().resetSeconds(), this::players,
                    context.messages().get("sumo.next-point"), ready);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!participant(event.getPlayer()) || !event.hasChangedPosition()) {
            return;
        }
        if (active && match.state() == SumoMatch.State.RESETTING) {
            var to = event.getFrom().clone();
            to.setYaw(event.getTo().getYaw());
            to.setPitch(event.getTo().getPitch());
            event.setTo(to);
        } else if (canPlay(event.getPlayer()) && arena.lost(event.getTo())) {
            fall(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !participant(victim)) {
            return;
        }
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent hit) || !(hit.getDamager() instanceof Player attacker)
                || hit.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || attacker.equals(victim)
                || !canPlay(attacker) || !canPlay(victim) || !attacker.getInventory().getItemInMainHand().isEmpty()) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        var previous = lastHits.get(victim.getUniqueId());
        if (previous != null && tick - previous < context.config().sumo().hitDelayTicks()) {
            return;
        }
        lastHits.put(victim.getUniqueId(), tick);
        knockback(victim, attacker, context.config().sumo().knockbackStrength());
    }

    static void knockback(Player victim, Player attacker, double strength) {
        var source = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (source.lengthSquared() < 0.0001) {
            source = attacker.getLocation().getDirection().setY(0).multiply(-1);
        }
        if (source.lengthSquared() > 0) {
            victim.knockback(strength, source.getX(), source.getZ());
            victim.setVelocity(victim.getVelocity());
            victim.playHurtAnimation(attacker.getYaw());
        }
    }

    @Override
    public void close() {
        if (reset != null) {
            reset.cancel();
        }
        super.close();
    }
}
