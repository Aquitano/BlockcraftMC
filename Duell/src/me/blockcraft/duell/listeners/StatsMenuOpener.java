package me.blockcraft.duell.listeners;

import me.blockcraft.coins.MySQL;
import me.blockcraft.duell.Duell;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.sql.ResultSet;
import java.sql.SQLException;

public class StatsMenuOpener implements Listener {
    public static Duell main;

    public StatsMenuOpener(final Duell main) {
        StatsMenuOpener.main = main;
    }

    @EventHandler
    public void onInteract(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_AIR && p.getItemInHand().getType() == Material.SKULL_ITEM) {
            StatsMenuOpener.main.openStatsInv(p);
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && p.getItemInHand().getType() == Material.SKULL_ITEM) {
            StatsMenuOpener.main.openStatsInv(p);
        }
    }

    @EventHandler
    public void onclick(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getClickedInventory() == null) {
            e.getWhoClicked().closeInventory();
            return;
        }
        if (p.getOpenInventory().getTopInventory() == null) {
            return;
        }
        if (e.getClickedInventory() == null) {
            return;
        }
        if (e.getCurrentItem() == null) {
            return;
        }
        if (e.getCurrentItem().getItemMeta() == null) {
            return;
        }
        if (e.getClickedInventory().getName().equals("Stats")) {
            e.setCancelled(true);
            if (e.getCurrentItem().getType() == Material.SKULL_ITEM) {
                if (e.getCurrentItem().getItemMeta().getDisplayName().equals("\u00a78● \u00a7bGLOBAL WINS \u00a78●")) {
                    e.getView().close();
                    p.sendMessage("\u00a77\u00a7m---------------------------------");
                    try {
                        final ResultSet rs = MySQL.getResult("SELECT `WIN`,`NAME` FROM `DUEL` ORDER BY WIN DESC LIMIT 10;");
                        int zahl = 0;
                        while (rs.next()) {
                            if (++zahl == 1) {
                                p.sendMessage("\u00a7e\u00a7l#1 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + "\u00a77 ( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 2) {
                                p.sendMessage("\u00a7r#2 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 3) {
                                p.sendMessage("\u00a7r#3 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 4) {
                                p.sendMessage("\u00a7r#4 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 5) {
                                p.sendMessage("\u00a7r#5 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 6) {
                                p.sendMessage("\u00a7r#6 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 7) {
                                p.sendMessage("\u00a7r#7 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 8) {
                                p.sendMessage("\u00a7r#8 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 9) {
                                p.sendMessage("\u00a7r#9 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                            if (zahl == 10) {
                                p.sendMessage("\u00a7r#10 " + ChatColor.GRAY + ChatColor.BLUE + rs.getString(2) + " \u00a77( " + ChatColor.WHITE + rs.getInt(1) + ChatColor.GRAY + " ) ");
                            }
                        }
                    } catch (SQLException x) {
                        x.printStackTrace();
                        System.out.println("FEHLER [4]");
                    }
                    p.sendMessage("\u00a77\u00a7m---------------------------------");
                } else {
                    e.getView().close();
                }
            }
        }
    }
}
