package me.blockcraft.duell.listeners.sg;

import me.blockcraft.duell.Duell;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ChestOpenerSG implements Listener {
    public static Duell main;

    public ChestOpenerSG(final Duell main) {
        ChestOpenerSG.main = main;
    }

    @EventHandler
    public void onInteractChest(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        if (ChestOpenerSG.main.SG && e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.CHEST) {
            if (!ChestOpenerSG.main.sgchest.containsKey(e.getClickedBlock().getLocation())) {
                e.setCancelled(true);
                p.playSound(p.getLocation(), Sound.CHEST_OPEN, 10.0f, 1.0f);
                final Random rnd = new Random();
                int n;
                n = rnd.nextInt(6) + 1;
                final Inventory inv = Bukkit.createInventory(null, InventoryType.CHEST);
                final List<ItemStack> items = new ArrayList<>();
                items.add(new ItemStack(Material.POTATO_ITEM, 4));
                items.add(new ItemStack(Material.MELON));
                items.add(new ItemStack(Material.PUMPKIN_PIE));
                items.add(new ItemStack(Material.BREAD));
                items.add(new ItemStack(Material.IRON_INGOT));
                items.add(new ItemStack(Material.IRON_INGOT, 2));
                items.add(new ItemStack(Material.BOW));
                items.add(new ItemStack(Material.ARROW));
                items.add(new ItemStack(Material.ARROW, 5));
                items.add(new ItemStack(Material.ARROW, 11));
                items.add(new ItemStack(Material.COOKED_BEEF));
                items.add(new ItemStack(Material.COOKED_BEEF, 2));
                items.add(new ItemStack(Material.IRON_INGOT));
                items.add(new ItemStack(Material.IRON_INGOT, 2));
                items.add(new ItemStack(Material.IRON_LEGGINGS));
                items.add(new ItemStack(Material.IRON_BOOTS));
                items.add(new ItemStack(Material.IRON_HELMET));
                items.add(new ItemStack(Material.CHAINMAIL_BOOTS));
                items.add(new ItemStack(Material.CHAINMAIL_HELMET));
                items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                items.add(new ItemStack(Material.LEATHER_BOOTS));
                items.add(new ItemStack(Material.LEATHER_HELMET));
                items.add(new ItemStack(Material.LEATHER_CHESTPLATE));
                items.add(new ItemStack(Material.LEATHER_LEGGINGS));
                items.add(new ItemStack(Material.WOOD_AXE));
                items.add(new ItemStack(Material.WOOD_SWORD));
                items.add(new ItemStack(Material.STONE_SWORD));
                items.add(new ItemStack(Material.STONE_AXE));
                while (n != 0) {
                    --n;
                    final Random rnd2 = new Random();
                    final Random rnd3 = new Random();
                    final int n2 = rnd3.nextInt(27);
                    final int n3 = rnd2.nextInt(items.size());
                    inv.setItem(n2, items.get(n3));
                }
                ChestOpenerSG.main.sgchest.put(e.getClickedBlock().getLocation(), inv);
                p.openInventory(ChestOpenerSG.main.sgchest.get(e.getClickedBlock().getLocation()));
                return;
            }
            p.openInventory(ChestOpenerSG.main.sgchest.get(e.getClickedBlock().getLocation()));
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractChest2(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        if (ChestOpenerSG.main.SG && e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.TRAPPED_CHEST) {
            if (!ChestOpenerSG.main.sgchest.containsKey(e.getClickedBlock().getLocation())) {
                e.setCancelled(true);
                p.playSound(p.getLocation(), Sound.CHEST_OPEN, 10.0f, 1.0f);
                final Random rnd = new Random();
                int n;
                n = rnd.nextInt(6) + 1;
                final Inventory inv = Bukkit.createInventory(null, InventoryType.CHEST);
                final List<ItemStack> items = new ArrayList<>();
                items.add(new ItemStack(Material.POTATO_ITEM, 4));
                items.add(new ItemStack(Material.MELON));
                items.add(new ItemStack(Material.PUMPKIN_PIE));
                items.add(new ItemStack(Material.BREAD));
                items.add(new ItemStack(Material.IRON_INGOT));
                items.add(new ItemStack(Material.IRON_INGOT, 2));
                items.add(new ItemStack(Material.BOW));
                items.add(new ItemStack(Material.ARROW));
                items.add(new ItemStack(Material.ARROW, 5));
                items.add(new ItemStack(Material.ARROW, 11));
                items.add(new ItemStack(Material.COOKED_BEEF));
                items.add(new ItemStack(Material.COOKED_BEEF, 2));
                items.add(new ItemStack(Material.IRON_INGOT));
                items.add(new ItemStack(Material.IRON_INGOT, 2));
                items.add(new ItemStack(Material.IRON_LEGGINGS));
                items.add(new ItemStack(Material.IRON_BOOTS));
                items.add(new ItemStack(Material.IRON_HELMET));
                items.add(new ItemStack(Material.CHAINMAIL_BOOTS));
                items.add(new ItemStack(Material.CHAINMAIL_HELMET));
                items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
                items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS));
                items.add(new ItemStack(Material.LEATHER_BOOTS));
                items.add(new ItemStack(Material.LEATHER_HELMET));
                items.add(new ItemStack(Material.LEATHER_CHESTPLATE));
                items.add(new ItemStack(Material.LEATHER_LEGGINGS));
                items.add(new ItemStack(Material.WOOD_AXE));
                items.add(new ItemStack(Material.WOOD_SWORD));
                items.add(new ItemStack(Material.STONE_SWORD));
                items.add(new ItemStack(Material.STONE_AXE));
                while (n != 0) {
                    --n;
                    final Random rnd2 = new Random();
                    final Random rnd3 = new Random();
                    final int n2 = rnd3.nextInt(27);
                    final int n3 = rnd2.nextInt(items.size());
                    inv.setItem(n2, items.get(n3));
                }
                ChestOpenerSG.main.sgchest.put(e.getClickedBlock().getLocation(), inv);
                p.openInventory(ChestOpenerSG.main.sgchest.get(e.getClickedBlock().getLocation()));
                return;
            }
            p.openInventory(ChestOpenerSG.main.sgchest.get(e.getClickedBlock().getLocation()));
            e.setCancelled(true);
        }
    }
}
