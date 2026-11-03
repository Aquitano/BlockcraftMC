package me.blockcraft.sternenfront.listener;

import me.blockcraft.api.game.GamePhase;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.api.world.BuildMode;
import me.blockcraft.sternenfront.game.SternenfrontGame;
import me.blockcraft.sternenfront.hero.Sabers;
import me.blockcraft.sternenfront.hero.Hero;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * The round's rules for bodies and inventories: teammates do not hurt each other, nobody hurts a player who just
 * deployed or one still choosing, a leap spares the landing, nobody goes hungry, kits stay where they are, and below
 * the map a fighter dies.
 */
public final class ArenaListener implements Listener {

    private static final double BELOW_MAP = 10;

    private final SternenfrontGame game;
    private final BuildMode buildMode;

    public ArenaListener(SternenfrontGame game, BuildMode buildMode) {
        this.game = game;
        this.buildMode = buildMode;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !inRound()) {
            return;
        }
        if (game.choosing(victim) || event.getCause() == EntityDamageEvent.DamageCause.FALL && game.sparesFall(victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !inRound()) {
            return;
        }
        var attacker = switch (event.getDamager()) {
            case Player player -> player;
            case Projectile projectile when projectile.getShooter() instanceof Player shooter -> shooter;
            default -> event.getDamageSource().getCausingEntity() instanceof Player causing ? causing : null;
        };
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        var held = attacker.getInventory().getItemInMainHand();
        if ((event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && game.heroOf(attacker).filter(Hero::bladed).isPresent()) {
            event.setCancelled(true);
            return;
        }
        if ((event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && Sabers.isSaber(held) && !Sabers.isLit(held)) {
            event.setCancelled(true);
            return;
        }
        if (game.allies(attacker, victim) || game.isProtected(victim) || game.choosing(attacker)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLandedHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamageSource().getCausingEntity() instanceof Player attacker
                && !attacker.equals(victim)) {
            game.endProtection(attacker);
            game.landed(attacker, victim);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHurt(EntityDamageEvent event) {
        if (!inRound() || event.getFinalDamage() <= 0) {
            return;
        }
        game.support().damaged(event);
        if (event.getEntity() instanceof Player victim) {
            game.hurt(victim);
            game.combat().damaged(victim, event.getDamageSource().getSourceLocation());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        game.death(event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        game.respawn(event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (inRound()) {
            event.setCancelled(true);
            event.getEntity().setFoodLevel(20);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && locked(player)
                && !(event.getView().getTopInventory().getHolder(false) instanceof Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && locked(player)
                && !(event.getView().getTopInventory().getHolder(false) instanceof Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && locked(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (inRound()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (inRound() && event.getTo().getY() < game.map().playArea().getMinY() - BELOW_MAP) {
            game.fell(event.getPlayer());
        }
    }

    private boolean locked(Player player) {
        return inRound() && game.isPlaying(player) && !buildMode.isEnabled(player);
    }

    private boolean inRound() {
        return game.phase() == GamePhase.INGAME;
    }
}
