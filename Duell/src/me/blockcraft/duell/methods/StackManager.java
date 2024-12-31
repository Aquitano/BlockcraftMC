package me.blockcraft.duell.methods;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class StackManager {
    public static ItemStack Stack(final String Display, final Material m, final String lores, final int Anzahl, final short Shorts) {
        final ItemStack istack52 = new ItemStack(m, Anzahl, Shorts);
        final ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }

    public static ItemStack Skull(final String Display, final Material m, final String lores, final int Anzahl, final short Shorts, final String Owner) {
        final ItemStack stack = new ItemStack(m, Anzahl, (short) 3);
        final SkullMeta meta = (SkullMeta) Bukkit.getItemFactory().getItemMeta(Material.SKULL_ITEM);
        meta.setOwner(Owner);
        meta.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack colorArmor(final String Display, final Material m, final String lores, final int Anzahl, final short Shorts, final int RGB1, final int RGB2, final int RGB3) {
        final ItemStack istack52 = new ItemStack(m, Anzahl, Shorts);
        final LeatherArmorMeta istackMeta52 = (LeatherArmorMeta) istack52.getItemMeta();
        istackMeta52.setColor(Color.fromRGB(RGB1, RGB2, RGB3));
        istackMeta52.setDisplayName(Display);
        final List<String> lore = new ArrayList<>();
        lore.add(lores);
        istackMeta52.setLore(lore);
        istack52.setItemMeta(istackMeta52);
        return istack52;
    }
}
