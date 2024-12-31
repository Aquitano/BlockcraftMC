package me.blockcraft.duell.listeners;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

public class PreventListeners implements Listener {
    @EventHandler
    public void onFood(final FoodLevelChangeEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(final BlockBreakEvent e) {
        e.setCancelled(!e.getPlayer().isOp());
    }

    @EventHandler
    public void onBreak(final BlockPlaceEvent e) {
        e.setCancelled(!e.getPlayer().isOp());
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTrample(final PlayerInteractEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            final Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            final int blockType = block.getTypeId();
            if (blockType == Material.getMaterial(59).getId()) {
                event.setCancelled(true);
                block.setTypeId(blockType);
                block.setData(block.getData());
            }
        }
        if (event.getAction() == Action.PHYSICAL) {
            final Block block = event.getClickedBlock();
            if (block == null) {
                return;
            }
            final int blockType = block.getTypeId();
            if (blockType == Material.getMaterial(60).getId()) {
                event.setCancelled(true);
                block.setType(Material.getMaterial(60));
                block.setData(block.getData());
            }
        }
    }

    @EventHandler
    public void weatherChange(final WeatherChangeEvent event) {
        if (event.toWeatherState()) {
            final World world = event.getWorld();
            event.setCancelled(true);
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(0);
        }
    }

    @EventHandler
    public void onleaf(final LeavesDecayEvent e) {
        e.setCancelled(true);
    }

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(final EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            final Player p = (Player) e.getEntity();
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.LAVA) {
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.FIRE) {
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
                e.setCancelled(true);
            }
        }
    }
}
