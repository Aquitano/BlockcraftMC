package me.blockcraft.QuickSG.Kits;

import me.blockcraft.MySQL.KitAPI;
import me.blockcraft.QuickSG.Main;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_Lobby;
import me.blockcraft.QuickSG.tools.items.KitSelector;
import me.blockcraft.coins.CAPI;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class Kit5_DiamantKit
        implements Listener {
    @EventHandler
    public void on(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Kits » Diamant Kit")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem() != null) {
                    if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a77Auswählen?")) {
                        if (KitAPI.hasbuyedKit5(p)) {
                            KitSelector.Kit1.remove(p);
                            KitSelector.Kit2.remove(p);
                            KitSelector.Kit3.remove(p);
                            KitSelector.Kit4.remove(p);
                            KitSelector.Kit5.remove(p);
                            KitSelector.Kit6.remove(p);
                            KitSelector.Kit7.remove(p);
                            KitSelector.Kit8.remove(p);
                            p.sendMessage(Main.Prefix + "\u00a77Du hast das \u00a7fDiamant Kit\u00a77 ausgewählt!");
                            KitSelector.Kit5.add(p);
                            p.closeInventory();
                            Scoreboard_Lobby.add(p);
                        } else {
                            p.closeInventory();
                            p.playSound(p.getLocation(), Sound.VILLAGER_NO, 4.0f, 4.0f);
                            p.sendMessage(Main.Prefix + "\u00a7cDu hast dieses Kit noch nicht gekauft!");
                        }
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7aKaufen?")) {
                        if (!KitAPI.hasbuyedKit5(p)) {
                            if (CAPI.getCoins(p.getUniqueId().toString()) > 199) {
                                KitAPI.setKit5(p.getUniqueId().toString(), p.getName());
                                CAPI.setCoins(p.getUniqueId().toString(), CAPI.getCoins(p.getUniqueId().toString()) - 200);
                                p.sendMessage(Main.Prefix + "\u00a77Du hast das \u00a7fDiamanten Kit \u00a7fgekauft!");
                                p.sendMessage(" \u00a7f» \u00a77- 200 Coins!");
                                p.playSound(p.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                                p.closeInventory();
                            } else {
                                p.closeInventory();
                                p.playSound(p.getLocation(), Sound.VILLAGER_NO, 4.0f, 4.0f);
                                p.sendMessage(Main.Prefix + "\u00a7cDu hast nicht genug Coins!");
                            }
                        } else {
                            p.closeInventory();
                            p.sendMessage(Main.Prefix + "\u00a7cDu hast dieses Kit bereits gekauft!");
                        }
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7cAbrechen? / Zurück?")) {
                        KitSelector.openInventory(p);
                    }
                }
            } catch (Exception var3_3) {
                // empty catch block
            }
        }
    }
}

