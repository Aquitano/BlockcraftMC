package me.blockcraft.QuickSG.tools.items;

import me.blockcraft.MySQL.KitAPI;
import me.blockcraft.QuickSG.ItemAPI;
import me.blockcraft.QuickSG.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class KitSelector
        implements Listener {
    public static final ArrayList<Player> Kit1 = new ArrayList<>();
    public static final ArrayList<Player> Kit2 = new ArrayList<>();
    public static final ArrayList<Player> Kit3 = new ArrayList<>();
    public static final ArrayList<Player> Kit4 = new ArrayList<>();
    public static final ArrayList<Player> Kit5 = new ArrayList<>();
    public static final ArrayList<Player> Kit6 = new ArrayList<>();
    public static final ArrayList<Player> Kit7 = new ArrayList<>();
    public static final ArrayList<Player> Kit8 = new ArrayList<>();

    public static void openInventory(Player p) {
        if (Main.State_Lobby) {
            Inventory inv = Bukkit.createInventory(null, 9, "\u00a78Kits");
            inv.setItem(0, ItemAPI.Stack("\u00a7» \u00a7bStarter Kit", 268, "", 1, (short) 0));
            if (KitAPI.hasbuyedKit2(p)) {
                inv.setItem(1, ItemAPI.Stack("\u00a7» \u00a7bBogen Kit", 261, "", 1, (short) 0));
            } else {
                inv.setItem(1, ItemAPI.Stackench("\u00a7» \u00a7bBogen Kit", 261, "", 1, (short) 0));
            }
            if (KitAPI.hasbuyedKit3(p)) {
                inv.setItem(2, ItemAPI.Stack("\u00a7» \u00a7bHerzen Kit", 331, "", 1, (short) 0));
            } else {
                inv.setItem(2, ItemAPI.Stackench("\u00a7» \u00a7bHerzen Kit", 331, "", 1, (short) 0));
            }
            if (KitAPI.hasbuyedKit4(p)) {
                inv.setItem(3, ItemAPI.Stack("\u00a7» \u00a7bChain Kit", 303, "", 1, (short) 0));
            } else {
                inv.setItem(3, ItemAPI.Stackench("\u00a7» \u00a7bChain Kit", 303, "", 1, (short) 0));
            }
            if (KitAPI.hasbuyedKit5(p)) {
                inv.setItem(4, ItemAPI.Stack("\u00a7» \u00a7bDiamant Kit", 311, "", 1, (short) 0));
            } else {
                inv.setItem(4, ItemAPI.Stackench("\u00a7» \u00a7bDiamant Kit", 311, "", 1, (short) 0));
            }
            p.openInventory(inv);
        }
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Kits")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem() != null) {
                    if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bStarter Kit")) {
                        Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits \u00bb Starter Kit");
                        ItemStack item = new ItemStack(Material.WOOD_SWORD);
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a7» \u00a7bStarter Kit");
                        ArrayList<String> lore = new ArrayList<>();
                        lore.add(" \u00a7f» \u00a771 Holz Schwert");
                        lore.add("");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, ItemAPI.Stack("\u00a77Preis", 421, "\u00a7akostenlos", 1, (short) 0));
                        sk.setItem(3, ItemAPI.Stack("\u00a77Auswählen?", 420, "\u00a7a", 1, (short) 0));
                        sk.setItem(7, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bBogen Kit")) {
                        Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits » Bogen Kit");
                        ItemStack item = new ItemStack(Material.BOW);
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a7» \u00a7bBogen Kit");
                        ArrayList<String> lore = new ArrayList<>();
                        lore.add(" \u00a7f\u00bb \u00a771 Bogen");
                        lore.add(" \u00a7f» \u00a7712 Pfeile");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, ItemAPI.Stack("\u00a77Preis", 421, "\u00a7f200 Coins", 1, (short) 0));
                        sk.setItem(3, ItemAPI.Stack("\u00a77Auswählen?", 420, "\u00a7a", 1, (short) 0));
                        if (KitAPI.hasbuyedKit2(p)) {
                            sk.setItem(6, ItemAPI.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, ItemAPI.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bHerzen Kit")) {
                        Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits » Herzen Kit");
                        ItemStack item = new ItemStack(Material.REDSTONE);
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a7» \u00a7bHerzen Kit");
                        ArrayList<String> lore = new ArrayList<>();
                        lore.add(" \u00a7f» \u00a773 Herzen mehr");
                        lore.add("");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, ItemAPI.Stack("\u00a77Preis", 421, "\u00a7f200 Coins", 1, (short) 0));
                        sk.setItem(3, ItemAPI.Stack("\u00a77Auswählen?", 420, "\u00a7a", 1, (short) 0));
                        if (KitAPI.hasbuyedKit3(p)) {
                            sk.setItem(6, ItemAPI.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, ItemAPI.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bChain Kit")) {
                        Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits \u00bb Chain Kit");
                        ItemStack item = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a7» \u00a7bChain Kit");
                        ArrayList<String> lore = new ArrayList<>();
                        lore.add(" \u00a7f» \u00a771 Chain Helm");
                        lore.add(" \u00a7f» \u00a771 Chain Leggings");
                        lore.add(" \u00a7f» \u00a771 Chain Boots");
                        lore.add("");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, ItemAPI.Stack("\u00a77Preis", 421, "\u00a7f200 Coins", 1, (short) 0));
                        sk.setItem(3, ItemAPI.Stack("\u00a77Auswählen?", 420, "\u00a7a", 1, (short) 0));
                        if (KitAPI.hasbuyedKit4(p)) {
                            sk.setItem(6, ItemAPI.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, ItemAPI.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bDiamant Kit")) {
                        Inventory sk = Bukkit.createInventory(null, 9, "\u00a78Kits » Diamant Kit");
                        ItemStack item = new ItemStack(Material.DIAMOND_CHESTPLATE);
                        ItemMeta meta = item.getItemMeta();
                        meta.setDisplayName("\u00a7» \u00a7bDiamant Kit");
                        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
                        ArrayList<String> lore = new ArrayList<>();
                        lore.add(" \u00a7f» \u00a771 Diamanten Chestplate");
                        lore.add(" \u00a7f» \u00a771 Holz Schwert");
                        lore.add("");
                        meta.setLore(lore);
                        item.setItemMeta(meta);
                        sk.setItem(0, item);
                        sk.setItem(1, ItemAPI.Stack("\u00a77Preis", 421, "\u00a7f200 Coins", 1, (short) 0));
                        sk.setItem(3, ItemAPI.Stack("\u00a77Auswählen?", 420, "\u00a7a", 1, (short) 0));
                        if (KitAPI.hasbuyedKit5(p)) {
                            sk.setItem(6, ItemAPI.Stack("\u00a7abereits gekauft!", 160, "\u00a7a", 1, (short) 1));
                        } else {
                            sk.setItem(6, ItemAPI.Stack("\u00a7aKaufen?", 160, "\u00a7a", 1, (short) 13));
                        }
                        sk.setItem(7, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        sk.setItem(8, ItemAPI.Stack("\u00a7cAbrechen? / Zurück?", 160, "\u00a7a", 1, (short) 14));
                        p.openInventory(sk);
                    } else {
                        p.closeInventory();
                    }
                }
            } catch (Exception sk) {
                // empty catch block
            }
        }
    }
}

