package me.blockcraft.rush.Shop;

import me.blockcraft.rush.Main;
import me.blockcraft.rush.RushManager;
import me.blockcraft.rush.Scoreboards.Scoreboard_Shopping;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class ChainArmor implements Listener {
    @EventHandler
    public void onPlayerInteract(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Chain Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Eisen Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Diamanten Rüstung") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Waffen") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Tränke") || e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Spezial")) {
            e.setCancelled(true);
            if (e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.CHAINMAIL_CHESTPLATE) {
                ChainArmor2.openInventory(p);
            }
        }
    }

    @EventHandler
    public void onClickLobby(final InventoryClickEvent e) {
        final Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a73Shop \u00a77» \u00a78Chain Rüstung")) {
            try {
                if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Chain Helm")) {
                    if (RushManager.getCoins(p) == 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_HELMET, 1));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_HELMET, 1));
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Chain Chestplate")) {
                    if (RushManager.getCoins(p) == 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Chain Hose")) {
                    if (RushManager.getCoins(p) == 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
                        Scoreboard_Shopping.add(p);
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
                        Scoreboard_Shopping.add(p);
                    } else {
                        p.sendMessage(Main.Prefix + "\u00a7cDu hast zu wenig Emeralds!");
                    }
                } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a78» \u00a79Chain Schuhe")) {
                    if (RushManager.getCoins(p) == 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
                        Scoreboard_Shopping.add(p);
                    } else if (RushManager.getCoins(p) >= 50) {
                        RushManager.setCoins(p, RushManager.getCoins(p) - 50);
                        p.getInventory().addItem(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
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
