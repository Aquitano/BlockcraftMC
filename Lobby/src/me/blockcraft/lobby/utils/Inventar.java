package me.blockcraft.lobby.utils;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

public class Inventar {
    public static void setupStandardItems(final Player p) {
        p.getInventory().clear();
        final ItemStack istack52 = new ItemStack(Material.COMPASS);
        final ItemMeta istackMeta52 = istack52.getItemMeta();
        istackMeta52.setDisplayName("\u00a78» \u00a79Navigator \u00a78● \u00a77Rechtsklick");
        istackMeta52.setLore(addNonDroppableLore("\u00a77Rechtsklick"));
        istack52.setItemMeta(istackMeta52);

        final ItemStack istack53 = new ItemStack(Material.EMERALD);
        final ItemMeta istackMeta53 = istack53.getItemMeta();
        istackMeta53.setDisplayName("\u00a78» \u00a79Secrets \u00a78● \u00a77Rechtsklick");
        istackMeta53.setLore(addNonDroppableLore("\u00a77Rechtsklick"));
        istack53.setItemMeta(istackMeta53);

        final ItemStack istack54 = new ItemStack(Material.SPECKLED_MELON);
        final ItemMeta istackMeta54 = istack54.getItemMeta();
        istackMeta54.setDisplayName("\u00a78» \u00a79Extras \u00a78● \u00a77Rechtsklick");
        istackMeta54.setLore(addNonDroppableLore("\u00a77Rechtsklick"));
        istack54.setItemMeta(istackMeta54);

        final ItemStack istack55 = Skull("\u00a78» \u00a79Freunde \u00a78● \u00a7cBald verfügbar!", Material.SKULL_ITEM, "", 1, (short) 3, p.getName());
        final ItemMeta istackMeta55 = istack55.getItemMeta();
        istackMeta55.setLore(addNonDroppableLore("\u00a77Rechtsklick"));
        istack55.setItemMeta(istackMeta55);

        final ItemStack Hide = Stack("\u00a78» \u00a79Spieler \u00a78● \u00a7aAN", Material.INK_SACK, "", 1, (short) 10);
        final ItemMeta istackMeta56 = Hide.getItemMeta();
        istackMeta56.setLore(addNonDroppableLore("\u00a77Rechtsklick"));
        Hide.setItemMeta(istackMeta56);

        p.getInventory().clear();
        p.getInventory().setItem(0, istack52);
        p.getInventory().setItem(1, Hide);
        p.getInventory().setItem(7, istack55);
        p.getInventory().setItem(4, istack54);
        p.getInventory().setItem(8, istack53);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setHealthScale(6.0);
    }

    public static void openTrailsMenu(final Player p) {
        final Inventory inv = Bukkit.createInventory(null, 9, "Trails");
        final ItemStack Book = Stack("\u00a78● \u00a79Zauber", Material.BOOK, "\u00a77Zauber Spuren!", 1, (short) 0);
        final ItemStack Ender = Stack("\u00a78● \u00a79Musik", Material.NOTE_BLOCK, "\u00a77Hinterlasse Musik!", 1, (short) 0);
        final ItemStack Feuer = Stack("\u00a78● \u00a79Feuer", Material.FIREBALL, "\u00a77Laufe auf Feuer!", 1, (short) 0);
        final ItemStack Feuerwerk = Stack("\u00a78● \u00a79Feuerwerk", Material.FIREWORK, "\u00a77Party!", 1, (short) 0);
        final ItemStack close = Stack("\u00a78● \u00a7cEffekt entfernen", Material.BARRIER, "\u00a77Klicke um Effekte zu entfernen.", 1, (short) 0);
        inv.setItem(0, Book);
        inv.setItem(1, Ender);
        inv.setItem(2, Feuer);
        inv.setItem(3, Feuerwerk);
        inv.setItem(8, close);
        p.openInventory(inv);
    }

    public static void setupPremiumInv(final Player p) {
        final Inventory inv = Bukkit.createInventory(null, 9, "Premium");
        final ItemStack Glas = Stack("", Material.STAINED_GLASS_PANE, "", 1, (short) 15);
        final ItemStack Armorstand = Stack("\u00a78● \u00a7bArmorStand", Material.ARMOR_STAND, "\u00a77Verwandel dich in einen Armorstand!", 1, (short) 0);
        final ItemStack PB = Stack("\u00a78● \u00a76Zum Premiumbereich", Material.FIREWORK, "\u00a77Teleportiere dich zum Premiumbereich!", 1, (short) 0);
        final ItemStack Trails = colorArmor("\u00a78● \u00a7bSpuren", Material.LEATHER_BOOTS, "\u00a77Hinterlasse Laufspuren!", 1, (short) 0, 19, 79, 218);
        final ItemStack Haustier = Stack("\u00a78● \u00a7bHaustiere", Material.MONSTER_EGG, "\u00a77Spawne dein eigenes Pet!", 1, (short) 0);
        inv.setItem(2, Armorstand);
        inv.setItem(6, PB);
        inv.setItem(7, Haustier);
        inv.setItem(0, Glas);
        inv.setItem(1, Trails);
        inv.setItem(3, Glas);
        inv.setItem(4, Glas);
        inv.setItem(5, Glas);
        inv.setItem(8, Glas);
        p.openInventory(inv);
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

    public static List<String> addNonDroppableLore(String lores) {
        List<String> lore = new ArrayList<>();
        lore.add(lores);
        lore.add("NON_DROPPABLE");
        return lore;
    }
}
