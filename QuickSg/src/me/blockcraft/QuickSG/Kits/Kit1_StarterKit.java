package me.blockcraft.QuickSG.Kits;

import me.blockcraft.QuickSG.Main;
import me.blockcraft.QuickSG.tools.Scoreboards.Scoreboard_Lobby;
import me.blockcraft.QuickSG.tools.items.KitSelector;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class Kit1_StarterKit
        implements Listener {
    @EventHandler
    public void on(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Kits » Starter Kit")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem() != null) {
                    if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a77Auswählen?")) {
                        KitSelector.Kit1.remove(p);
                        KitSelector.Kit2.remove(p);
                        KitSelector.Kit3.remove(p);
                        KitSelector.Kit4.remove(p);
                        KitSelector.Kit5.remove(p);
                        KitSelector.Kit6.remove(p);
                        KitSelector.Kit7.remove(p);
                        KitSelector.Kit8.remove(p);
                        p.sendMessage(Main.Prefix + "\u00a77Du hast das \u00a7fStarter Kit\u00a77 ausgewählt!");
                        KitSelector.Kit1.add(p);
                        p.closeInventory();
                        Scoreboard_Lobby.add(p);
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

