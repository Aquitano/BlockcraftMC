package me.blockcraft.rush.Shop;

import me.blockcraft.rush.RushManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class Extra implements Listener {
    @EventHandler
    public void onPlayerInteract(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Chain Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Eisen Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Diamanten Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Waffen") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Tränke") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Spezial")) {
            try {
                if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bVerzaubern")) {
                    p.teleport(RushManager.getLocation("Enchant"));
                }
            } catch (Exception ex) {
            }
        }
    }
}
