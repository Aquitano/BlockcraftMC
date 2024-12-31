package me.blockcraft.duell.methods;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class InventoryManager {
    public static void addVSInventory(final Player p) {
        final ItemStack Helm = StackManager.Stack("\u00a79Helm", Material.IRON_HELMET, "", 1, (short) 0);
        final ItemStack Harnisch = StackManager.Stack("\u00a79Harnisch", Material.IRON_CHESTPLATE, "", 1, (short) 0);
        final ItemStack Hose = StackManager.Stack("\u00a79Hose", Material.IRON_LEGGINGS, "", 1, (short) 0);
        final ItemStack Schuhe = StackManager.Stack("\u00a79Schuhe", Material.IRON_BOOTS, "", 1, (short) 0);
        final ItemStack Schwert = StackManager.Stack("\u00a79Schwert", Material.IRON_SWORD, "", 1, (short) 0);
        final ItemStack Angel = StackManager.Stack("\u00a79Angel", Material.FISHING_ROD, "", 1, (short) 0);
        final ItemStack Bogen = StackManager.Stack("\u00a79Bogen", Material.BOW, "", 1, (short) 0);
        final ItemStack Pfeile = StackManager.Stack("\u00a79Pfeile", Material.ARROW, "", 12, (short) 0);
        p.getInventory().clear();
        p.getInventory().setArmorContents(null);
        p.getInventory().setHelmet(Helm);
        p.getInventory().setChestplate(Harnisch);
        p.getInventory().setLeggings(Hose);
        p.getInventory().setBoots(Schuhe);
        p.getInventory().setItem(0, Schwert);
        p.getInventory().setItem(1, Angel);
        p.getInventory().setItem(7, Bogen);
        p.getInventory().setItem(8, Pfeile);
        p.setHealth(20.0);
    }

    public static void addSJInventory(final Player p) {
        p.getInventory().clear();
        p.setLevel(0);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.getInventory().setArmorContents(null);
    }
}
