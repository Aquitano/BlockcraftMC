package me.blockcraft.QuickSG;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;

public class ItemAPI {
    @SuppressWarnings("deprecation")
    public static ItemStack Stack(String Display, int i, String lores, int Anzahl, short Shorts) {
        ItemStack istack52 = new ItemStack(i, Anzahl, Shorts);
        ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName(Display);
        ArrayList<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack Stackench(String Display, int i, String lores, int Anzahl, short Shorts) {
        ItemStack istack52 = new ItemStack(i, Anzahl, Shorts);
        ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName(Display);
        istackMeta52.addEnchant(Enchantment.DURABILITY, 1, true);
        ArrayList<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    public static ItemStack IDStack(String Display, int id, String lores, int Anzahl, short Shorts) {
        return Stack(Display, id, lores, Anzahl, Shorts);
    }

    @SuppressWarnings("deprecation")
    public static ItemStack Skull(String Display, int i, String lores, int Anzahl, short Shorts, String Owner) {
        ItemStack stack = new ItemStack(i, Anzahl, (short) 3);
        SkullMeta meta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.SKULL_ITEM);
        meta.setOwner(Owner);
        meta.setDisplayName(Display);
        ArrayList<String> lore = new ArrayList<>();
        lore.add(lores);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack colorArmor(String Display, Material m, String lores, int Anzahl, short Shorts, int RGB1, int RGB2, int RGB3) {
        ItemStack istack52 = new ItemStack(m, Anzahl, Shorts);
        LeatherArmorMeta istackMeta52 = (LeatherArmorMeta) istack52.getItemMeta();
        istackMeta52.setColor(Color.fromRGB(RGB1, RGB2, RGB3));
        istackMeta52.setDisplayName(Display);
        ArrayList<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }
}

