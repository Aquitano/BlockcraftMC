package me.blockcraft.lobby.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

public class DisableFoodLevel implements Listener {
    @EventHandler
    public void onFoodlevelChange(final FoodLevelChangeEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void onPcikup(final PlayerPickupItemEvent e) {
        e.setCancelled(true);
    }

    @EventHandler
    public void ontarget(final EntityTargetEvent e) {
        e.setCancelled(true);
    }
}
