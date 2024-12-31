package me.blockcraft.rush.tools;

import me.blockcraft.rush.Main;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class WaterJump implements Listener {
    @EventHandler
    public void on(final PlayerMoveEvent e) {
        final Player p = e.getPlayer();
        if (Main.State_Lobby && (p.getLocation().getBlock().getType() == Material.STATIONARY_WATER || p.getLocation().getBlock().getType() == Material.WATER)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 60, 5));
        }
    }
}
