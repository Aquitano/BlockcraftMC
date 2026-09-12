package me.blockcraft.lobby.listener;

import me.blockcraft.lobby.menu.Menus;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class ShopNpcListener implements Listener {

    private final Menus menus;

    public ShopNpcListener(Menus menus) {
        this.menus = menus;
    }

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                menus.openShop(event.getPlayer());
            }
        }
    }
}
