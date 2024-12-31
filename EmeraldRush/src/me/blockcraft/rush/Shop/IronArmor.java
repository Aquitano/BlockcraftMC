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

import java.util.ArrayList;

public class IronArmor implements Listener {

    public IronArmor() {
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerInteract(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Chain Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Eisen Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Diamanten Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Waffen") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Tränke") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Spezial")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.IRON_CHESTPLATE) {
                final Inventory SetupInv = p.getServer().createInventory(null, 18, "\u00a73Shop \u00a77» \u00a78Eisen Rüstung");
                final ItemStack MenuBlcke = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
                final ItemMeta MenuBlckeMeta = MenuBlcke.getItemMeta();
                MenuBlckeMeta.setDisplayName("\u00a78» \u00a7bChain");
                MenuBlcke.setItemMeta(MenuBlckeMeta);
                final ItemStack MenuSpitzhacken = new ItemStack(Material.IRON_CHESTPLATE);
                final ItemMeta MenuSpitzhackenMeta = MenuSpitzhacken.getItemMeta();
                MenuSpitzhackenMeta.setDisplayName("\u00a78» \u00a7bEisen");
                MenuSpitzhackenMeta.addEnchant(Enchantment.DURABILITY, 1, true);
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
                final ItemStack MenuBlocksClay = new ItemStack(Material.IRON_HELMET, 1);
                final ItemMeta MenuBlocksClayMeta = MenuBlocksClay.getItemMeta();
                MenuBlocksClayMeta.setDisplayName("\u00a78» \u00a79Eisen Helm");
                final ArrayList<String> lore1 = new ArrayList<>();
                lore1.add("\u00a78» \u00a7780 Emeralds");
                MenuBlocksClayMeta.setLore(lore1);
                MenuBlocksClay.setItemMeta(MenuBlocksClayMeta);
                final ItemStack MenuBlocksEndStein = new ItemStack(Material.IRON_CHESTPLATE);
                final ItemMeta MenuBlocksEndSteinMeta = MenuBlocksEndStein.getItemMeta();
                MenuBlocksEndSteinMeta.setDisplayName("\u00a78» \u00a79Eisen Brustpanzer");
                final ArrayList<String> lore2 = new ArrayList<>();
                lore2.add("\u00a78» \u00a7780 Emeralds");
                MenuBlocksEndSteinMeta.setLore(lore2);
                MenuBlocksEndStein.setItemMeta(MenuBlocksEndSteinMeta);
                final ItemStack MenuBlocksIronBlock = new ItemStack(Material.IRON_LEGGINGS);
                final ItemMeta MenuBlocksIronBlockMeta = MenuBlocksIronBlock.getItemMeta();
                MenuBlocksIronBlockMeta.setDisplayName("\u00a78» \u00a79Eisen Hose");
                final ArrayList<String> lore3 = new ArrayList<>();
                lore3.add("\u00a78» \u00a7780 Emeralds");
                MenuBlocksIronBlockMeta.setLore(lore3);
                MenuBlocksIronBlock.setItemMeta(MenuBlocksIronBlockMeta);
                final ItemStack MenuBlocksKohleBlock = new ItemStack(Material.IRON_BOOTS);
                final ItemMeta MenuBlocksKohleBlockMeta = MenuBlocksKohleBlock.getItemMeta();
                MenuBlocksKohleBlockMeta.setDisplayName("\u00a78» \u00a79Eisen Schuhe");
                final ArrayList<String> lore4 = new ArrayList<>();
                lore4.add("\u00a78» \u00a7780 Emeralds");
                MenuBlocksKohleBlockMeta.setLore(lore4);
                MenuBlocksKohleBlock.setItemMeta(MenuBlocksKohleBlockMeta);
                SetupInv.setItem(9, MenuBlocksClay);
                SetupInv.setItem(10, MenuBlocksEndStein);
                SetupInv.setItem(11, MenuBlocksIronBlock);
                SetupInv.setItem(12, MenuBlocksKohleBlock);
                p.openInventory(SetupInv);
            }
        }
    }

    @EventHandler
    public void onClickLobby(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Eisen Rüstung")) {
            try {
                if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Eisen Helm")) {
                    if (RushManager.getCoins(p) == 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_HELMET, 1));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_HELMET, 1));
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Eisen Brustpanzer")) {
                    if (RushManager.getCoins(p) == 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE, 1));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE, 1));
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Eisen Hose")) {
                    if (RushManager.getCoins(p) == 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_LEGGINGS, 1));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_LEGGINGS, 1));
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Eisen Schuhe")) {
                    if (RushManager.getCoins(p) == 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_BOOTS, 1));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 80) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 80);
                        p.getInventory().addItem(new ItemStack(Material.IRON_BOOTS, 1));
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
