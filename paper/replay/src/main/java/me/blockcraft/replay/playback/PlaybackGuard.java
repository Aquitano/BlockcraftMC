package me.blockcraft.replay.playback;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import me.blockcraft.api.menu.Menu;
import me.blockcraft.common.message.Messages;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.Locale;
import java.util.Set;

final class PlaybackGuard implements Listener {

    private final PlaybackManager manager;
    private final Messages messages;

    PlaybackGuard(PlaybackManager manager, Messages messages) {
        this.manager = manager;
        this.messages = messages;
    }

    private boolean watching(Entity entity) {
        return entity instanceof Player player && manager.active(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (watching(event.getEntity()) || manager.replayWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (watching(event.getDamager())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (watching(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAttemptPickup(PlayerAttemptPickupItemEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExperience(PlayerPickupExperienceEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (watching(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (watching(event.getPlayer()) && !(event.getInventory().getHolder(false) instanceof Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (watching(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (watching(event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent event) {
        if (watching(event.getPlayer()) || manager.replayWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        if (watching(event.getPlayer()) || manager.replayWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFood(FoodLevelChangeEvent event) {
        if (watching(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotion(EntityPotionEffectEvent event) {
        if (watching(event.getEntity()) && event.getNewEffect() != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMount(EntityMountEvent event) {
        if (watching(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!manager.permittedWorld(event.getPlayer(), event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Portals have their own events, which the teleport guard never sees; a recorded map may contain some. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (watching(event.getPlayer()) || manager.replayWorld(event.getFrom().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPortal(EntityPortalEvent event) {
        if (manager.replayWorld(event.getFrom().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (watching(event.getPlayer()) && !event.isFlying()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!watching(event.getPlayer())) {
            return;
        }
        String command = event.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        command = command.substring(command.lastIndexOf(':') + 1);
        if (!Set.of("replay", "hub", "lobby").contains(command)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(messages.get("playback.command-denied"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { manager.hideFrom(event.getPlayer()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) { manager.quit(event.getPlayer()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpawn(EntitySpawnEvent event) {
        if (manager.replayWorld(event.getLocation().getWorld()) && !PlaybackCast.figure(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    /** Recorded undead would burn in the replay world's daylight. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCombust(EntityCombustEvent event) {
        if (manager.replayWorld(event.getEntity().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (manager.replayWorld(event.getEntity().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhysics(BlockPhysicsEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlow(BlockFromToEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGrow(BlockGrowEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSpread(BlockSpreadEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFade(BlockFadeEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onForm(BlockFormEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDecay(LeavesDecayEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(BlockIgniteEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstone(BlockRedstoneEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setNewCurrent(event.getOldCurrent()); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityBlock(EntityChangeBlockEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (manager.replayWorld(event.getLocation().getWorld())) { event.setCancelled(true); }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (manager.replayWorld(event.getBlock().getWorld())) { event.setCancelled(true); }
    }
}
