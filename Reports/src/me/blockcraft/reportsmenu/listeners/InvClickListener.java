package me.blockcraft.reportsmenu.listeners;

import me.blockcraft.reportsmenu.commands.ReportsCommand;
import me.blockcraft.reportsmenu.util.AnvilGUI;
import me.blockcraft.reportsmenu.util.ReportsManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class InvClickListener implements Listener {

    public InvClickListener() {
    }

    @EventHandler
    public void onInvClick(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null) {
            return;
        }
        if (e.getClickedInventory().getTitle().equals("§cReports")) {
            if (e.getCurrentItem() == null | e.getCurrentItem().getData().getItemType() == Material.AIR) {
                return;
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§4Schließen")) {
                e.setCancelled(true);
                p.closeInventory();
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§aNächste Seite")) {
                e.setCancelled(true);
                final Integer nextp = Integer.parseInt(e.getClickedInventory().getItem(49).getItemMeta().getLore().get(0).substring(20, 21)) + 1;
                ReportsCommand.openReportsInv(p, nextp);
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§cVorherige Seite")) {
                e.setCancelled(true);
                final Integer nextp = Integer.parseInt(e.getClickedInventory().getItem(49).getItemMeta().getLore().get(0).substring(20, 21)) - 1;
                ReportsCommand.openReportsInv(p, nextp);
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§6Aktuelle Seite")) {
                e.setCancelled(true);
            }
            if (e.getCurrentItem().getData().getItemType() == Material.IRON_FENCE | e.getCurrentItem().getData().getItemType() == Material.AIR) {
                e.setCancelled(true);
            }
            if (e.getCurrentItem().getData().getItemType() == Material.SKULL_ITEM) {
                final Integer id = Integer.valueOf(e.getCurrentItem().getItemMeta().getLore().get(3).replace("§7ReportID: §e", ""));
                e.setCancelled(true);
                ReportsCommand.openReportMenu(p, id, false);
            }
        }
        if (e.getClickedInventory().getTitle().equals("§7Player§cReports")) {
            if (e.getCurrentItem() == null | e.getCurrentItem().getData().getItemType() == Material.AIR) {
                return;
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§4Schließen")) {
                e.setCancelled(true);
                p.closeInventory();
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§aNächste Seite")) {
                e.setCancelled(true);
                final Integer nextp = Integer.parseInt(e.getClickedInventory().getItem(49).getItemMeta().getLore().get(0).substring(20, 21)) + 1;
                final String target = e.getClickedInventory().getItem(45).getItemMeta().getDisplayName().replace("§c", "");
                ReportsCommand.openPlayerReportsInv(p, target, nextp);
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§cVorherige Seite")) {
                e.setCancelled(true);
                final Integer nextp = Integer.parseInt(e.getClickedInventory().getItem(49).getItemMeta().getLore().get(0).substring(20, 21)) - 1;
                final String target = e.getClickedInventory().getItem(45).getItemMeta().getDisplayName().replace("§c", "");
                ReportsCommand.openPlayerReportsInv(p, target, nextp);
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§6Aktuelle Seite")) {
                e.setCancelled(true);
            }
            if (e.getCurrentItem().getData().getItemType() == Material.IRON_FENCE | e.getCurrentItem().getData().getItemType() == Material.AIR) {
                e.setCancelled(true);
            }
            if (e.getCurrentItem().getData().getItemType() == Material.SKULL_ITEM && !e.getCurrentItem().getItemMeta().getDisplayName().contains("§c")) {
                final Integer id = Integer.valueOf(e.getCurrentItem().getItemMeta().getLore().get(3).replace("§7ReportID: §e", ""));
                e.setCancelled(true);
                ReportsCommand.openReportMenu(p, id, true);
            }
            if (e.getCurrentItem().getData().getItemType() == Material.SKULL_ITEM && e.getCurrentItem().getItemMeta().getDisplayName().contains("§c")) {
                e.setCancelled(true);
            }
        }
        if (e.getClickedInventory().getTitle().contains("§eReport: §a")) {
            if (e.getCurrentItem() == null | e.getCurrentItem().getData().getItemType() == Material.AIR) {
                return;
            }
            e.setCancelled(true);
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§4Schließen")) {
                e.setCancelled(true);
                p.closeInventory();
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§cZurück")) {
                e.setCancelled(true);
                if (e.getClickedInventory().getItem(4).getItemMeta().getLore().get(0).equals("§cPlayerReports")) {
                    final String target2 = e.getClickedInventory().getItem(5).getItemMeta().getLore().get(0).replace("§c", "");
                    ReportsCommand.openPlayerReportsInv(p, target2, 1);
                } else {
                    ReportsCommand.openReportsInv(p, 1);
                }
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§4Löschen")) {
                e.setCancelled(true);
                final Integer ID = Integer.valueOf(e.getClickedInventory().getItem(4).getItemMeta().getDisplayName().replace("§eReportID: §6", ""));
                ReportsManager.delReport(ID);
                if (e.getClickedInventory().getItem(4).getItemMeta().getLore().get(0).equals("§cPlayerReports")) {
                    final String target = e.getClickedInventory().getItem(5).getItemMeta().getLore().get(0).replace("§c", "");
                    p.closeInventory();
                    ReportsCommand.openPlayerReportsInv(p, target, 1);
                } else {
                    p.closeInventory();
                    ReportsCommand.openReportsInv(p, 1);
                }
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§9Status setzen")) {
                e.setCancelled(true);
                final AnvilGUI gui = new AnvilGUI(p, event -> {
                    if (event.getSlot() == AnvilGUI.AnvilSlot.OUTPUT) {
                        event.setWillClose(false);
                        event.setWillDestroy(false);
                        final Integer ID = Integer.valueOf(e.getClickedInventory().getItem(4).getItemMeta().getDisplayName().replace("§eReportID: §6", ""));
                        ReportsManager.updateStatus(event.getName(), ID);
                        ReportsCommand.openReportMenu(p, ID, !e.getClickedInventory().getItem(4).getItemMeta().getLore().get(0).replace("§c", "").equals("§cPlayerReports"));
                    } else {
                        event.setWillClose(false);
                        event.setWillDestroy(false);
                    }
                });
                gui.setSlot(AnvilGUI.AnvilSlot.INPUT_LEFT, new ItemStack(Material.NAME_TAG));
                gui.open();
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§5Zum Beschuldigten")) {
                e.setCancelled(true);
                final String target2 = e.getClickedInventory().getItem(5).getItemMeta().getLore().get(0).replace("§c", "");
                p.closeInventory();
                p.chat("/find " + target2);
            }
            if (e.getCurrentItem().getItemMeta().getDisplayName().equals("§5Zum Reporter")) {
                e.setCancelled(true);
                final String target2 = e.getClickedInventory().getItem(3).getItemMeta().getLore().get(0).replace("§c", "");
                p.closeInventory();
                p.chat("/find " + target2);
            }
        }
    }
}
