package me.blockcraft.QuickSG.tools.items;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.blockcraft.MySQL.StatsAPI;
import me.blockcraft.QuickSG.ItemAPI;
import me.blockcraft.QuickSG.Main;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;

public class JoinItems
        implements Listener {
    @EventHandler
    public void on(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && p.getItemInHand().getType() == Material.SLIME_BALL) {
            e.setCancelled(true);
            this.connect(p, "lobby");
        }
    }

    @EventHandler
    public void on1(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if ((p.hasPermission("bs.startGame")) &&
                (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && p.getItemInHand().getType() == Material.DIAMOND) {
            e.setCancelled(true);
            p.chat("/start");
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void on3(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && p.getItemInHand().getType() == Material.SKULL_ITEM) {
            e.setCancelled(true);
            Inventory inv = Bukkit.createInventory(null, 18, "\u00a78Stats");
            for (Player all : Bukkit.getOnlinePlayers()) {
                ItemStack stack = new ItemStack(397, 1, (short) 3);
                SkullMeta meta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.SKULL_ITEM);
                meta.setDisplayName("\u00a7e" + all.getName());
                ArrayList<String> lore = new ArrayList<>();
                lore.add("\u00a7» \u00a79\u00a7lWins: \u00a77" + StatsAPI.getWins(all.getUniqueId().toString()));
                lore.add("\u00a7» \u00a79\u00a7lLoses: \u00a77" + StatsAPI.getLoses(all.getUniqueId().toString()));
                lore.add("\u00a7» \u00a79\u00a7lDeaths: \u00a77" + StatsAPI.getDeaths(all.getUniqueId().toString()));
                lore.add("\u00a7» \u00a79\u00a7lKills: \u00a77" + StatsAPI.getKills(all.getUniqueId().toString()));
                meta.setLore(lore);
                stack.setItemMeta(meta);
                inv.addItem(stack);
                inv.setItem(13, ItemAPI.Skull("\u00a7» \u00a7bGlobal Stats", 397, "", 1, (short) 3, "Kevos"));
            }
            p.openInventory(inv);
        }
    }

    public void connect(Player player, String servername) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(servername);
        player.sendPluginMessage(Main.getInstance(), "BungeeCord", out.toByteArray());
    }

    @EventHandler
    public void on(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Stats")) {
            e.setCancelled(true);
        }
        if (p.getItemInHand() != null && p.getItemInHand().getType() == Material.SLIME_BALL) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void on1(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Stats")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem() != null && e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bGlobal Stats")) {
                    Inventory inv = Bukkit.createInventory(null, 9, "\u00a78Stats » Global");
                    inv.setItem(0, ItemAPI.Stack("\u00a7» \u00a7bWins", 339, "", 1, (short) 0));
                    inv.setItem(2, ItemAPI.Stack("\u00a7» \u00a7bDeaths", 339, "", 1, (short) 0));
                    inv.setItem(4, ItemAPI.Stack("\u00a7» \u00a7bKills", 339, "", 1, (short) 0));
                    inv.setItem(6, ItemAPI.Stack("\u00a7» \u00a7bVerlorene Spiele", 339, "", 1, (short) 0));
                    inv.setItem(8, ItemAPI.Stack("\u00a7» \u00a7bQuickSG Points", 339, "", 1, (short) 0));
                    p.openInventory(inv);
                }
            } catch (Exception inv) {
                // empty catch block
            }
        }
    }

    @EventHandler
    public void on2(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        if (e.getInventory().getName().equalsIgnoreCase("\u00a78Stats » Global")) {
            e.setCancelled(true);
            try {
                if (e.getCurrentItem() != null) {
                    if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bWins")) {
                        p.closeInventory();
                        p.chat("/top wins");
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bQuickSG Points")) {
                        p.closeInventory();
                        p.chat("/top points");
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bDeaths")) {
                        p.closeInventory();
                        p.chat("/top deaths");
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bKills")) {
                        p.closeInventory();
                        p.chat("/top kills");
                    } else if (e.getCurrentItem().getItemMeta().getDisplayName().equalsIgnoreCase("\u00a7» \u00a7bVerlorene Spiele")) {
                        p.chat("/top loses");
                        p.closeInventory();
                    } else {
                        p.closeInventory();
                    }
                }
            } catch (Exception var3_3) {
                // empty catch block
            }
        }
    }
}

