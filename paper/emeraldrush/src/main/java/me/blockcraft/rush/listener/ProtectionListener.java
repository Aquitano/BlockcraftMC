package me.blockcraft.rush.listener;

import me.blockcraft.api.world.BuildMode;
import me.blockcraft.rush.game.Game;
import me.blockcraft.rush.game.GamePhase;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ProtectionListener implements Listener {

    private static final double LOADOUT_SNAPSHOT_MIN_HEALTH = 5.0;

    private final Game game;
    private final BuildMode buildMode;

    public ProtectionListener(Game game, BuildMode buildMode) {
        this.game = game;
        this.buildMode = buildMode;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!game.phase().allowsDamage()) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player player) {
                game.returnToPhaseSpawn(player);
            }
            return;
        }
        if (event.getEntity() instanceof Player player && player.getHealth() >= LOADOUT_SNAPSHOT_MIN_HEALTH) {
            game.saveLoadout(player);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!game.phase().allowsDamage() && !buildMode.isEnabled(event.getPlayer())) {
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
        if (game.phase().locksInventory() && event.getWhoClicked() instanceof Player player && !buildMode.isEnabled(player)) {
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
}
