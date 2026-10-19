package me.blockcraft.bedwars.listener;

import me.blockcraft.api.item.HotbarItem;
import me.blockcraft.bedwars.game.Game;
import me.blockcraft.bedwars.game.GameItem;
import me.blockcraft.bedwars.perk.StickSkin;
import org.bukkit.Bukkit;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/**
 * The jump and run's way back to its start, and what the perks do to items: the stick wears its holder's skin and the
 * plain stick once it leaves their hands, and an arrow leaves its shooter's trail.
 */
public final class ItemListener implements Listener {

    private final Plugin plugin;
    private final Game game;

    public ItemListener(Plugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
    }

    @EventHandler
    public void onHotbar(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !event.getAction().isRightClick()) {
            return;
        }
        HotbarItem.of(event.getItem(), GameItem.class).ifPresent(item -> {
            event.setCancelled(true);
            switch (item) {
                case JUMP_RESET -> game.jumpAndRun().ifPresent(run -> run.toStart(event.getPlayer()));
            }
        });
        if (StickSkin.isStick(event.getItem())) {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    /** A flag on a stick is still a stick, and does not go up as a banner. */
    @EventHandler
    public void onPlaceStick(BlockPlaceEvent event) {
        if (StickSkin.isStick(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && StickSkin.isStick(event.getItem().getItemStack())) {
            event.getItem().setItemStack(game.perks().stick(player).skin(event.getItem().getItemStack()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        var dropped = event.getItemDrop();
        if (StickSkin.isStick(dropped.getItemStack())) {
            dropped.setItemStack(StickSkin.STICK.skin(dropped.getItemStack()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        game.perks().arrowTrail(shooter).ifPresent(trail -> Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!arrow.isValid() || arrow.isInBlock()) {
                task.cancel();
                return;
            }
            trail.spawn(arrow.getLocation());
        }, 0L, 1L));
    }
}
