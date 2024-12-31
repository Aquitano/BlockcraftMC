package me.blockcraft.lobby.listeners;

import me.blockcraft.lobby.Lobby;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

public class DisableCMD implements Listener {
    public static Lobby main;

    public DisableCMD(final Lobby main) {
        DisableCMD.main = main;
    }

    @EventHandler
    public void onexecute(final PlayerCommandPreprocessEvent e) {
        final Player p = e.getPlayer();
        if (p.hasPermission("bs.bypass")) {
            e.setCancelled(false);
        } else e.setCancelled(!e.getMessage().equalsIgnoreCase("/coins"));
    }

    @EventHandler
    public void decay(final LeavesDecayEvent e) {
        e.setCancelled(true);
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

    @SuppressWarnings("unused")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(final EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            final Player p = (Player) e.getEntity();
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.LAVA) {
                e.getEntity().setFireTicks(0);
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.FIRE) {
                e.getEntity().setFireTicks(0);
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
                e.setCancelled(true);
            }
            if (e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
                e.getEntity().setFireTicks(0);
                e.setCancelled(true);
            }
        }
    }
}
