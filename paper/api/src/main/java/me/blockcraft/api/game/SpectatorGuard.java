package me.blockcraft.api.game;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import me.blockcraft.api.menu.Menu;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.util.BoundingBox;

import java.util.function.Predicate;

/**
 * Keeps spectators out of the round. They watch in adventure mode, so that their hotbar works, and adventure mode
 * protects nothing that spectator mode did: here they take and deal no damage, touch, pick up, drop and open nothing
 * but menus, and draw no mob, projectile or potion. A spectator hovering where a player places a block moves up.
 */
final class SpectatorGuard implements Listener {

    private final Predicate<Player> isSpectator;

    SpectatorGuard(Predicate<Player> isSpectator) {
        this.isSpectator = isSpectator;
    }

    private boolean watching(Entity entity) {
        return entity instanceof Player player && isSpectator.test(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!watching(event.getEntity())) {
            return;
        }
        event.setCancelled(true);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.getEntity().teleport(event.getEntity().getWorld().getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        var damager = event.getDamager();
        if (watching(damager) || damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter && watching(shooter)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (watching(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickupExperience(PlayerPickupExperienceEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (watching(event.getPlayer()) && !(event.getInventory().getHolder(false) instanceof Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (watching(event.getWhoClicked()) && !inMenu(event.getView())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (watching(event.getWhoClicked()) && !inMenu(event.getView())) {
            event.setCancelled(true);
        }
    }

    /** A menu handles its own clicks; everything else a spectator could rearrange is their own hotbar. */
    private static boolean inMenu(InventoryView view) {
        return view.getTopInventory().getHolder(false) instanceof Menu;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (watching(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && watching(event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitEntity() != null && watching(event.getHitEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPotion(EntityPotionEffectEvent event) {
        if (watching(event.getEntity()) && event.getNewEffect() != null && event.getCause() != EntityPotionEffectEvent.Cause.PLUGIN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (watching(event.getEntered())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMount(EntityMountEvent event) {
        if (watching(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onVehicleDamage(VehicleDamageEvent event) {
        if (event.getAttacker() != null && watching(event.getAttacker())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() != null && watching(event.getRemover())) {
            event.setCancelled(true);
        }
    }

    /**
     * The server still counts an unseen spectator's body as being in the way of a block. When spectators are all
     * that stands there and the block could stand there otherwise, the block goes in and they move above it.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCanBuild(BlockCanBuildEvent event) {
        if (event.isBuildable()) {
            return;
        }
        var block = event.getBlock();
        var space = BoundingBox.of(block);
        var inTheWay = block.getWorld().getNearbyEntities(space, this::watching);
        if (inTheWay.isEmpty() || !event.getBlockData().isSupported(block)) {
            return;
        }
        boolean blocked = !block.getWorld().getNearbyEntities(space,
                entity -> !watching(entity) && !entity.isDead() && (entity instanceof LivingEntity || entity instanceof Vehicle)).isEmpty();
        if (blocked) {
            return;
        }
        inTheWay.forEach(spectator -> spectator.teleport(spectator.getLocation().add(0, space.getHeight() + 1, 0)));
        event.setBuildable(true);
    }
}
