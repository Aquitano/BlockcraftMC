package me.blockcraft.rush.tools;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.MySQL.PerkAPI;
import me.blockcraft.rush.RushManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class KitSelector implements Listener {
    public static ArrayList<Player> Kit1;
    public static ArrayList<Player> Kit2;
    public static ArrayList<Player> Kit3;
    public static ArrayList<Player> Kit4;
    public static ArrayList<Player> Kit5;
    public static ArrayList<Player> Kit6;
    public static ArrayList<Player> Kit7;
    public static ArrayList<Player> Kit8;

    static {
        KitSelector.Kit1 = new ArrayList<>();
        KitSelector.Kit2 = new ArrayList<>();
        KitSelector.Kit3 = new ArrayList<>();
        KitSelector.Kit4 = new ArrayList<>();
        KitSelector.Kit5 = new ArrayList<>();
        KitSelector.Kit6 = new ArrayList<>();
        KitSelector.Kit7 = new ArrayList<>();
        KitSelector.Kit8 = new ArrayList<>();
    }

    public static void openInventory(final Player p) {
        if (Main.State_Lobby) {
            final Inventory inv = Bukkit.createInventory(null, 9, "\u00a78Kits");
            if (PerkAPI.hasbuyedKit1(p)) {
                inv.setItem(0, RushManager.Stack("\u00a78» \u00a7bAngel Perk", 346, "", 1, (short) 0));
            } else {
                inv.setItem(0, RushManager.Stackench("\u00a78» \u00a7bAngel Perk", 346, "", 1, (short) 0));
            }
            if (PerkAPI.hasbuyedKit3(p)) {
                inv.setItem(1, RushManager.Stack("\u00a78» \u00a7bLuckyPotion Perk", 373, "", 1, (short) 0));
            } else {
                inv.setItem(1, RushManager.Stackench("\u00a78» \u00a7bLuckyPotion Perk", 373, "", 1, (short) 0));
            }
            if (PerkAPI.hasbuyedKit4(p)) {
                inv.setItem(2, RushManager.Stack("\u00a78» \u00a7bStärke Potion Perk", 331, "", 1, (short) 0));
            } else {
                inv.setItem(2, RushManager.Stackench("\u00a78» \u00a7bStärke Potion Perk", 331, "", 1, (short) 0));
            }
            final ItemStack item = new ItemStack(Material.SIGN);
            final ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("\u00a78» \u00a76Erklärung");
            final ArrayList<String> lore = new ArrayList<>();
            lore.add("\u00a7fDurch den kauf eines Perks");
            lore.add("\u00a7fschaltest du dir die Funktion");
            lore.add("\u00a7ffrei, es im Shop zu kaufen!");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(8, item);
            p.openInventory(inv);
        }
    }

    @EventHandler
    public void on(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Kits")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem() != null) {
                    if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bAngel Perk")) {
                        final Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits » Angel Perk");
                        final ItemStack item = new ItemStack(Material.FISHING_ROD);
                        final ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a78» \u00a7bAngel Perk");
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, RushManager.Stack("\u00a77Preis", 421, "\u00a7f200 Coins", 1, (short) 0));
                        if (PerkAPI.hasbuyedKit1(p)) {
                            sk.setItem(6, RushManager.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, RushManager.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bLuckyPotion Perk")) {
                        final Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits » LuckyPotion Perk");
                        final ItemStack item = new ItemStack(Material.POTION);
                        final ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a78» \u00a7bLuckyPotion Perk");
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, RushManager.Stack("\u00a77Preis", 421, "\u00a7f300 Coins", 1, (short) 0));
                        if (PerkAPI.hasbuyedKit3(p)) {
                            sk.setItem(6, RushManager.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, RushManager.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bStärke Potion Perk")) {
                        final Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits » Stärke Perk");
                        final ItemStack item = new ItemStack(Material.REDSTONE);
                        final ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a78» \u00a7bStärke Potion Perk");
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, RushManager.Stack("\u00a77Preis", 421, "\u00a7f500 Coins", 1, (short) 0));
                        if (PerkAPI.hasbuyedKit4(p)) {
                            sk.setItem(6, RushManager.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, RushManager.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bCobweb Perk")) {
                        final Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits » Cobweb Perk");
                        final ItemStack item = new ItemStack(Material.WEB);
                        final ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a78» \u00a7bCobweb Perk");
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, RushManager.Stack("\u00a77Preis", 421, "\u00a7f150 Coins", 1, (short) 0));
                        if (PerkAPI.hasbuyedKit5(p)) {
                            sk.setItem(6, RushManager.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, RushManager.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, RushManager.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else {
                        p.closeInventory();
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
}
