package me.blockcraft.rush.listener;

import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ProtectionListener implements Listener {

    private static final double LOADOUT_SNAPSHOT_MIN_HEALTH = 5.0;

    private final Game game;

    public ProtectionListener(Game game) {
        this.game = game;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!game.phase().allowsDamage()) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && player.getHealth() >= LOADOUT_SNAPSHOT_MIN_HEALTH) {
            game.saveLoadout(player);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!game.phase().allowsDamage() && !game.isBuilder(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && game.phase().locksInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (game.phase().locksInventory()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (game.phase().locksInventory() && event.getWhoClicked() instanceof Player player && !game.isBuilder(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (game.phase() == GamePhase.LOBBY && event.hasChangedBlock()
                && event.getPlayer().getLocation().getBlock().getType() == Material.WATER) {
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 5));
        }
    }

    @EventHandler
    public void onTrample(PlayerInteractEvent event) {
        var block = event.getClickedBlock();
        if (event.getAction() == Action.PHYSICAL && block != null && block.getType() == Material.FARMLAND) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }
}
