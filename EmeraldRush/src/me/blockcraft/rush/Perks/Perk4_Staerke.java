package me.blockcraft.rush.Perks;

import me.blockcraft.coins.CAPI;
import me.blockcraft.rush.Main;
import me.blockcraft.rush.MySQL.PerkAPI;
import me.blockcraft.rush.tools.KitSelector;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class Perk4_Staerke implements Listener {
    @EventHandler
    public void on(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Kits » Stärke Perk")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem() != null) {
                    if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7aKaufen?")) {
                        if (!PerkAPI.hasbuyedKit4(p)) {
                            if (CAPI.getCoins(p.getUniqueId().toString()) > 499) {
                                PerkAPI.setKit4(p.getUniqueId().toString(), p.getName());
                                CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 500);
                                p.sendMessage(Main.Prefix + "\u00a77Du hast das \u00a7fStärke Perk \u00a7fgekauft!");
                                p.sendMessage(" \u00a7f» \u00a77- 500 Coins!");
                                p.playSound(p.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                                p.closeInventory();
                            } else {
                                p.closeInventory();
                                p.playSound(p.getLocation(), Sound.VILLAGER_NO, 4.0f, 4.0f);
                                p.sendMessage(Main.Prefix + "\u00a7cDu hast nicht genug Coins!");
                            }
                        } else {
                            p.closeInventory();
                            p.sendMessage(Main.Prefix + "\u00a7cDu hast dieses Perk bereits gekauft!");
                        }
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7cAbrechen? / Zurück?")) {
                        KitSelector.openInventory(p);
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
}
