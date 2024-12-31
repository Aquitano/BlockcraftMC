package me.blockcraft.rush.Shop;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.MySQL.PerkAPI;
import me.blockcraft.rush.RushManager;
import me.blockcraft.rush.Scoreboards.Scoreboard_Shopping;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;

public class Spezial implements Listener {

    public Spezial() {

    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerInteract(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Chain Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Eisen Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Diamanten Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Waffen") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Tränke") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Spezial")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.FIREWORK_CHARGE) {
                final Inventory SetupInv = p.getServer().createInventory(null, 18, "\u00a73Shop \u00a77» \u00a78Spezial");
                final ItemStack MenuBlcke = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
                final ItemMeta MenuBlckeMeta = MenuBlcke.getItemMeta();
                MenuBlckeMeta.setDisplayName("\u00a78» \u00a7bChain");
                MenuBlcke.setItemMeta(MenuBlckeMeta);
                final ItemStack MenuSpitzhacken = new ItemStack(Material.IRON_CHESTPLATE);
                final ItemMeta MenuSpitzhackenMeta = MenuSpitzhacken.getItemMeta();
                MenuSpitzhackenMeta.setDisplayName("\u00a78» \u00a7bEisen");
                MenuSpitzhacken.setItemMeta(MenuSpitzhackenMeta);
                final ItemStack MenuRuestung = new ItemStack(Material.IRON_SWORD);
                final ItemMeta MenuRuestungMeta = MenuRuestung.getItemMeta();
                MenuRuestungMeta.setDisplayName("\u00a78» \u00a7bWaffen");
                MenuRuestung.setItemMeta(MenuRuestungMeta);
                final ItemStack MenuSpecial = new ItemStack(Material.FIREWORK_CHARGE);
                final ItemMeta MenuSpecialMeta = MenuSpecial.getItemMeta();
                MenuSpecialMeta.setDisplayName("\u00a78» \u00a7bSpezial");
                MenuSpecialMeta.addEnchant(Enchantment.DURABILITY, 1, true);
                MenuSpecial.setItemMeta(MenuSpecialMeta);
                final ItemStack MenuTraenke = new ItemStack(Material.ENCHANTMENT_TABLE);
                final ItemMeta MenuTraenkeMeta = MenuTraenke.getItemMeta();
                MenuTraenkeMeta.setDisplayName("\u00a78» \u00a7bVerzaubern");
                MenuTraenke.setItemMeta(MenuTraenkeMeta);
                final ItemStack MenuTr = new ItemStack(Material.POTION);
                final ItemMeta MenuTrM = MenuTr.getItemMeta();
                MenuTrM.setDisplayName("\u00a78» \u00a7bTränke");
                MenuTr.setItemMeta(MenuTrM);
                final ItemStack WeissesGlas = new ItemStack(160, 1, (short) 0, (byte) 0);
                final ItemMeta WeissesGlasMeta = WeissesGlas.getItemMeta();
                WeissesGlasMeta.setDisplayName("\u00a7o");
                WeissesGlas.setItemMeta(WeissesGlasMeta);
                SetupInv.setItem(1, WeissesGlas);
                SetupInv.setItem(0, MenuRuestung);
                SetupInv.setItem(4, WeissesGlas);
                SetupInv.setItem(2, MenuBlcke);
                SetupInv.setItem(3, MenuSpitzhacken);
                SetupInv.setItem(5, MenuTr);
                SetupInv.setItem(6, MenuSpecial);
                SetupInv.setItem(7, WeissesGlas);
                SetupInv.setItem(8, MenuTraenke);
                final ItemStack MenuBlocksKohleBlock = new ItemStack(Material.RED_ROSE);
                final ItemMeta MenuBlocksKohleBlockMeta = MenuBlocksKohleBlock.getItemMeta();
                MenuBlocksKohleBlockMeta.setDisplayName("\u00a78» \u00a74Extra Herz");
                final ArrayList<String> lore4 = new ArrayList<>();
                lore4.add("\u00a78» \u00a7740 Emeralds");
                MenuBlocksKohleBlockMeta.setLore(lore4);
                MenuBlocksKohleBlock.setItemMeta(MenuBlocksKohleBlockMeta);
                SetupInv.setItem(9, MenuBlocksKohleBlock);
                final ItemStack MenuBlocksKohleBlock2 = new ItemStack(Material.GOLDEN_APPLE);
                final ItemMeta MenuBlocksKohleBlockMeta2 = MenuBlocksKohleBlock2.getItemMeta();
                MenuBlocksKohleBlockMeta2.setDisplayName("\u00a78» \u00a7eGoldapfel");
                final ArrayList<String> lore5 = new ArrayList<>();
                lore5.add("\u00a78» \u00a7735 Emeralds");
                MenuBlocksKohleBlockMeta2.setLore(lore5);
                MenuBlocksKohleBlock2.setItemMeta(MenuBlocksKohleBlockMeta2);
                SetupInv.setItem(10, MenuBlocksKohleBlock2);
                final ItemStack Level = new ItemStack(Material.ENCHANTED_BOOK);
                final ItemMeta LevelMeta = Level.getItemMeta();
                LevelMeta.setDisplayName("\u00a78» \u00a7f1 Level");
                final ArrayList<String> lore6 = new ArrayList<>();
                lore6.add("\u00a78» \u00a7730 Emeralds");
                LevelMeta.setLore(lore6);
                Level.setItemMeta(LevelMeta);
                SetupInv.setItem(11, Level);
                final ItemStack Lapis = new ItemStack(RushManager.Stack("", 351, "", 1, (short) 4));
                final ItemMeta LapisMeta = Lapis.getItemMeta();
                LapisMeta.setDisplayName("\u00a78» \u00a79Lapis");
                final ArrayList<String> lore7 = new ArrayList<>();
                lore7.add("\u00a78» \u00a7720 Emeralds");
                LapisMeta.setLore(lore7);
                Lapis.setItemMeta(LapisMeta);
                SetupInv.setItem(12, Lapis);
                if (PerkAPI.hasbuyedKit1(p)) {
                    SetupInv.setItem(14, RushManager.Stack("\u00a78» \u00a7bAngel", 346, "\u00a77100 Emeralds", 1, (short) 0));
                }
//                else {
//                    SetupInv.setItem(14, RushManager.Stackench("\u00a78» \u00a7bAngel", 346, "\u00a77100 Emeralds", 1, (short) 0));
//                }
                if (PerkAPI.hasbuyedKit3(p)) {
                    SetupInv.setItem(15, RushManager.Stack("\u00a78» \u00a7bLuckyPotion", 373, "\u00a77400 Emeralds", 1, (short) 0));
                }
//                else {
//                    SetupInv.setItem(15, RushManager.Stackench("\u00a78» \u00a7bLuckyPotion", 373, "\u00a77400 Emeralds", 1, (short) 0));
//                }
                if (PerkAPI.hasbuyedKit4(p)) {
                    SetupInv.setItem(16, RushManager.Stack("\u00a78» \u00a7bStaerke Potion", 331, "\u00a77500 Emeralds", 1, (short) 0));
                }
//                else {
//                    SetupInv.setItem(16, RushManager.Stackench("\u00a78» \u00a7bStaerke Potion", 331, "\u00a77300 Emeralds", 1, (short) 0));
//                }
                p.openInventory(SetupInv);
            }
        }
    }

    @EventHandler
    public void onClickLobby(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Spezial")) {
            try {
                if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a74Extra Herz")) {
                    if (RushManager.getCoins(p) == 40) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 40);
                        Scoreboard_Shopping.add(p);
                        p.setMaxHealth(p.getMaxHealth() + 2.0);
                        p.setHealth(p.getHealth() + 2.0);
                    } else if (RushManager.getCoins(p) >= 40) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 40);
                        Scoreboard_Shopping.add(p);
                        p.setMaxHealth(p.getMaxHealth() + 2.0);
                        p.setHealth(p.getHealth() + 2.0);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7eGoldapfel")) {
                    if (RushManager.getCoins(p) == 35) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 35);
                        Scoreboard_Shopping.add(p);
                        p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
                    } else if (RushManager.getCoins(p) >= 35) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 35);
                        Scoreboard_Shopping.add(p);
                        p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE));
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7f1 Level")) {
                    if (RushManager.getCoins(p) == 30) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 30);
                        Scoreboard_Shopping.add(p);
                        p.setLevel(p.getLevel() + 1);
                        p.playSound(p.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                    } else if (RushManager.getCoins(p) >= 35) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 30);
                        Scoreboard_Shopping.add(p);
                        p.setLevel(p.getLevel() + 1);
                        p.playSound(p.getLocation(), Sound.LEVEL_UP, 4.0f, 4.0f);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bAngel")) {
                    if (PerkAPI.hasbuyedKit1(p)) {
                        if (RushManager.getCoins(p) == 100) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(new ItemStack(Material.FISHING_ROD));
                        } else if (RushManager.getCoins(p) >= 35) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(new ItemStack(Material.FISHING_ROD));
                        } else {
                            p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                        }
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bInstant TNT")) {
                    if (PerkAPI.hasbuyedKit2(p)) {
                        if (RushManager.getCoins(p) == 80) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(new ItemStack(Material.TNT));
                        } else if (RushManager.getCoins(p) >= 80) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(new ItemStack(Material.TNT));
                        } else {
                            p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                        }
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu musst dir dieses Item noch im Perk Shop kaufen!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bLuckyPotion")) {
                    if (PerkAPI.hasbuyedKit3(p)) {
                        if (RushManager.getCoins(p) == 400) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 400);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(RushManager.Stack("", 373, "", 1, (short) 8238));
                        } else if (RushManager.getCoins(p) >= 400) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 400);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(RushManager.Stack("", 373, "", 1, (short) 8238));
                        } else {
                            p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                        }
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu musst dir dieses Item noch im Perk Shop kaufen!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bStaerke Potion")) {
                    if (PerkAPI.hasbuyedKit4(p)) {
                        if (RushManager.getCoins(p) == 500) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 500);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(RushManager.Stack("", 373, "", 1, (short) 8201));
                        } else if (RushManager.getCoins(p) >= 500) {
                            RushManager.setCoins(p, RushManager.getCoins(p) - 500);
                            Scoreboard_Shopping.add(p);
                            p.getInventory().addItem(RushManager.Stack("", 373, "", 1, (short) 8201));
                        } else {
                            p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                        }
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu musst dir dieses Item noch im Perk Shop kaufen!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Lapis")) {
                    if (RushManager.getCoins(p) == 20) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 20);
                        p.getInventory().addItem(RushManager.Stack("", 351, "", 1, (short) 4));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 20) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 20);
                        p.getInventory().addItem(RushManager.Stack("", 351, "", 1, (short) 4));
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                }
            } catch (Exception ex) {
            }
        }
    }
}
