package me.blockcraft.duell.game;

import me.blockcraft.api.game.RoundStart;
import me.blockcraft.api.game.Defeat;
import me.blockcraft.api.game.HarmlessHit;
import me.blockcraft.api.game.ThrowFight;
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

import java.util.Optional;
import java.util.UUID;

/**
 * Pushing the opponent off the ring: with bare fists in Sumo, or in the seasonal Wurfschlacht with the season's
 * throwables, which push and never hurt.
 */
final class SumoDuelGame extends MapDuelGame {
    private final SumoArena arena;
    private final SumoMatch match;
    private final Optional<ThrowFight.Ammo> ammo;
    private final Optional<ThrowFight> throwFight;
    private Countdown reset;

    SumoDuelGame(DuelGames.Context context, SumoArena arena, Optional<ThrowFight.Ammo> ammo) {
        super(context);
        this.arena = arena;
        this.ammo = ammo;
        var settings = context.config().throwFight();
        throwFight = ammo.map(season -> new ThrowFight((shooter, victim) -> canPlay(shooter) && canPlay(victim),
                (shooter, victim) -> { }, settings.knockback(), settings.cooldownTicks()).start(context.plugin()));
        match = new SumoMatch(context.config().sumo().pointsToWin());
        match.start(context.participants());
        var target = Formatter.number("target", context.config().sumo().pointsToWin());
        Bukkit.broadcast(ammo.isPresent() ? context.messages().get("throw.started", target) : context.messages().get("sumo.started", target));
    }

    @Override
    protected void equip(Player player) {
        super.equip(player);
        ammo.ifPresent(season -> player.getInventory().setItem(0,
                ThrowFight.item(season, context.messages().get("throw.item." + season.key()))));
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
                match.ready();
            }
        };
        if (context.config().sumo().resetSeconds() == 0) {
            RoundStart.go(players());
            ready.run();
        } else {
            reset = RoundStart.freeze(context.plugin(), context.config().sumo().resetSeconds(), this::players,
                    context.messages().get(ammo.isPresent() ? "throw.next-point" : "sumo.next-point"), ready);
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
        if (ammo.isEmpty() && event instanceof EntityDamageByEntityEvent hit
                && hit.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && hit.getDamager() instanceof Player attacker && canPlay(attacker) && canPlay(victim)) {
            HarmlessHit.land(hit, victim);
        } else {
            event.setCancelled(true);
        }
    }

    @Override
    public void close() {
        if (reset != null) {
            reset.cancel();
        }
        throwFight.ifPresent(ThrowFight::close);
        super.close();
    }
}
