package me.blockcraft.rush.Shop;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import me.blockcraft.rush.Scoreboards.Scoreboard_Shopping;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Objects;

public class Potions implements Listener {

    public Potions() {

    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerInteract(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Chain Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Eisen Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Diamanten Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Waffen") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Tränke") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Spezial")) {
            e.setCancelled(true);
            if ((e.getCurrentItem() != null || e.getCurrentItem().getItemMeta() != null || Objects.equals(e.getCurrentItem().getItemMeta().getDisplayName(), "\u00a78» \u00a7bTränke")) && e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a7bTränke")) {
                final Inventory SetupInv = p.getServer().createInventory(null, 18, "\u00a73Shop \u00a77» \u00a78Tränke");
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
                MenuSpecial.setItemMeta(MenuSpecialMeta);
                final ItemStack MenuTraenke = new ItemStack(Material.ENCHANTMENT_TABLE);
                final ItemMeta MenuTraenkeMeta = MenuTraenke.getItemMeta();
                MenuTraenkeMeta.setDisplayName("\u00a78» \u00a7bVerzaubern");
                MenuTraenke.setItemMeta(MenuTraenkeMeta);
                final ItemStack MenuTr = new ItemStack(Material.POTION);
                final ItemMeta MenuTrM = MenuTr.getItemMeta();
                MenuTrM.setDisplayName("\u00a78» \u00a7bTränke");
                MenuTrM.addEnchant(Enchantment.DURABILITY, 1, true);
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
                final ItemStack MenuBlocksClay = new ItemStack(Material.POTION, 1);
                final ItemMeta MenuBlocksClayMeta = MenuBlocksClay.getItemMeta();
                MenuBlocksClayMeta.setDisplayName("\u00a78» \u00a79Jump");
                final ArrayList<String> lore1 = new ArrayList<>();
                lore1.add("\u00a78» \u00a77100 Emeralds");
                MenuBlocksClayMeta.setLore(lore1);
                final Potion jumpp = new Potion(PotionType.JUMP, 2);
                jumpp.setHasExtendedDuration(true);
                jumpp.setSplash(false);
                jumpp.apply(MenuBlocksClay);
                MenuBlocksClay.setItemMeta(MenuBlocksClayMeta);
                final ItemStack MenuBlocksClay2 = new ItemStack(Material.POTION, 3);
                final ItemMeta MenuBlocksClayMeta2 = MenuBlocksClay2.getItemMeta();
                MenuBlocksClayMeta2.setDisplayName("\u00a78» \u00a79Heal 1");
                final ArrayList<String> lore2 = new ArrayList<>();
                lore2.add("\u00a78» \u00a77100 Emeralds");
                MenuBlocksClayMeta2.setLore(lore2);
                final Potion jumpp2 = new Potion(PotionType.INSTANT_HEAL, 1);
                jumpp2.setSplash(true);
                jumpp2.apply(MenuBlocksClay2);
                MenuBlocksClay2.setItemMeta(MenuBlocksClayMeta2);
                final ItemStack MenuBlocksClay3 = new ItemStack(Material.POTION, 3);
                final ItemMeta MenuBlocksClayMeta3 = MenuBlocksClay3.getItemMeta();
                MenuBlocksClayMeta3.setDisplayName("\u00a78» \u00a79Heal 2");
                final ArrayList<String> lore3 = new ArrayList<>();
                lore3.add("\u00a78» \u00a77200 Emeralds");
                MenuBlocksClayMeta3.setLore(lore3);
                final Potion jumpp3 = new Potion(PotionType.INSTANT_HEAL, 2);
                jumpp3.setSplash(true);
                jumpp3.apply(MenuBlocksClay3);
                MenuBlocksClay3.setItemMeta(MenuBlocksClayMeta3);
                final ItemStack MenuBlocksClay4 = new ItemStack(Material.POTION, 1);
                final ItemMeta MenuBlocksClayMeta4 = MenuBlocksClay4.getItemMeta();
                MenuBlocksClayMeta4.setDisplayName("\u00a78» \u00a79Fire Resistance");
                final ArrayList<String> lore4 = new ArrayList<>();
                lore4.add("\u00a78» \u00a7770 Emeralds");
                MenuBlocksClayMeta4.setLore(lore1);
                final Potion jumpp4 = new Potion(PotionType.FIRE_RESISTANCE, 1);
                jumpp4.setSplash(false);
                jumpp4.apply(MenuBlocksClay4);
                MenuBlocksClay4.setItemMeta(MenuBlocksClayMeta4);
                final ItemStack MenuBlocksClay5 = new ItemStack(Material.POTION, 1);
                final ItemMeta MenuBlocksClayMeta5 = MenuBlocksClay5.getItemMeta();
                MenuBlocksClayMeta5.setDisplayName("\u00a78» \u00a79Speed");
                final ArrayList<String> lore5 = new ArrayList<>();
                lore5.add("\u00a78» \u00a77100 Emeralds");
                MenuBlocksClayMeta5.setLore(lore5);
                final Potion jumpp5 = new Potion(PotionType.SPEED, 1);
                jumpp5.setSplash(false);
                jumpp5.apply(MenuBlocksClay5);
                MenuBlocksClay5.setItemMeta(MenuBlocksClayMeta5);
                SetupInv.setItem(10, MenuBlocksClay);
                SetupInv.setItem(12, MenuBlocksClay2);
                SetupInv.setItem(13, MenuBlocksClay3);
                SetupInv.setItem(15, MenuBlocksClay4);
                SetupInv.setItem(16, MenuBlocksClay5);
                p.openInventory(SetupInv);
            }
        }
    }

    @EventHandler
    public void onClickLobby(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Tränke")) {
            try {
                if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Jump")) {
                    if (RushManager.getCoins(p) == 100) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                        final ItemStack MenuBlocksClay = new ItemStack(Material.POTION, 1);
                        final ItemMeta MenuBlocksClayMeta = MenuBlocksClay.getItemMeta();
                        final Potion jumpp = new Potion(PotionType.JUMP, 2);
                        jumpp.setHasExtendedDuration(true);
                        jumpp.setSplash(false);
                        jumpp.apply(MenuBlocksClay);
                        MenuBlocksClay.setItemMeta(MenuBlocksClayMeta);
                        p.getInventory().addItem(MenuBlocksClay);
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 100) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                        final ItemStack MenuBlocksClay = new ItemStack(Material.POTION, 1);
                        final ItemMeta MenuBlocksClayMeta = MenuBlocksClay.getItemMeta();
                        final Potion jumpp = new Potion(PotionType.JUMP, 2);
                        jumpp.setHasExtendedDuration(true);
                        jumpp.setSplash(false);
                        jumpp.apply(MenuBlocksClay);
                        MenuBlocksClay.setItemMeta(MenuBlocksClayMeta);
                        p.getInventory().addItem(MenuBlocksClay);
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Fire Resistance")) {
                    if (RushManager.getCoins(p) == 100) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                        final ItemStack MenuBlocksClay2 = new ItemStack(Material.POTION, 1);
                        final ItemMeta MenuBlocksClayMeta2 = MenuBlocksClay2.getItemMeta();
                        final Potion jumpp2 = new Potion(PotionType.FIRE_RESISTANCE, 1);
                        jumpp2.setSplash(false);
                        jumpp2.apply(MenuBlocksClay2);
                        MenuBlocksClay2.setItemMeta(MenuBlocksClayMeta2);
                        p.getInventory().addItem(MenuBlocksClay2);
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 100) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                        final ItemStack MenuBlocksClay2 = new ItemStack(Material.POTION, 1);
                        final ItemMeta MenuBlocksClayMeta2 = MenuBlocksClay2.getItemMeta();
                        final Potion jumpp2 = new Potion(PotionType.FIRE_RESISTANCE, 1);
                        jumpp2.setSplash(false);
                        jumpp2.apply(MenuBlocksClay2);
                        MenuBlocksClay2.setItemMeta(MenuBlocksClayMeta2);
                        p.getInventory().addItem(MenuBlocksClay2);
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Heal 1")) {
                    if (RushManager.getCoins(p) == 100) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                        final ItemStack MenuBlocksClay3 = new ItemStack(Material.POTION, 3);
                        final ItemMeta MenuBlocksClayMeta3 = MenuBlocksClay3.getItemMeta();
                        final Potion jumpp3 = new Potion(PotionType.INSTANT_HEAL, 1);
                        jumpp3.setSplash(true);
                        jumpp3.apply(MenuBlocksClay3);
                        MenuBlocksClay3.setItemMeta(MenuBlocksClayMeta3);
                        p.getInventory().addItem(MenuBlocksClay3);
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 100) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                        final ItemStack MenuBlocksClay3 = new ItemStack(Material.POTION, 3);
                        final ItemMeta MenuBlocksClayMeta3 = MenuBlocksClay3.getItemMeta();
                        final Potion jumpp3 = new Potion(PotionType.INSTANT_HEAL, 1);
                        jumpp3.setSplash(true);
                        jumpp3.apply(MenuBlocksClay3);
                        MenuBlocksClay3.setItemMeta(MenuBlocksClayMeta3);
                        p.getInventory().addItem(MenuBlocksClay3);
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Heal 2")) {
                    if (RushManager.getCoins(p) == 200) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 200);
                        final ItemStack MenuBlocksClay3 = new ItemStack(Material.POTION, 3);
                        final ItemMeta MenuBlocksClayMeta3 = MenuBlocksClay3.getItemMeta();
                        final Potion jumpp3 = new Potion(PotionType.INSTANT_HEAL, 2);
                        jumpp3.setSplash(true);
                        jumpp3.apply(MenuBlocksClay3);
                        MenuBlocksClay3.setItemMeta(MenuBlocksClayMeta3);
                        p.getInventory().addItem(MenuBlocksClay3);
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 200) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 200);
                        final ItemStack MenuBlocksClay3 = new ItemStack(Material.POTION, 3);
                        final ItemMeta MenuBlocksClayMeta3 = MenuBlocksClay3.getItemMeta();
                        final Potion jumpp3 = new Potion(PotionType.INSTANT_HEAL, 2);
                        jumpp3.setSplash(true);
                        jumpp3.apply(MenuBlocksClay3);
                        MenuBlocksClay3.setItemMeta(MenuBlocksClayMeta3);
                        p.getInventory().addItem(MenuBlocksClay3);
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Speed")) {
                    if (RushManager.getCoins(p) == 100) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 100);
                        final ItemStack MenuBlocksClay4 = new ItemStack(Material.POTION, 1);
                        final ItemMeta MenuBlocksClayMeta4 = MenuBlocksClay4.getItemMeta();
                        final Potion jumpp4 = new Potion(PotionType.JUMP, 2);
                        jumpp4.setSplash(false);
                        jumpp4.apply(MenuBlocksClay4);
                        MenuBlocksClay4.setItemMeta(MenuBlocksClayMeta4);
                        p.getInventory().addItem(MenuBlocksClay4);
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        final ItemStack MenuBlocksClay4 = new ItemStack(Material.POTION, 1);
                        final ItemMeta MenuBlocksClayMeta4 = MenuBlocksClay4.getItemMeta();
                        final Potion jumpp4 = new Potion(PotionType.SPEED, 1);
                        jumpp4.setSplash(false);
                        jumpp4.apply(MenuBlocksClay4);
                        MenuBlocksClay4.setItemMeta(MenuBlocksClayMeta4);
                        p.getInventory().addItem(MenuBlocksClay4);
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
