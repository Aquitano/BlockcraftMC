package me.blockcraft.QuickSG.tools;

import me.blockcraft.QuickSG.ItemAPI;
import me.blockcraft.QuickSG.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Chest implements Listener {
    public static final List<Location> openedchests = new ArrayList<>();
    public static final HashMap<Location, Inventory> sgchest = new HashMap<>();
    public static final List<ItemStack> items = new ArrayList<>();

    public static void addItems() {
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.GOLD_HELMET, 1));
        items.add(new ItemStack(Material.GOLD_CHESTPLATE, 1));
        items.add(new ItemStack(Material.GOLD_LEGGINGS, 1));
        items.add(new ItemStack(Material.GOLD_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_HELMET, 1));
        items.add(new ItemStack(Material.CHAINMAIL_CHESTPLATE, 1));
        items.add(new ItemStack(Material.CHAINMAIL_LEGGINGS, 1));
        items.add(new ItemStack(Material.CHAINMAIL_BOOTS, 1));
        items.add(new ItemStack(Material.IRON_HELMET, 1));
        items.add(new ItemStack(Material.IRON_CHESTPLATE, 1));
        items.add(new ItemStack(Material.IRON_LEGGINGS, 1));
        items.add(new ItemStack(Material.IRON_BOOTS, 1));
        items.add(new ItemStack(Material.IRON_HELMET, 1));
        items.add(new ItemStack(Material.IRON_CHESTPLATE, 1));
        items.add(new ItemStack(Material.IRON_LEGGINGS, 1));
        items.add(new ItemStack(Material.IRON_BOOTS, 1));
        items.add(new ItemStack(Material.IRON_HELMET, 1));
        items.add(new ItemStack(Material.IRON_CHESTPLATE, 1));
        items.add(new ItemStack(Material.IRON_LEGGINGS, 1));
        items.add(new ItemStack(Material.IRON_BOOTS, 1));
        items.add(new ItemStack(Material.DIAMOND_HELMET, 1));
        items.add(new ItemStack(Material.DIAMOND_CHESTPLATE, 1));
        items.add(new ItemStack(Material.DIAMOND_LEGGINGS, 1));
        items.add(new ItemStack(Material.DIAMOND_BOOTS, 1));
        items.add(new ItemStack(Material.WOOD_SWORD, 1));
        items.add(new ItemStack(Material.WOOD_SWORD, 1));
        items.add(new ItemStack(Material.WOOD_SWORD, 1));
        items.add(new ItemStack(Material.WOOD_SWORD, 1));
        items.add(new ItemStack(Material.STONE_SWORD, 1));
        items.add(new ItemStack(Material.STONE_SWORD, 1));
        items.add(new ItemStack(Material.STONE_SWORD, 1));
        items.add(new ItemStack(Material.STONE_SWORD, 1));
        items.add(new ItemStack(Material.IRON_SWORD, 1));
        items.add(new ItemStack(Material.IRON_SWORD, 1));
        items.add(new ItemStack(Material.DIAMOND_SWORD, 1));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(new ItemStack(Material.COOKED_BEEF, 2));
        items.add(new ItemStack(Material.COOKED_CHICKEN, 2));
        items.add(new ItemStack(Material.CARROT, 2));
        items.add(ItemAPI.Stack("", 351, "", 2, (short) 4));
        items.add(ItemAPI.Stack("", 351, "", 2, (short) 4));
        items.add(ItemAPI.Stack("", 351, "", 2, (short) 4));
        items.add(ItemAPI.Stack("", 351, "", 3, (short) 4));
        items.add(ItemAPI.Stack("", 351, "", 1, (short) 4));
        items.add(ItemAPI.Stack("\u00a7e+1 Level", 403, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7e+1 Level", 403, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7e+1 Herz", 282, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7e+1 Herz", 282, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7e+1 Herz", 282, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7e+1 Herz", 282, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7e+1 Level", 403, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7e+1 Herz", 282, "\u00a77Rechtsklick", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7eFull Food", 354, "\u00a77Dein Hunger wird sofort gefüllt!", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a7eFull Food", 354, "\u00a77Dein Hunger wird sofort gefüllt!", 1, (short) 0));
        items.add(ItemAPI.Stack("\u00a79Kompass", 345, "\u00a77Tracke Spieler in deiner Nühe", 1, (short) 0));
    }

    @EventHandler
    public void onInteractChest(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!Main.spectator.contains(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock().getType() == Material.CHEST) {
            if (sgchest.containsKey(e.getClickedBlock().getLocation())) {
                p.openInventory(sgchest.get(e.getClickedBlock().getLocation()));
                p.playSound(p.getLocation(), Sound.CHEST_OPEN, 10.0f, 1.0f);
                e.setCancelled(true);
            } else {
                e.setCancelled(true);
                p.playSound(p.getLocation(), Sound.CHEST_OPEN, 10.0f, 1.0f);
                Random rnd = new Random();
                int n;
                n = rnd.nextInt(7) + 1;
                Inventory inv = Bukkit.createInventory(null, InventoryType.CHEST);
                while (n != 0) {
                    --n;
                    Random rnd2 = new Random();
                    Random rnd3 = new Random();
                    int n3 = rnd3.nextInt(27);
                    int n2 = rnd2.nextInt(items.size());
                    inv.setItem(n3, items.get(n2));
                }
                sgchest.put(e.getClickedBlock().getLocation(), inv);
                p.openInventory(sgchest.get(e.getClickedBlock().getLocation()));
            }
        }
    }

    @EventHandler
    public void on(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();
        if (e.getInventory().getName().equalsIgnoreCase("Chest")) {
            try {
                p.playSound(p.getLocation(), Sound.CHEST_CLOSE, 10.0f, 1.0f);
            } catch (Exception var3_3) {
                // empty catch block
            }
        }
    }
}

