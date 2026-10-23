package me.blockcraft.api.game.rush;

import me.blockcraft.api.game.rush.RushConfig.Kit;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RushRules implements Listener {

    private final RushSession game;
    private final java.util.function.Predicate<Player> scoped;
    private final RushArena arena;
    private final Kit kit;
    private final Map<UUID, Integer> lastHits = new HashMap<>();

    public RushRules(RushSession game, RushArena arena, Kit kit) {
        this(game, arena, kit, game::scoped);
    }

    RushRules(RushSession game, RushArena arena, Kit kit, java.util.function.Predicate<Player> scoped) {
        this.game = game;
        this.scoped = scoped;
        this.arena = arena;
        this.kit = kit;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        var player = event.getPlayer();
        if (!scoped.test(player)) {
            return;
        }
        event.setCancelled(true);
        if (!game.active(player)) {
            return;
        }
        int owner = arena.bedOwner(event.getBlock());
        if (owner >= 0) {
            game.bed(player, owner);
        } else if (arena.remove(event.getBlock())) {
            event.getBlock().setType(Material.AIR, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        var player = event.getPlayer();
        if (!scoped.test(player)) {
            return;
        }
        var material = game.slot(player) == 0 ? Material.RED_TERRACOTTA : Material.BLUE_TERRACOTTA;
        if (!game.active(player) || !arena.canPlace(event.getBlock()) || event.getBlock().getType() != material
                || player.getInventory().getHeldItemSlot() != 1) {
            event.setCancelled(true);
            return;
        }
        arena.placed(event.getBlock());
        game.refill(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !scoped.test(player)) {
            return;
        }
        event.setCancelled(true);
        if (!(event instanceof EntityDamageByEntityEvent hit) || !(hit.getEntity() instanceof Player victim)
                || !(hit.getDamager() instanceof Player attacker) || attacker.equals(victim)
                || !game.active(attacker) || !game.active(victim)
                || hit.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || attacker.getInventory().getItemInMainHand().getType() != Material.STICK) {
            return;
        }
        int tick = Bukkit.getCurrentTick();
        var previous = lastHits.get(victim.getUniqueId());
        if (previous != null && tick - previous < kit.hitDelayTicks()) {
            return;
        }
        lastHits.put(victim.getUniqueId(), tick);
        var direction = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0);
        if (direction.lengthSquared() < 0.0001) {
            direction = attacker.getLocation().getDirection().setY(0);
        }
        if (direction.lengthSquared() > 0) {
            direction.normalize();
        }
        victim.setVelocity(direction.multiply(kit.horizontalPerLevel() * kit.knockbackLevel()).setY(kit.verticalKnockback()));
        victim.playHurtAnimation(attacker.getYaw());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (scoped.test(event.getPlayer())) {
            event.deathMessage(null);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            game.died(event.getPlayer());
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (scoped.test(event.getPlayer())) {
            game.respawn(event);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (scoped.test(event.getPlayer()) && event.getClickedBlock() != null && event.getAction().isRightClick()
                && event.getClickedBlock().getBlockData() instanceof Bed) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (scoped.test(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && scoped.test(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && scoped.test(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (scoped.test(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && scoped.test(player)
                && !(event.getView().getTopInventory().getHolder(false) instanceof me.blockcraft.api.menu.Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && scoped.test(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && scoped.test(player)
                && !(event.getInventory().getHolder(false) instanceof me.blockcraft.api.menu.Menu)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPhysics(BlockPhysicsEvent event) {
        if (arena.owns(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        if (arena.owns(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        if (arena.owns(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFlow(BlockFromToEvent event) {
        if (arena.owns(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (arena.owns(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (arena.owns(event.getLocation())) {
            event.setCancelled(true);
        }
    }
}
