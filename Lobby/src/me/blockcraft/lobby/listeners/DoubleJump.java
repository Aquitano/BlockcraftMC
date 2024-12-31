package me.blockcraft.lobby.listeners;

import me.blockcraft.lobby.Lobby;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

public class DoubleJump implements Listener {
    public static Lobby main;
    public final double height;
    public final double multiply;

    public DoubleJump(final Lobby main) {
        this.height = 0.6;
        this.multiply = 1.0;
        DoubleJump.main = main;
    }

    @EventHandler
    public void onMove(final PlayerMoveEvent e) {
        if (e.getPlayer().hasPermission("bs.doublejump") && e.getPlayer().getGameMode() != GameMode.CREATIVE && e.getPlayer().getLocation().getBlock().getRelative(BlockFace.DOWN).getType() != Material.AIR) {
            e.getPlayer().setAllowFlight(true);
        }
    }

    @EventHandler
    public void onFallDamage(final EntityDamageEvent e) {
        final Entity entity = e.getEntity();
        if (entity instanceof Player && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFly(final PlayerToggleFlightEvent e) {
        final Player p = e.getPlayer();
        if (p.hasPermission("bs.doublejump") && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setVelocity(p.getLocation().getDirection().multiply(this.multiply).setY(this.height));
            p.setFallDistance(0.0f);
            p.playSound(p.getLocation(), Sound.ENDERDRAGON_WINGS, 4.0f, 4.0f);
        }
    }

    @EventHandler
    public void onMoves(final PlayerMoveEvent e) {
        e.getPlayer().setFallDistance(-100.0f);
        final Player p = e.getPlayer();
        final Location loc = e.getPlayer().getLocation();
        final Block b = p.getWorld().getBlockAt(loc);
        if (b.getType() == Material.GOLD_PLATE) {
            final Vector dir = p.getLocation().getDirection();
            dir.setY(0.18);
            p.playSound(p.getLocation(), Sound.ARROW_HIT, 1.0f, 0.0f);
            p.playEffect(p.getLocation(), Effect.CRIT, null);
            if (dir.multiply(5.0).length() > 6.0) {
                return;
            }
            p.setVelocity(dir);
        }
    }
}
